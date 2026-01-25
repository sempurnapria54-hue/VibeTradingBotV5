package com.example.tradingbot.domain.model;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Balance {

    private String sourceUpdatedAt;
    private String totalEquityUsd;
    private String isolatedEquityUsd;
    private String adjustedEquityUsd;
    private String availableEquityUsd;
    private String orderFrozenUsd;
    private String initialMarginRequirementUsd;
    private String maintenanceMarginRequirementUsd;
    private String borrowFrozenUsd;
    private String marginRatio;
    private String notionalUsd;
    private String notionalUsdForBorrow;
    private String notionalUsdForSwap;
    private String notionalUsdForFutures;
    private String notionalUsdForOption;
    private String unrealizedPnlUsd;
    private String delta;
    private String deltaLever;
    private String deltaNeutralStatus;
    private List<BalanceCurrencyDetail> details;
}
