package com.example.tradingbot.domain.model.okxproxy;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AlgoOrder {

    private String algoOrderId;
    private String clientOrderId;
    private String instrumentId;
    private String orderType;
    private String statusCode;
    private String statusMessage;
}
