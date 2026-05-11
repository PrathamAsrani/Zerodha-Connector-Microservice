package com.zerodha.service.repository;

import com.zerodha.service.model.StockDailySummaryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface StockDailySummaryRepo extends JpaRepository<StockDailySummaryEntity, Long> {
    Optional<StockDailySummaryEntity> findByTradingSymbolAndExchangeAndSummaryDate(
            String tradingSymbol, String exchange, LocalDate summaryDate);
}
