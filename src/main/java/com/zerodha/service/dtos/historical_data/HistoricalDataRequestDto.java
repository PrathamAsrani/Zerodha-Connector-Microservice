package com.zerodha.service.dtos.historical_data;

import com.zerodha.service.enums.ExchangeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistoricalDataRequestDto {
    private String instrumentToken;
    private String tradingSymbol; // e.g., "ITC"
    private ExchangeEnum exchange; // e.g., "NSE"
    private LocalDate fromDate;
    private LocalDate toDate;
    private String interval; // "day", "minute", "3minute", "5minute", "10minute", "15minute", "30minute", "60minute"
    private Boolean continuous; // true for continuous data (adjusted for splits/bonuses)
    private Boolean oi; // Include Open Interest (for F&O)
}