package com.zerodha.service.dtos.historical_data;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InstrumentSearchRequestDto {
    private String tradingSymbol; // e.g., "ITC"
    private String exchange; // e.g., "NSE", "BSE"
}
