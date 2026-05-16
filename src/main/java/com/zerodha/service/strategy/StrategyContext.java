package com.zerodha.service.strategy;

import com.zerodha.service.services.HoldingCacheService.CachedHolding;

import java.time.Instant;

/**
 * Read-only context passed to every {@link HoldingStrategy} for a given symbol
 * tick. New fields can be added (e.g. historical OHLC, indicators) without
 * breaking existing strategies.
 */
public record StrategyContext(
        CachedHolding holding,
        double ltp,
        Instant fetchedAt,
        String requestId,
        String phoneNumber
) {
    public String symbol() {
        return holding.tradingSymbol();
    }
}
