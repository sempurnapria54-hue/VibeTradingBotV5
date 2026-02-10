package com.example.tradingbot.rest.model.okxproxy;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InstrumentsRequest {

    private String instrumentType;
    private String instrumentId;
}
