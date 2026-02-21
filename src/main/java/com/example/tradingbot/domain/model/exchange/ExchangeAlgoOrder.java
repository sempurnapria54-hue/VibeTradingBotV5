package com.example.tradingbot.domain.model.exchange;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ExchangeAlgoOrder {

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
