package com.zerodha.service.services;

import com.zerodha.service.utils.KiteUtility;
import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.Holding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Singleton snapshot of portfolio concentration used to cap BUY orders.
 *
 * <p>Holds the total portfolio market value and a per-stock allocation ceiling
 * (a configurable fraction of that total — default 30%, overridable via the
 * {@code properties} table). The snapshot is (re)built from Kite holdings on
 * first use and after every successful order placement, so the next BUY always
 * sees fresh numbers.
 *
 * <p>All accessors are safe to call from the per-symbol virtual threads.
 */
@Service
public class PortfolioAllocationService {

    private static final Logger logger = LoggerFactory.getLogger(PortfolioAllocationService.class);

    /** Property name (in the {@code properties} table) overriding the per-stock cap fraction. */
    public static final String ALLOCATION_FRACTION_PROPERTY = "MAX_STOCK_ALLOCATION_FRACTION";
    private static final double DEFAULT_ALLOCATION_FRACTION = 0.30;

    private final KiteUtility kiteUtility;
    private final PropertyService propertyService;

    // Current market value per trading symbol (lastPrice * qty), rebuilt on refresh.
    private final Map<String, Double> symbolValues = new ConcurrentHashMap<>();
    private volatile double totalPortfolioValue = 0.0;
    private volatile double maxAllocationPerStock = 0.0;
    private volatile double allocationFraction = DEFAULT_ALLOCATION_FRACTION;
    private volatile boolean loaded = false;

    public PortfolioAllocationService(KiteUtility kiteUtility, PropertyService propertyService) {
        this.kiteUtility = kiteUtility;
        this.propertyService = propertyService;
    }

    /**
     * Rebuild the portfolio-value snapshot from live Kite holdings and recompute
     * the per-stock allocation ceiling. Best-effort: any failure leaves the
     * previous snapshot intact and is logged, never thrown.
     */
    public synchronized void refresh() {
        if (!kiteUtility.hasTokens()) {
            logger.warn("Cannot refresh portfolio allocation: no Kite tokens.");
            return;
        }
        try {
            KiteConnect sdk = kiteUtility.getKiteSdk();
            List<Holding> holdings = sdk.getHoldings();

            Map<String, Double> values = new ConcurrentHashMap<>();
            double total = 0.0;
            if (holdings != null) {
                for (Holding h : holdings) {
                    if (h == null || h.tradingSymbol == null || h.quantity <= 0) continue;
                    // Prefer live last_price; fall back to average_price when absent.
                    double unitPrice = h.lastPrice != null && h.lastPrice > 0.0
                            ? h.lastPrice
                            : (h.averagePrice != null ? h.averagePrice : 0.0);
                    double value = unitPrice * h.quantity;
                    values.merge(h.tradingSymbol, value, Double::sum);
                    total += value;
                }
            }

            double fraction = resolveFraction();
            symbolValues.clear();
            symbolValues.putAll(values);
            totalPortfolioValue = total;
            allocationFraction = fraction;
            maxAllocationPerStock = total * fraction;
            loaded = true;

            logger.info("Portfolio allocation refreshed: total=Rs {}, fraction={}%, per-stock cap=Rs {} across {} symbols.",
                    String.format("%.2f", total), String.format("%.1f", fraction * 100.0),
                    String.format("%.2f", maxAllocationPerStock), values.size());
        } catch (KiteException | java.io.IOException ex) {
            logger.error("Failed to refresh portfolio allocation: {}", ex.getMessage(), ex);
        } catch (Exception ex) {
            logger.error("Unexpected error refreshing portfolio allocation: {}", ex.getMessage(), ex);
        }
    }

    /** Never-throwing variant for hot paths (called after every order placement). */
    public void refreshQuietly() {
        try {
            refresh();
        } catch (Exception ex) {
            logger.error("refreshQuietly swallowed error: {}", ex.getMessage(), ex);
        }
    }

    /**
     * Whether a BUY of {@code buyValue} (price * qty) for {@code tradingSymbol}
     * keeps that symbol at or below the per-stock allocation ceiling.
     *
     * <p>If the snapshot can't be established (no tokens / empty portfolio), the
     * cap can't be enforced and we allow the order rather than blocking trading.
     */
    public boolean isBuyWithinAllocationLimit(String tradingSymbol, double buyValue) {
        if (!loaded) {
            refresh();
        }
        if (totalPortfolioValue <= 0.0 || maxAllocationPerStock <= 0.0) {
            logger.warn("Allocation cap not established (total=Rs {}); allowing BUY for {}.",
                    String.format("%.2f", totalPortfolioValue), tradingSymbol);
            return true;
        }

        double existing = symbolValues.getOrDefault(tradingSymbol, 0.0);
        double projected = existing + buyValue;
        boolean within = projected <= maxAllocationPerStock;

        logger.info("Allocation check {} BUY: current=Rs {}, +buy=Rs {} => projected=Rs {} vs cap=Rs {} ({}% of Rs {}) -> {}",
                tradingSymbol, String.format("%.2f", existing), String.format("%.2f", buyValue),
                String.format("%.2f", projected), String.format("%.2f", maxAllocationPerStock),
                String.format("%.1f", allocationFraction * 100.0),
                String.format("%.2f", totalPortfolioValue),
                within ? "ALLOW" : "BLOCK");
        return within;
    }

    public double getTotalPortfolioValue() {
        return totalPortfolioValue;
    }

    public double getMaxAllocationPerStock() {
        return maxAllocationPerStock;
    }

    private double resolveFraction() {
        double fraction = propertyService.getDouble(ALLOCATION_FRACTION_PROPERTY, DEFAULT_ALLOCATION_FRACTION);
        if (fraction <= 0.0 || fraction > 1.0) {
            logger.warn("Property '{}' = {} is outside (0,1]; using default {}.",
                    ALLOCATION_FRACTION_PROPERTY, fraction, DEFAULT_ALLOCATION_FRACTION);
            return DEFAULT_ALLOCATION_FRACTION;
        }
        return fraction;
    }
}
