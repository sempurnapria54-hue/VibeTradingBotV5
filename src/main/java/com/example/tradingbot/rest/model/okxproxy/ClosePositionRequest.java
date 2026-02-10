package com.example.tradingbot.rest.model.okxproxy;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ClosePositionRequest {

    private String instrumentId;
    private String marginMode;
    private String positionSide;
    private String currency;
    private String autoCancel;
}
