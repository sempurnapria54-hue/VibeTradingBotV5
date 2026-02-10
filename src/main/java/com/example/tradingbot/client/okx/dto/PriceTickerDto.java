package com.example.tradingbot.client.okx.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PriceTickerDto {

    private String instId;
    private String last;
    private String askPx;
    private String bidPx;
    private String ts;
}
