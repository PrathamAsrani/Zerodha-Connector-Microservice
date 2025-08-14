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

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getTradingsymbol() {
        return tradingsymbol;
    }

    public void setTradingsymbol(String tradingsymbol) {
        this.tradingsymbol = tradingsymbol;
    }

    public ExchangeEnum getExchange() {
        return exchange;
    }

    public void setExchange(ExchangeEnum exchange) {
        this.exchange = exchange;
    }

    public TransactionEnum getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(TransactionEnum transactionType) {
        this.transactionType = transactionType;
    }

    public OrderEnum getOrderType() {
        return orderType;
    }

    public void setOrderType(OrderEnum orderType) {
        this.orderType = orderType;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public ProductEnum getProduct() {
        return product;
    }

    public void setProduct(ProductEnum product) {
        this.product = product;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public double getTriggerPrice() {
        return triggerPrice;
    }

    public void setTriggerPrice(double triggerPrice) {
        this.triggerPrice = triggerPrice;
    }

    public int getDisclosedQuantity() {
        return disclosedQuantity;
    }

    public void setDisclosedQuantity(int disclosedQuantity) {
        this.disclosedQuantity = disclosedQuantity;
    }

    public ValidityEnum getValidity() {
        return validity;
    }

    public void setValidity(ValidityEnum validity) {
        this.validity = validity;
    }

    public Double getSquareoff() {
        return squareoff;
    }

    public void setSquareoff(Double squareoff) {
        this.squareoff = squareoff;
    }

    public Double getStoploss() {
        return stoploss;
    }

    public void setStoploss(Double stoploss) {
        this.stoploss = stoploss;
    }

    public Double getTrailingStoploss() {
        return trailingStoploss;
    }

    public void setTrailingStoploss(Double trailingStoploss) {
        this.trailingStoploss = trailingStoploss;
    }

    public VarietyEnum getVariety() {
        return variety;
    }

    public void setVariety(VarietyEnum variety) {
        this.variety = variety;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public Instant getPlacedAt() {
        return placedAt;
    }

    public void setPlacedAt(Instant placedAt) {
        this.placedAt = placedAt;
    }

    private Double squareoff;
    private Double stoploss;
    private Double trailingStoploss;

    @Enumerated(EnumType.STRING)
    private VarietyEnum variety;

    private String tag;

    private Instant placedAt;


}
