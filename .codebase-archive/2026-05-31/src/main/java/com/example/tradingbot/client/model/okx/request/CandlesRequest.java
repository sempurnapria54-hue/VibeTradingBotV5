package com.example.tradingbot.client.model.okx.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CandlesRequest {

    private String externalInstrumentId;
    private String externalTimeframe;
    private String after;
    private String before;
    private String limit;
}
