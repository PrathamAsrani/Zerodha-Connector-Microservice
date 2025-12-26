package com.zerodha.service.dtos.historical_data;

import com.zerodha.service.enums.ExchangeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistoricalDataResponseDto {
    private String instrumentToken;
    private String tradingSymbol;
    private ExchangeEnum exchange;
    private LocalDate fromDate;
    private LocalDate toDate;
    private String interval;
    private List<CandleData> candles;
    private Integer totalRecords;

    private String message;
    private Boolean success;
}
