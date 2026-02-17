package com.example.tradingbot.domain.model.trading;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ClosePositionResult {

    private String instrumentId;
    private String positionSide;
    private String updateTime;
}
