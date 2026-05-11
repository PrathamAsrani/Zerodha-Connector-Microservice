package com.zerodha.service.dtos.scraper;

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
public class ScreenerLtpDto {

    @JsonProperty("symbol")
    private String symbol;

    @JsonProperty("company_name")
    private String companyName;

    @JsonProperty("last_price")
    private Double lastPrice;

    @JsonProperty("source")
    private String source;

    @JsonProperty("source_url")
    private String sourceUrl;

    @JsonProperty("fetched_at")
    private Instant fetchedAt;

    @JsonProperty("from_cache")
    private Boolean fromCache;
}
