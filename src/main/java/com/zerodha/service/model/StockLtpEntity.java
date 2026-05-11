package com.zerodha.service.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "stock_ltp",
        indexes = {
                @Index(name = "idx_stock_ltp_symbol_fetched", columnList = "trading_symbol, exchange, fetched_at"),
                @Index(name = "idx_stock_ltp_fetched_at", columnList = "fetched_at")
        }
)
public class StockLtpEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trading_symbol", nullable = false, length = 64)
    private String tradingSymbol;

    @Column(name = "exchange", nullable = false, length = 16)
    private String exchange;

    @Column(name = "instrument_token")
    private Long instrumentToken;

    @Column(name = "ltp", nullable = false)
    private double ltp;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;
}
