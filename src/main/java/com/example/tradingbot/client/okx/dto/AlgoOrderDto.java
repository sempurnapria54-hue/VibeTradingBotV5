package com.example.tradingbot.client.okx.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AlgoOrderDto {

    private String algoId;
    private String clOrdId;
    private String instId;
    private String ordType;
    private String sCode;
    private String sMsg;
}
