package com.example.tradingbot.rest.model.response.trading;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ClosePositionResponse {

    private String instrumentId;
    private String positionSide;
    private String updateTime;
}
