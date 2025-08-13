package com.zerodha.service.model.dtos;

public class OrderResponseDto {

    private String orderId;

    // No-args constructor for JSON serialization
    public OrderResponseDto() {}

    // All-args constructor for easy creation
    public OrderResponseDto(String orderId) {
        this.orderId = orderId;
    }

    // Getter
    public String getOrderId() {
        return orderId;
    }

    // Setter
    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }
}
