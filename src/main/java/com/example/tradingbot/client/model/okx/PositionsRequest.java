package com.example.tradingbot.client.model.okx;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PositionsRequest {

    private String instrumentId;
    private String instrumentType;
}
