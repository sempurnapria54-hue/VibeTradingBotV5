package com.example.tradingbot.client.model.okx.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InstrumentResponse {

    private String instId;
    private String instType;
    private String baseCcy;
    private String quoteCcy;
    private String settleCcy;
    private String lotSz;
    private String minSz;
    private String ctVal;
    private String ctMult;
    private String tickSz;
}
