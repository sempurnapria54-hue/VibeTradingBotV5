package com.example.tradingbot.client.okx.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OkxFillsRequest {

    private String instType;
    private String instId;
    private String ordId;
    private String after;
    private String before;
    private String limit;
}
