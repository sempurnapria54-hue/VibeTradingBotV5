package com.example.tradingbot.rest.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrderCancelResult {

    private String orderId;
    private String clientOrderId;
    private String resultCode;
    private String resultMessage;
}
