package com.zerodha.service.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum InstrumentSegmentEnum {
    EQUITY("equity"),
    COMMODITY("commodity"),
    FUTURES("futures"),
    CURRENCY("currency");

    private final String code;

    InstrumentSegmentEnum(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static InstrumentSegmentEnum fromCode(String code) {
        for (InstrumentSegmentEnum e : values()) {
            if (e.code.equalsIgnoreCase(code)) {
                return e;
            }
        }
        throw new IllegalArgumentException("Invalid value: " + code);
    }

}
