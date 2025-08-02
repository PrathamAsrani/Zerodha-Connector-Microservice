package com.zerodha.service.model.dtos;

import com.zerodha.service.enums.*;

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
}
