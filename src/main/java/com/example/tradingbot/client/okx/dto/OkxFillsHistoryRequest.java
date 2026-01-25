package com.example.tradingbot.client.okx.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OkxFillsHistoryRequest {

    private String instType;
    private String instId;
    private String ordId;
    private String after;
    private String before;
    private String begin;
    private String end;
    private String limit;
}
