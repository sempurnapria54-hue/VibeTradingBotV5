package com.example.tradingbot.client.model.okx;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CancelAlgoOrderRequest {

    private String instrumentId;
    private String algoOrderId;
    private String clientOrderId;
}
