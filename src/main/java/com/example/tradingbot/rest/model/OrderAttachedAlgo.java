package com.example.tradingbot.rest.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrderAttachedAlgo {

    private String attachAlgoId;
    private String attachAlgoClientOrderId;
    private String tpOrderKind;
    private String tpTriggerPrice;
    private String tpTriggerRatio;
    private String tpTriggerPriceType;
    private String tpOrderPrice;
    private String slTriggerPrice;
    private String slTriggerRatio;
    private String slTriggerPriceType;
    private String slOrderPrice;
    private String size;
    private String amendPriceOnTriggerType;
    private String failCode;
    private String failReason;
}
