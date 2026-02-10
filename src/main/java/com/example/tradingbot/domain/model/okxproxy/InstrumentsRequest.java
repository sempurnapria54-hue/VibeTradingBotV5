package com.example.tradingbot.domain.model.okxproxy;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InstrumentsRequest {

    private String instrumentType;
    private String instrumentId;
}
