package com.example.tradingbot.client.okx.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrdersPendingRequest {

    private String instrumentId;
    private String instrumentType;
}
