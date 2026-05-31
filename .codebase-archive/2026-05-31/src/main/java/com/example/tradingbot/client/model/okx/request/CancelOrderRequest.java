package com.example.tradingbot.client.model.okx.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CancelOrderRequest {

    private String instrumentId;
    private String orderId;
    private String clientOrderId;
}
