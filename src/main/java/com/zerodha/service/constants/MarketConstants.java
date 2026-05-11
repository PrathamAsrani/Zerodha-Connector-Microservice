package com.zerodha.service.constants;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public final class MarketConstants {
    public static final ZoneId INDIA_ZONE = ZoneId.of("Asia/Kolkata");
    private static final LocalTime MARKET_OPEN_TIME = LocalTime.of(9, 30), MARKET_CLOSE_TIME = LocalTime.of(15, 30);

    private MarketConstants() {
        // Private constructor to prevent instantiation
    }

    public static boolean IS_MARKET_OPEN() {
        ZonedDateTime now = ZonedDateTime.now(INDIA_ZONE);
        DayOfWeek day = now.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return false;
        }
        LocalTime t = now.toLocalTime();
        return t.isAfter(MARKET_OPEN_TIME) && t.isBefore(MARKET_CLOSE_TIME);
    }
}