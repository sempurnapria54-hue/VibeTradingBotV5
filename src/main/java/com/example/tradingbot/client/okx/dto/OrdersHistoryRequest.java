package com.example.tradingbot.client.okx.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrdersHistoryRequest {

    private String instrumentType;
    private String instrumentId;
    private String state;
    private String after;
    private String before;
    private String limit;
}
