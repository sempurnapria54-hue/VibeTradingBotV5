package com.example.tradingbot.domain.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BalanceCurrencyDetail {

    private String currency;
    private String equity;
    private String cashBalance;
    private String availableBalance;
    private String sourceUpdatedAt;
    private String isolatedEquity;
    private String availableEquity;
    private String discountedEquityUsd;
    private String fixedBalance;
    private String frozenBalance;
    private String orderFrozen;
    private String liability;
    private String unrealizedPnl;
    private String unrealizedPnlLiability;
    private String crossLiability;
    private String isolatedLiability;
    private String marginRatio;
    private String initialMarginRequirement;
    private String maintenanceMarginRequirement;
    private String interest;
    private String twap;
    private String frpType;
    private String maxLoan;
    private String equityUsd;
    private String borrowFrozen;
    private String notionalLeverage;
    private String strategyEquity;
    private String isolatedUnrealizedPnl;
    private String spotInUseAmount;
    private String clSpotInUseAmount;
    private String maxSpotInUse;
    private String spotIsolatedBalance;
    private String smartSyncEquity;
    private String spotCopyTradingEquity;
    private String spotBalance;
    private String openAveragePriceUsd;
    private String accumulatedAveragePriceUsd;
    private String spotUnrealizedPnlUsd;
    private String spotUnrealizedPnlRatio;
    private String totalPnlUsd;
    private String totalPnlRatio;
    private String collateralRestrictionStatus;
    private String collateralBorrowAutoConversion;
    private Boolean collateralRestrict;
    private Boolean collateralEnabled;
    private String autoLendStatus;
    private String autoLendMatchedAmount;
    private String rewardBalance;
}
