package com.zerodha.service.dtos.portfolio;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class HoldingDto {

    @JsonProperty("tradingsymbol")
    private String tradingSymbol;

    @JsonProperty("exchange")
    private String exchange;

    @JsonProperty("instrument_token")
    private Long instrumentToken;

    @JsonProperty("isin")
    private String isin;

    @JsonProperty("product")
    private String product;

    @JsonProperty("price")
    private Double price;

    @JsonProperty("quantity")
    private Integer quantity;

    @JsonProperty("used_quantity")
    private Integer usedQuantity;

    @JsonProperty("t1_quantity")
    private Integer t1Quantity;

    @JsonProperty("realised_quantity")
    private Integer realisedQuantity;

    @JsonProperty("authorised_quantity")
    private Integer authorisedQuantity;

    @JsonProperty("authorised_date")
    private String authorisedDate;

    @JsonProperty("opening_quantity")
    private Integer openingQuantity;

    @JsonProperty("short_quantity")
    private Integer shortQuantity;

    @JsonProperty("collateral_quantity")
    private Integer collateralQuantity;

    @JsonProperty("collateral_type")
    private String collateralType;

    @JsonProperty("discrepancy")
    private Boolean discrepancy;

    @JsonProperty("average_price")
    private Double averagePrice;

    @JsonProperty("last_price")
    private Double lastPrice;

    @JsonProperty("close_price")
    private Double closePrice;

    @JsonProperty("pnl")
    private Double pnl;

    @JsonProperty("day_change")
    private Double dayChange;

    @JsonProperty("day_change_percentage")
    private Double dayChangePercentage;
}
