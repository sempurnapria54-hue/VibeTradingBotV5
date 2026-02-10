package com.example.tradingbot.client.okx.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FillsRequest {

    private String instrumentType;
    private String instrumentId;
    private String orderId;
    private String after;
    private String before;
    private String limit;
}
