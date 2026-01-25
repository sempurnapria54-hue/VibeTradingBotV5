package com.example.tradingbot.rest.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PositionCloseAlgoOrder {

    private String algoId;
    private String tpTriggerPrice;
    private String tpTriggerPriceType;
    private String slTriggerPrice;
    private String slTriggerPriceType;
    private String closeFraction;
}
