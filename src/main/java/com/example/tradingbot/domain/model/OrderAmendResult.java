package com.example.tradingbot.domain.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrderAmendResult {

    private String orderId;
    private String clientOrderId;
    private String exchangeProcessedAt;
    private String requestId;
    private String resultCode;
    private String resultMessage;
}
