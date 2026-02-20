package com.example.tradingbot.client.model.okx;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CandlesRequest {

    private String instrumentId;
    private String bar;
    private String after;
    private String before;
    private String limit;
}
