package com.zerodha.service.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum GttTriggerEnum {
    OCO("two-leg"),
    SINGLE("single");

    private final String code;

    GttTriggerEnum(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static GttTriggerEnum fromCode(String code) {
        for (GttTriggerEnum e : values()) {
            if (e.code.equalsIgnoreCase(code)) {
                return e;
            }
        }
        throw new IllegalArgumentException("Invalid value: " + code);
    }

}
