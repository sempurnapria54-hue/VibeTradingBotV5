package com.example.tradingbot.domain.model.okxproxy;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrdersAlgoPendingRequest {

    private String orderType;
    private String instrumentId;
    private String instrumentType;
}
