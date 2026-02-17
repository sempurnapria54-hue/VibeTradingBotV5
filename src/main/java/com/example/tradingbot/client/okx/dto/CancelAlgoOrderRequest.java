package com.example.tradingbot.client.okx.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CancelAlgoOrderRequest {

    private String instrumentId;
    private String algoOrderId;
    private String clientOrderId;
}
