package com.zerodha.service.dtos.ticker;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LtpResponseDto {

    @JsonProperty("trading_symbol")
    private String tradingSymbol;

    @JsonProperty("exchange")
    private String exchange;

    @JsonProperty("instrument_token")
    private Long instrumentToken;

    @JsonProperty("last_price")
    private Double lastPrice;

    @JsonProperty("timestamp")
    private Instant timestamp;
}
