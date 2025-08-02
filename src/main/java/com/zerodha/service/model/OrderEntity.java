package com.zerodha.service.model;

import com.zerodha.service.enums.*;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import lombok.*;

import java.time.Instant;
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class OrderEntity {
    @Id
    private String orderId;

    private String tradingsymbol;

    @Enumerated(EnumType.STRING)
    private ExchangeEnum exchange;

    @Enumerated(EnumType.STRING)
    private TransactionEnum transactionType;

    @Enumerated(EnumType.STRING)
    private OrderEnum orderType;

    private int quantity;

    @Enumerated(EnumType.STRING)
    private ProductEnum product;

    private double price;
    private double triggerPrice;
    private int disclosedQuantity;

    @Enumerated(EnumType.STRING)
    private ValidityEnum validity;

    private Double squareoff;
    private Double stoploss;
    private Double trailingStoploss;

    @Enumerated(EnumType.STRING)
    private VarietyEnum variety;

    private String tag;

    private Instant placedAt;
}
