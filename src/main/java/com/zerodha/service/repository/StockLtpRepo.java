package com.zerodha.service.repository;

import com.zerodha.service.model.StockLtpEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface StockLtpRepo extends JpaRepository<StockLtpEntity, Long> {
    List<StockLtpEntity> findByTradingSymbolAndExchangeAndFetchedAtBetweenOrderByFetchedAtAsc(
            String tradingSymbol, String exchange, Instant from, Instant to);
}
