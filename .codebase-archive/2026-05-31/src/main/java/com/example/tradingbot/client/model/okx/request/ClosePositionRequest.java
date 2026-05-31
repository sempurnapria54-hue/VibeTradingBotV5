package com.example.tradingbot.client.model.okx.request;

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
