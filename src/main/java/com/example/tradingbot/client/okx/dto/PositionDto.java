package com.example.tradingbot.client.okx.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PositionDto {

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
