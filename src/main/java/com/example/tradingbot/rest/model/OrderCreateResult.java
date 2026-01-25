package com.example.tradingbot.rest.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrderCreateResult {

    private String orderId;
    private String clientOrderId;
    private String tag;
    private String exchangeProcessedAt;
    private String resultCode;
    private String resultMessage;
}
