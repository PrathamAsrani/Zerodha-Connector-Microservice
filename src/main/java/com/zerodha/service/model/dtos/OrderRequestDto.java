package com.zerodha.service.model.dtos;

import com.zerodha.service.enums.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;

@Data
@AllArgsConstructor
@Getter
public class OrderRequestDto {
    public String tradingsymbol;
    public ExchangeEnum exchange;
    public TransactionEnum transactionType;
    public OrderEnum orderType;
    public int quantity;
    public ProductEnum product;
    public double price;
    public double triggerPrice;
    public int disclosedQuantity;
    public ValidityEnum validity;
    public Double squareoffValue;
    public Double stoplossValue;
    public Double trailingStoploss;
    public VarietyEnum variety;
    public String tag;

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

    public Double getSquareoffValue() {
        return squareoffValue;
    }

    public void setSquareoffValue(Double squareoffValue) {
        this.squareoffValue = squareoffValue;
    }

    public Double getStoplossValue() {
        return stoplossValue;
    }

    public void setStoplossValue(Double stoplossValue) {
        this.stoplossValue = stoplossValue;
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
}
