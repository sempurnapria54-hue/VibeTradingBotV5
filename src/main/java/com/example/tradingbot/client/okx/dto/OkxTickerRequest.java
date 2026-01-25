package com.example.tradingbot.client.okx.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OkxTickerRequest {

    private String instId;
    private String instType;
}
