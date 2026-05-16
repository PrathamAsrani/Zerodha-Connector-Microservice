package com.zerodha.service.model;

import com.zerodha.service.enums.TransactionEnum;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

/**
 * Record of a strategy-triggered trade decision (one row per placed order).
 * Strategy-agnostic: {@code strategyName} identifies which rule fired so we can
 * add more strategies without schema churn.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "trades",
        indexes = {
                @Index(name = "idx_trades_symbol_time", columnList = "trading_symbol, trade_time"),
                @Index(name = "idx_trades_phone_time", columnList = "phone_number, trade_time"),
                @Index(name = "idx_trades_strategy_time", columnList = "strategy_name, trade_time")
        }
)
public class TradeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trading_symbol", nullable = false, length = 64)
    private String tradingSymbol;

    @Column(name = "exchange", length = 16)
    private String exchange;

    @Column(name = "avg_price", nullable = false)
    private double avgPrice;

    @Column(name = "target_price", nullable = false)
    private double targetPrice;

    @Column(name = "profit", nullable = false)
    private double profit;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "trade_time", nullable = false)
    private Instant tradeTime;

    @Column(name = "phone_number", length = 32)
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 8)
    private TransactionEnum transactionType;

    @Column(name = "strategy_name", nullable = false, length = 64)
    private String strategyName;

    @Column(name = "order_id", length = 64)
    private String orderId;

    @Column(name = "request_id", length = 64)
    private String requestId;
}
