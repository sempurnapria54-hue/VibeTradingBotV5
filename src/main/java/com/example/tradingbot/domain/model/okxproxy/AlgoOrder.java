package com.example.tradingbot.domain.model.okxproxy;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AlgoOrder {

    private String algoOrderId;
    private String clientOrderId;
    private String instrumentId;
    private String orderType;
    private String state;
    private String size;
    private String triggerPrice;
    private String orderPrice;
    private String takeProfitTriggerPrice;
    private String takeProfitOrderPrice;
    private String stopLossTriggerPrice;
    private String stopLossOrderPrice;
    private String callbackRatio;
    private String callbackSpread;
    private String createTime;
    private String updateTime;
    private String statusCode;
    private String statusMessage;
}
