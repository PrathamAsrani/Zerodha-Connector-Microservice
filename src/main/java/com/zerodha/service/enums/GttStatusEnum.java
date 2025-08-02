package com.zerodha.service.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum GttStatusEnum {
    ACTIVE("active"),
    TRIGGERED("triggered"),
    DISABLED("disabled"),
    EXPIRED("expired"),
    CANCELLED("cancelled"),
    REJECTED("rejected"),
    DELETED("deleted");

    private final String code;

    GttStatusEnum(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static GttStatusEnum fromCode(String code) {
        for (GttStatusEnum e : values()) {
            if (e.code.equalsIgnoreCase(code)) {
                return e;
            }
        }
        throw new IllegalArgumentException("Invalid value: " + code);
    }

}
