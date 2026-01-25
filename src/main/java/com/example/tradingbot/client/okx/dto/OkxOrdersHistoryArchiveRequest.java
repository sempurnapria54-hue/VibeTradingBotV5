package com.example.tradingbot.client.okx.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OkxOrdersHistoryArchiveRequest {

    private String instType;
    private String instFamily;
    private String instId;
    private String ordType;
    private String state;
    private String category;
    private String after;
    private String before;
    private String begin;
    private String end;
    private String limit;
}
