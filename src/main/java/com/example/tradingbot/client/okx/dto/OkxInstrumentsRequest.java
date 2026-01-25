package com.example.tradingbot.client.okx.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OkxInstrumentsRequest {

    private String instType;
    private String instId;
    private String instFamily;
}
