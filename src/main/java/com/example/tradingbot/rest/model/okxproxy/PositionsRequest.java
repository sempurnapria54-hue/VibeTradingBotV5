package com.example.tradingbot.rest.model.okxproxy;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PositionsRequest {

    private String instrumentId;
    private String instrumentType;
}
