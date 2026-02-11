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
    private String state;
    private String sz;
    private String triggerPx;
    private String ordPx;
    private String tpTriggerPx;
    private String tpOrdPx;
    private String slTriggerPx;
    private String slOrdPx;
    private String callbackRatio;
    private String callbackSpread;
    private String cTime;
    private String uTime;
    private String sCode;
    private String sMsg;
}
