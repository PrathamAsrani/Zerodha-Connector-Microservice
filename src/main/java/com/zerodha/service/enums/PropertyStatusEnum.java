package com.zerodha.service.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum PropertyStatusEnum {
    ACTIVE("ACTIVE"),
    INACTIVE("INACTIVE");

    private final String code;

    PropertyStatusEnum(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static PropertyStatusEnum fromCode(String code) {
        for (PropertyStatusEnum e : values()) {
            if (e.code.equalsIgnoreCase(code)) {
                return e;
            }
        }
        throw new IllegalArgumentException("Invalid value: " + code);
    }
}
