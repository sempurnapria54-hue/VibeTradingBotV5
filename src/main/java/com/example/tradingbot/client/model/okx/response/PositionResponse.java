package com.example.tradingbot.client.model.okx.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PositionResponse {

    private String instId;
    private String instType;
    private String posSide;
    private String pos;
    private String avgPx;
    private String markPx;
    private String liqPx;
    private String upl;
    private String lever;
    private String mgnMode;
    private String uTime;
}
