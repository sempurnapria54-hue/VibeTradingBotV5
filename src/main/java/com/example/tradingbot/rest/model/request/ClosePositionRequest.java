package com.example.tradingbot.rest.model.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ClosePositionRequest {

    private String exchangeId;
    private String instrumentId;
    private String positionSide;
}
