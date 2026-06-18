package com.zerodha.service.repository;

import com.zerodha.service.model.StockLtpEntity;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface StockLtpRepo extends JpaRepository<StockLtpEntity, Long> {
    List<StockLtpEntity> findByTradingSymbolAndExchangeAndFetchedAtBetweenOrderByFetchedAtAsc(
            String tradingSymbol, String exchange, Instant from, Instant to);

    /** Most recent intraday LTP snapshots (newest first), capped by {@code limit}. */
    List<StockLtpEntity> findByTradingSymbolAndExchangeOrderByFetchedAtDesc(
            String tradingSymbol, String exchange, Limit limit);
}
