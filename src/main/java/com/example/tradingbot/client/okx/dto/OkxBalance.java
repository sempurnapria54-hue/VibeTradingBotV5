package com.example.tradingbot.client.okx.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OkxBalance {

    @JsonProperty("uTime")
    private String sourceUpdatedAt;

    @JsonProperty("totalEq")
    private String totalEquityUsd;

    @JsonProperty("isoEq")
    private String isolatedEquityUsd;

    @JsonProperty("adjEq")
    private String adjustedEquityUsd;

    @JsonProperty("availEq")
    private String availableEquityUsd;

    @JsonProperty("ordFroz")
    private String orderFrozenUsd;

    @JsonProperty("imr")
    private String initialMarginRequirementUsd;

    @JsonProperty("mmr")
    private String maintenanceMarginRequirementUsd;

    @JsonProperty("borrowFroz")
    private String borrowFrozenUsd;

    @JsonProperty("mgnRatio")
    private String marginRatio;

    @JsonProperty("notionalUsd")
    private String notionalUsd;

    @JsonProperty("notionalUsdForBorrow")
    private String notionalUsdForBorrow;

    @JsonProperty("notionalUsdForSwap")
    private String notionalUsdForSwap;

    @JsonProperty("notionalUsdForFutures")
    private String notionalUsdForFutures;

    @JsonProperty("notionalUsdForOption")
    private String notionalUsdForOption;

    @JsonProperty("upl")
    private String unrealizedPnlUsd;

    @JsonProperty("delta")
    private String delta;

    @JsonProperty("deltaLever")
    private String deltaLever;

    @JsonProperty("deltaNeutralStatus")
    private String deltaNeutralStatus;

    private List<OkxBalanceDetail> details;
}
