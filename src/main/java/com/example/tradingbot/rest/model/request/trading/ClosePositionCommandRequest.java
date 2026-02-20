package com.example.tradingbot.rest.model.request.trading;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ClosePositionCommandRequest {

    private Long exchangeId;
    private Long instrumentId;
    private String positionSide;
}
