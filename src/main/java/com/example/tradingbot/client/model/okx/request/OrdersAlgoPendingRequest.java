package com.example.tradingbot.client.model.okx.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrdersAlgoPendingRequest {

    private String orderType;
    private String instrumentId;
    private String instrumentType;
}
