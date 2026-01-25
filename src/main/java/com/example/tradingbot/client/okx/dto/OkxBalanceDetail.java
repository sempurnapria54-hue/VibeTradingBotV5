package com.example.tradingbot.client.okx.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OkxBalanceDetail {

    @JsonProperty("ccy")
    private String currency;

    @JsonProperty("eq")
    private String equity;

    @JsonProperty("cashBal")
    private String cashBalance;

    @JsonProperty("availBal")
    private String availableBalance;

    @JsonProperty("uTime")
    private String sourceUpdatedAt;

    @JsonProperty("isoEq")
    private String isolatedEquity;

    @JsonProperty("availEq")
    private String availableEquity;

    @JsonProperty("disEq")
    private String discountedEquityUsd;

    @JsonProperty("fixedBal")
    private String fixedBalance;

    @JsonProperty("frozenBal")
    private String frozenBalance;

    @JsonProperty("ordFrozen")
    private String orderFrozen;

    @JsonProperty("liab")
    private String liability;

    @JsonProperty("upl")
    private String unrealizedPnl;

    @JsonProperty("uplLiab")
    private String unrealizedPnlLiability;

    @JsonProperty("crossLiab")
    private String crossLiability;

    @JsonProperty("isoLiab")
    private String isolatedLiability;

    @JsonProperty("mgnRatio")
    private String marginRatio;

    @JsonProperty("imr")
    private String initialMarginRequirement;

    @JsonProperty("mmr")
    private String maintenanceMarginRequirement;

    @JsonProperty("interest")
    private String interest;

    @JsonProperty("twap")
    private String twap;

    @JsonProperty("frpType")
    private String frpType;

    @JsonProperty("maxLoan")
    private String maxLoan;

    @JsonProperty("eqUsd")
    private String equityUsd;

    @JsonProperty("borrowFroz")
    private String borrowFrozen;

    @JsonProperty("notionalLever")
    private String notionalLeverage;

    @JsonProperty("stgyEq")
    private String strategyEquity;

    @JsonProperty("isoUpl")
    private String isolatedUnrealizedPnl;

    @JsonProperty("spotInUseAmt")
    private String spotInUseAmount;

    @JsonProperty("clSpotInUseAmt")
    private String clSpotInUseAmount;

    @JsonProperty("maxSpotInUse")
    private String maxSpotInUse;

    @JsonProperty("spotIsoBal")
    private String spotIsolatedBalance;

    @JsonProperty("smtSyncEq")
    private String smartSyncEquity;

    @JsonProperty("spotCopyTradingEq")
    private String spotCopyTradingEquity;

    @JsonProperty("spotBal")
    private String spotBalance;

    @JsonProperty("openAvgPx")
    private String openAveragePriceUsd;

    @JsonProperty("accAvgPx")
    private String accumulatedAveragePriceUsd;

    @JsonProperty("spotUpl")
    private String spotUnrealizedPnlUsd;

    @JsonProperty("spotUplRatio")
    private String spotUnrealizedPnlRatio;

    @JsonProperty("totalPnl")
    private String totalPnlUsd;

    @JsonProperty("totalPnlRatio")
    private String totalPnlRatio;

    @JsonProperty("colRes")
    private String collateralRestrictionStatus;

    @JsonProperty("colBorrAutoConversion")
    private String collateralBorrowAutoConversion;

    @JsonProperty("collateralRestrict")
    private Boolean collateralRestrict;

    @JsonProperty("collateralEnabled")
    private Boolean collateralEnabled;

    @JsonProperty("autoLendStatus")
    private String autoLendStatus;

    @JsonProperty("autoLendMtAmt")
    private String autoLendMatchedAmount;

    @JsonProperty("rewardBal")
    private String rewardBalance;
}
