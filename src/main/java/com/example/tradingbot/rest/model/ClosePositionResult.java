package com.example.tradingbot.rest.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ClosePositionResult {

    private String exchangeInstrumentName;
    private String positionSide;
}
