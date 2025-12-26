package com.zerodha.service.dtos.historical_data;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InstrumentInfoDto {
    private Long instrumentToken;
    private Long exchangeToken;
    private String tradingSymbol;
    private String name;
    private Double lastPrice;
    private LocalDate expiry;
    private Double strike;
    private Double tickSize;
    private Integer lotSize;
    private String instrumentType;
    private String segment;
    private String exchange;
}
