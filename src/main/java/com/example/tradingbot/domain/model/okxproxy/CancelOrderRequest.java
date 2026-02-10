package com.example.tradingbot.domain.model.okxproxy;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CancelOrderRequest {

    private String instrumentId;
    private String orderId;
    private String clientOrderId;
}
