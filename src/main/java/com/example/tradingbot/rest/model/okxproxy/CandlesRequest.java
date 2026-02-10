package com.example.tradingbot.rest.model.okxproxy;

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
