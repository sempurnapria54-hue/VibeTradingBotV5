package com.example.tradingbot.client.okx.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrderResponse {

    private String ordId;
    private String clOrdId;
    private String instId;
    private String instType;
    private String side;
    private String posSide;
    private String ordType;
    private String px;
    private String sz;
    private String state;
    private String avgPx;
    private String accFillSz;
    private String fee;
    private String cTime;
    private String uTime;
    private String sCode;
    private String sMsg;
}
