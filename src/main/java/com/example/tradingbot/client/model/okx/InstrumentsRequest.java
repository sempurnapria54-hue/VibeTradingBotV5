package com.example.tradingbot.client.model.okx;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InstrumentsRequest {

    private String instrumentType;
    private String instrumentId;
}
