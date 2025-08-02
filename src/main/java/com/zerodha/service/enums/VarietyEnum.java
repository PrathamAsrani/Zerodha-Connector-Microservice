package com.zerodha.service.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum VarietyEnum {
    REGULAR("regular"),
    BO("bo"),
    CO("co"),
    AMO("amo"),
    ICEBERG("iceberg"),
    AUCTION("auction");

    private final String code;

    VarietyEnum(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static VarietyEnum fromCode(String code) {
        for (VarietyEnum e : values()) {
            if (e.code.equalsIgnoreCase(code)) {
                return e;
            }
        }
        throw new IllegalArgumentException("Invalid value: " + code);
    }

}
