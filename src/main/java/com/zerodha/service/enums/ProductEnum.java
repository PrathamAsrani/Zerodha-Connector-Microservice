package com.zerodha.service.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ProductEnum {
    MIS("MIS"),
    CNC("CNC"),
    NRML("NRML"),
    MTF("MTF");

    private final String code;

    ProductEnum(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static ProductEnum fromCode(String code) {
        for (ProductEnum e : values()) {
            if (e.code.equalsIgnoreCase(code)) {
                return e;
            }
        }
        throw new IllegalArgumentException("Invalid value: " + code);
    }

}
