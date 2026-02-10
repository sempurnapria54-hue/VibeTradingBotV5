package com.example.tradingbot.client.okx.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CancelOrderRequest {

    private String instrumentId;
    private String orderId;
    private String clientOrderId;
}
