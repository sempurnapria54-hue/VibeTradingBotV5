package com.example.tradingbot.domain.model.okxproxy;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CancelAlgoOrderRequest {

    private String instrumentId;
    private String algoOrderId;
}
