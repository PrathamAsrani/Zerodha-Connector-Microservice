package com.zerodha.service.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum OrderStatusEnum {
    CANCELLED("CANCELLED"),
    REJECTED("REJECTED"),
    COMPLETE("COMPLETE"),
    OPEN("OPEN"),
    LAPSED("LAPSED"),
    TRIGGER_PENDING("TRIGGER PENDING");

    private final String code;

    OrderStatusEnum(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static OrderStatusEnum fromCode(String code) {
        for (OrderStatusEnum e : values()) {
            if (e.code.equalsIgnoreCase(code)) {
                return e;
            }
        }
        throw new IllegalArgumentException("Invalid value: " + code);
    }

}
