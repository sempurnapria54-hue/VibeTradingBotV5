package com.example.tradingbot.rest.model.okxproxy;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrderDetailsRequest {

    private String instrumentId;
    private String orderId;
    private String clientOrderId;
}
