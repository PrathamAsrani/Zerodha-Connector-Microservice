package com.zerodha.service.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ExchangeEnum {
    NSE("NSE"),
    BSE("BSE"),
    NFO("NFO"),
    BFO("BFO"),
    MCX("MCX"),
    CDS("CDS");

    private final String code;

    ExchangeEnum(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static ExchangeEnum fromCode(String code) {
        for (ExchangeEnum e : values()) {
            if (e.code.equalsIgnoreCase(code)) {
                return e;
            }
        }
        throw new IllegalArgumentException("Invalid value: " + code);
    }

}
