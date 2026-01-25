package com.example.tradingbot.domain.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AlgoOrderResult {

    private String algoId;
    private String algoClOrdId;
    private String clOrdId;
    private String resultCode;
    private String resultMessage;
    private String tag;
}
