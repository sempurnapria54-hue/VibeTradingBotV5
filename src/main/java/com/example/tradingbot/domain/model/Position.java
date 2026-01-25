package com.example.tradingbot.domain.model;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Position {

    private String exchangeInstrumentName;
    private String instrumentType;
    private String marginMode;
    private String positionSide;
    private String marginCurrency;
    private String exchangePosId;
    private String lastTradeId;
    private String positionContracts;
    private String averagePrice;
    private String markPrice;
    private String lastPrice;
    private String breakEvenPrice;
    private String leverage;
    private String liquidationPrice;
    private String positionMargin;
    private String notionalUsd;
    private String marginRatio;
    private String maintenanceMargin;
    private String adl;
    private String unrealizedPnl;
    private String unrealizedPnlRatio;
    private String realizedPnl;
    private String fundingFee;
    private String fee;
    private List<PositionCloseAlgoOrder> closeAlgoOrders;
    private String sourceCreatedAt;
    private String sourceUpdatedAt;
}
