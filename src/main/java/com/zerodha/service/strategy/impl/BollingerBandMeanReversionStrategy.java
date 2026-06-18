package com.zerodha.service.strategy.impl;

import com.zerodha.service.constants.MarketConstants;
import com.zerodha.service.enums.TransactionEnum;
import com.zerodha.service.model.StockDailySummaryEntity;
import com.zerodha.service.model.StockLtpEntity;
import com.zerodha.service.repository.StockDailySummaryRepo;
import com.zerodha.service.repository.StockLtpRepo;
import com.zerodha.service.strategy.HoldingStrategy;
import com.zerodha.service.strategy.StrategyContext;
import com.zerodha.service.strategy.StrategyDecision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Statistical mean-reversion using both persisted tables:
 *
 * <ul>
 *   <li>{@code stock_daily_summary} → builds a Bollinger band from the last
 *       N completed sessions: {@code mean ± k·σ} of daily {@code avg_price}.</li>
 *   <li>{@code stock_ltp} → a short intraday moving average (last M snapshots)
 *       used as the "fast" line that triggers the signal, smoothing single-tick
 *       noise.</li>
 * </ul>
 *
 * <p>Logic (classic band reversion):
 * <ul>
 *   <li><b>BUY</b> when the short MA <em>and</em> the live LTP both sit at/below
 *       the lower band (statistically oversold) — average down a budgeted slice.</li>
 *   <li><b>SELL</b> when both sit at/above the upper band (overbought) — exit the
 *       whole holding to book the reversion.</li>
 * </ul>
 *
 * <p>Requiring both the smoothed line and the live tick to agree avoids acting on
 * a single spurious print. Distinct from the fixed-percentage strategies: the
 * trigger adapts to each symbol's own recent volatility (σ).
 *
 * <p>Stateless apart from injected repos, so it is safe to run concurrently from
 * the orchestrator's per-symbol virtual threads.
 */
@Component
public class BollingerBandMeanReversionStrategy implements HoldingStrategy {

    public static final String NAME = "BOLLINGER_MEAN_REVERSION";

    private static final Logger logger = LoggerFactory.getLogger(BollingerBandMeanReversionStrategy.class);

    private final int lookbackDays;
    private final int minSamples;
    private final double k;
    private final int shortMaSamples;
    private final double limitOffset;
    private final double perTradeBudgetRs;

    private final StockDailySummaryRepo dailySummaryRepo;
    private final StockLtpRepo ltpRepo;

    public BollingerBandMeanReversionStrategy(
            @Value("${strategy.bollinger.lookback-days:10}") int lookbackDays,
            @Value("${strategy.bollinger.min-samples:5}") int minSamples,
            @Value("${strategy.bollinger.k:2.0}") double k,
            @Value("${strategy.bollinger.short-ma-samples:12}") int shortMaSamples,
            @Value("${strategy.bollinger.limit-offset-rs:1.0}") double limitOffset,
            @Value("${strategy.bollinger.per-trade-budget-rs:50000}") double perTradeBudgetRs,
            StockDailySummaryRepo dailySummaryRepo,
            StockLtpRepo ltpRepo) {
        this.lookbackDays = lookbackDays;
        this.minSamples = minSamples;
        this.k = k;
        this.shortMaSamples = shortMaSamples;
        this.limitOffset = limitOffset;
        this.perTradeBudgetRs = perTradeBudgetRs;
        this.dailySummaryRepo = dailySummaryRepo;
        this.ltpRepo = ltpRepo;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public StrategyDecision evaluate(StrategyContext ctx) {
        String symbol = ctx.holding().tradingSymbol();
        String exchange = ctx.holding().exchange();

        Band band = computeBand(symbol, exchange);
        if (band == null) {
            return StrategyDecision.hold();
        }

        double shortMa = computeShortMa(symbol, exchange, ctx.ltp());
        double ltp = ctx.ltp();

        // SELL: overbought — both the smoothed line and the live tick at/above upper band.
        if (shortMa >= band.upper && ltp >= band.upper) {
            int qty = ctx.holding().quantity();
            if (qty <= 0) return StrategyDecision.hold();
            double limitPrice = round2(ltp + limitOffset);
            String reason = String.format(
                    "overbought: shortMA %.2f & ltp %.2f >= upper %.2f (mean %.2f, σ %.2f, k %.1f, n %d)",
                    shortMa, ltp, band.upper, band.mean, band.sd, k, band.n);
            logger.info("{}: {} -> SELL {} @ Rs {}", symbol, reason, qty, limitPrice);
            return StrategyDecision.trade(TransactionEnum.SELL, qty, limitPrice, NAME, reason);
        }

        // BUY: oversold — both at/below lower band; size to a budgeted, affordable slice.
        if (shortMa <= band.lower && ltp <= band.lower) {
            double limitPrice = round2(Math.max(0.05, ltp - limitOffset));
            double budget = Math.min(ctx.availableNet(), perTradeBudgetRs);
            int qty = (int) Math.floor(budget / limitPrice);
            if (qty < 1) {
                logger.info("{}: oversold (shortMA {} <= lower {}) but budget Rs {} < limit Rs {}; holding.",
                        symbol, String.format("%.2f", shortMa), String.format("%.2f", band.lower),
                        String.format("%.2f", budget), String.format("%.2f", limitPrice));
                return StrategyDecision.hold();
            }
            String reason = String.format(
                    "oversold: shortMA %.2f & ltp %.2f <= lower %.2f (mean %.2f, σ %.2f, k %.1f, n %d); qty %d @ Rs %.2f",
                    shortMa, ltp, band.lower, band.mean, band.sd, k, band.n, qty, limitPrice);
            logger.info("{}: {} -> BUY", symbol, reason);
            return StrategyDecision.trade(TransactionEnum.BUY, qty, limitPrice, NAME, reason);
        }

        return StrategyDecision.hold();
    }

    /**
     * Build the {@code mean ± k·σ} band from the last {@code lookbackDays}
     * completed sessions (today's partial summary excluded). Returns null when
     * there isn't enough history or volatility is degenerate.
     */
    private Band computeBand(String symbol, String exchange) {
        LocalDate today = ZonedDateTime.now(MarketConstants.INDIA_ZONE).toLocalDate();
        List<StockDailySummaryEntity> rows;
        try {
            // Pull one extra so dropping today still leaves a full window.
            rows = dailySummaryRepo.findByTradingSymbolAndExchangeOrderBySummaryDateDesc(
                    symbol, exchange, Limit.of(lookbackDays + 1));
        } catch (Exception ex) {
            logger.warn("{}: failed to load daily summaries: {}", symbol, ex.getMessage());
            return null;
        }

        double sum = 0.0;
        double sumSq = 0.0;
        int n = 0;
        for (StockDailySummaryEntity r : rows) {
            if (r.getSummaryDate() != null && r.getSummaryDate().isEqual(today)) continue; // skip partial day
            if (n >= lookbackDays) break;
            double v = r.getAvgPrice();
            if (v <= 0.0) continue;
            sum += v;
            sumSq += v * v;
            n++;
        }

        if (n < minSamples) {
            logger.debug("{}: only {} completed daily samples (< {} required); skipping.",
                    symbol, n, minSamples);
            return null;
        }

        double mean = sum / n;
        double variance = (sumSq / n) - (mean * mean);   // population variance
        double sd = variance > 0.0 ? Math.sqrt(variance) : 0.0;
        if (sd <= 0.0) {
            logger.debug("{}: degenerate σ=0 over {} samples; skipping.", symbol, n);
            return null;
        }
        return new Band(mean, sd, mean - k * sd, mean + k * sd, n);
    }

    /**
     * Average of the last {@code shortMaSamples} stored LTP snapshots. Falls back
     * to the live tick when no intraday history is available yet.
     */
    private double computeShortMa(String symbol, String exchange, double fallback) {
        try {
            List<StockLtpEntity> recent = ltpRepo.findByTradingSymbolAndExchangeOrderByFetchedAtDesc(
                    symbol, exchange, Limit.of(shortMaSamples));
            if (recent.isEmpty()) return fallback;
            double sum = 0.0;
            for (StockLtpEntity e : recent) sum += e.getLtp();
            return sum / recent.size();
        } catch (Exception ex) {
            logger.warn("{}: failed to load recent LTP; using live tick. ({})", symbol, ex.getMessage());
            return fallback;
        }
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private record Band(double mean, double sd, double lower, double upper, int n) {}
}
