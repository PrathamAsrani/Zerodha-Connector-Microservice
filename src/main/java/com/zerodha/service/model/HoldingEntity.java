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
        name = "holdings",
        indexes = {
                @Index(name = "idx_holdings_phone_fetched", columnList = "phone_number, fetched_at"),
                @Index(name = "idx_holdings_symbol_fetched", columnList = "trading_symbol, exchange, fetched_at")
        }
)
public class HoldingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "phone_number", length = 32)
    private String phoneNumber;

    @Column(name = "trading_symbol", nullable = false, length = 64)
    private String tradingSymbol;

    @Column(name = "exchange", nullable = false, length = 16)
    private String exchange;

    @Column(name = "instrument_token")
    private Long instrumentToken;

    @Column(name = "isin", length = 32)
    private String isin;

    @Column(name = "product", length = 16)
    private String product;

    @Column(name = "units")
    private Integer units;

    @Column(name = "used_quantity")
    private Integer usedQuantity;

    @Column(name = "t1_quantity")
    private Integer t1Quantity;

    @Column(name = "realised_quantity")
    private Integer realisedQuantity;

    @Column(name = "authorised_quantity")
    private Integer authorisedQuantity;

    @Column(name = "opening_quantity")
    private Integer openingQuantity;

    @Column(name = "collateral_quantity")
    private Integer collateralQuantity;

    @Column(name = "collateral_type", length = 32)
    private String collateralType;

    @Column(name = "average_price")
    private Double averagePrice;

    @Column(name = "last_price")
    private Double lastPrice;

    @Column(name = "close_price")
    private Double closePrice;

    @Column(name = "pnl")
    private Double pnl;

    @Column(name = "day_change")
    private Double dayChange;

    @Column(name = "day_change_percentage")
    private Double dayChangePercentage;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;
}
