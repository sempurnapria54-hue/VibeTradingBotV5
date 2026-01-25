package com.example.tradingbot.client.okx.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OkxCandlesRequest {

    private String instId;
    private String bar;
    private String after;
    private String before;
    private String limit;
}
