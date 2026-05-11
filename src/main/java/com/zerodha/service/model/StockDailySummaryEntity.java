package com.zerodha.service.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "stock_daily_summary",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_stock_daily_summary_symbol_exchange_date",
                columnNames = {"trading_symbol", "exchange", "summary_date"}
        )
)
public class StockDailySummaryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trading_symbol", nullable = false, length = 64)
    private String tradingSymbol;

    @Column(name = "exchange", nullable = false, length = 16)
    private String exchange;

    @Column(name = "summary_date", nullable = false)
    private LocalDate summaryDate;

    @Column(name = "avg_price", nullable = false)
    private double avgPrice;

    @Column(name = "min_price", nullable = false)
    private double minPrice;

    @Column(name = "max_price", nullable = false)
    private double maxPrice;

    @Column(name = "last_price", nullable = false)
    private double lastPrice;

    @Column(name = "sample_count", nullable = false)
    private int sampleCount;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
