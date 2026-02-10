package com.example.tradingbot.rest.model.okxproxy;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrdersPendingRequest {

    private String instrumentId;
    private String instrumentType;
}
