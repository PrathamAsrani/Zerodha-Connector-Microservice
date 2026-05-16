package com.zerodha.service.constants;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Set;

public final class MarketConstants {
    public static final ZoneId INDIA_ZONE = ZoneId.of("Asia/Kolkata");
    public static final LocalTime MARKET_OPEN_TIME = LocalTime.of(9, 30);
    public static final LocalTime MARKET_CLOSE_TIME = LocalTime.of(15, 30);

    // Source: https://groww.in/p/nse-holidays
    // Equity / Equity Derivatives / SLB segment holidays.
    public static final Set<LocalDate> NSE_HOLIDAYS = Set.of(
            LocalDate.of(2026, 1, 15),  // Municipal Corp Election - Maharashtra
            LocalDate.of(2026, 1, 26),  // Republic Day
            LocalDate.of(2026, 3, 3),   // Holi
            LocalDate.of(2026, 3, 26),  // Shri Ram Navami
            LocalDate.of(2026, 3, 31),  // Shri Mahavir Jayanti
            LocalDate.of(2026, 4, 3),   // Good Friday
            LocalDate.of(2026, 4, 14),  // Dr. Baba Saheb Ambedkar Jayanti
            LocalDate.of(2026, 5, 1),   // Maharashtra Day
            LocalDate.of(2026, 5, 28),  // Bakri Id
            LocalDate.of(2026, 6, 26),  // Muharram
            LocalDate.of(2026, 9, 14),  // Ganesh Chaturthi
            LocalDate.of(2026, 10, 2),  // Mahatma Gandhi Jayanti
            LocalDate.of(2026, 10, 20), // Dussehra
            LocalDate.of(2026, 11, 10), // Diwali-Balipratipada
            LocalDate.of(2026, 11, 24), // Prakash Gurpurb Sri Guru Nanak Dev
            LocalDate.of(2026, 12, 25)  // Christmas
    );

    private MarketConstants() {
    }

    public static boolean IS_MARKET_OPEN() {
        ZonedDateTime now = ZonedDateTime.now(INDIA_ZONE);
        DayOfWeek day = now.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return false;
        }
        if (NSE_HOLIDAYS.contains(now.toLocalDate())) {
            return false;
        }
        LocalTime t = now.toLocalTime();
        return !t.isBefore(MARKET_OPEN_TIME) && !t.isAfter(MARKET_CLOSE_TIME);
    }
}
