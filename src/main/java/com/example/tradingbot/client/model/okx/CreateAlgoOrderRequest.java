package com.example.tradingbot.client.model.okx;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateAlgoOrderRequest {

    private String instrumentId;
    private String tradeMode;
    private String side;
    private String positionSide;
    private String orderType;
    private String size;
    private String triggerPrice;
    private String orderPrice;
    private String clientOrderId;
}
