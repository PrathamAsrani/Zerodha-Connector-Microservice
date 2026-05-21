package com.zerodha.service.services;

import com.zerodha.service.utils.KiteUtility;
import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.Holding;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Snapshot of the user's holdings used by the per-tick strategy fan-out.
 * Loaded once per trading day at tick 0; all virtual threads read from this
 * same instance. Also hosts a short-TTL key set used as a sell-idempotency
 * guard ({@link #tryReserve}).
 */
@Service
@Slf4j
public class HoldingCacheService {

    private static final Logger logger = LoggerFactory.getLogger(HoldingCacheService.class);
    private static final Duration RESERVATION_TTL = Duration.ofMinutes(5);

    public record CachedHolding(String tradingSymbol, String exchange,
                                int quantity, double averagePrice) {}

    private final KiteUtility kiteUtility;
    private final AtomicReference<List<CachedHolding>> cache =
            new AtomicReference<>(Collections.emptyList());
    private volatile boolean loaded = false;

    // key -> expiry instant
    private final Map<String, Instant> reservations = new ConcurrentHashMap<>();

    public HoldingCacheService(KiteUtility kiteUtility) {
        this.kiteUtility = kiteUtility;
    }

    public synchronized void reload() {
        if (!kiteUtility.hasTokens()) {
            logger.warn("Cannot load holdings cache: no Kite tokens. Strategy fan-out will be a no-op.");
            cache.set(Collections.emptyList());
            loaded = true;
            return;
        }
        try {
            KiteConnect sdk = kiteUtility.getKiteSdk();
            List<Holding> raw = sdk.getHoldings();
            List<CachedHolding> mapped = new ArrayList<>(raw == null ? 0 : raw.size());
            if (raw != null) {
                for (Holding h : raw) {
                    if (h == null || h.tradingSymbol == null || h.averagePrice == null) continue;
                    if (h.quantity <= 0) continue;
                    log.info("Trading symbol: {}, average price: {}, quantity: {}", h.tradingSymbol, h.averagePrice, h.quantity);
                    mapped.add(new CachedHolding(
                            h.tradingSymbol,
                            h.exchange == null ? "NSE" : h.exchange,
                            h.quantity,
                            h.averagePrice
                    ));
                }
            }
            cache.set(List.copyOf(mapped));
            loaded = true;
            logger.info("Holdings cache loaded with {} holdings.", mapped.size());
        } catch (KiteException | IOException ex) {
            logger.error("Failed to load holdings cache: {}", ex.getMessage(), ex);
            cache.set(Collections.emptyList());
            loaded = true;
        }
    }

    public List<CachedHolding> getHoldings() {
        return cache.get();
    }

    /**
     * Empties the holdings cache. The next cron tick sees an empty cache and
     * triggers a fresh {@link #reload()} from Kite.
     *
     * @return number of holdings that were cached before clearing
     */
    public synchronized int clear() {
        int size = cache.get().size();
        cache.set(Collections.emptyList());
        loaded = false;
        logger.info("Holdings cache cleared ({} entries removed).", size);
        return size;
    }

    public boolean isLoaded() {
        return loaded;
    }

    /**
     * Best-effort sell-idempotency guard. Returns true if the key was reserved
     * (no prior reservation, or prior reservation has expired). Returns false
     * if another thread already holds the key within the TTL window.
     */
    public boolean tryReserve(String key) {
        if (key == null) return false;
        Instant now = Instant.now();
        // sweep expired entries opportunistically
        reservations.entrySet().removeIf(e -> e.getValue().isBefore(now));
        Instant prev = reservations.putIfAbsent(key, now.plus(RESERVATION_TTL));
        return prev == null;
    }
}
