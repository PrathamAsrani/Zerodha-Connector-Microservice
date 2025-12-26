package com.zerodha.service.constants;

import java.time.ZoneId;
import java.time.LocalTime;

public final class MarketConstants {
    private static final ZoneId INDIA_ZONE = ZoneId.of("Asia/Kolkata");
    private static final LocalTime MARKET_OPEN_TIME = LocalTime.of(9, 20), MARKET_CLOSE_TIME = LocalTime.of(15, 25);

    private MarketConstants() {
        // Private constructor to prevent instantiation
    }

    public static boolean IS_MARKET_OPEN() {
        LocalTime now = LocalTime.now(INDIA_ZONE);
        return now.isAfter(MARKET_OPEN_TIME) && now.isBefore(MARKET_CLOSE_TIME);
    }
}