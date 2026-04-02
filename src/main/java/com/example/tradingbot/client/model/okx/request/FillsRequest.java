package com.example.tradingbot.client.model.okx.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FillsRequest {

    private String externalInstrumentType;
    private String externalInstrumentId;
    private String externalOrderId;
    private String after;
    private String before;
    private String limit;
}
