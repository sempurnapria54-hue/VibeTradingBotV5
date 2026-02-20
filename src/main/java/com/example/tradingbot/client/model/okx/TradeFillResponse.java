package com.example.tradingbot.client.model.okx;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TradeFillResponse {

    private String billId;
    private String tradeId;
    private String ordId;
    private String instId;
    private String side;
    private String fillSz;
    private String fillPx;
    private String fillPnl;
    private String ts;
}
