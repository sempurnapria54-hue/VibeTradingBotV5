package com.example.tradingbot.client.model.okx.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PriceTickerResponse {

    private String instId;
    private String last;
    private String markPx;
    private String idxPx;
    private String askPx;
    private String bidPx;
    private String ts;
}
