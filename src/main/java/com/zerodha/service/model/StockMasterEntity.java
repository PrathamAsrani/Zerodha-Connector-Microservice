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

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "stock_master",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_stock_master_symbol_exchange",
                columnNames = {"trading_symbol", "exchange"}
        )
)
public class StockMasterEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trading_symbol", nullable = false, length = 64)
    private String tradingSymbol;

    @Column(name = "exchange", nullable = false, length = 16)
    private String exchange;

    @Column(name = "instrument_token")
    private Long instrumentToken;

    @Column(name = "name")
    private String name;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
