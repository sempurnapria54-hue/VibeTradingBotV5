package com.example.tradingbot.rest.model.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ClosePositionRequest {

    private Long exchangeId;
    private Long instrumentId;
    private String positionSide;
}
