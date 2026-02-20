package com.example.tradingbot.client.model.okx;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AmendOrderRequest {

    private String instrumentId;
    private String orderId;
    private String clientOrderId;
    private String newSize;
    private String newPrice;
}
