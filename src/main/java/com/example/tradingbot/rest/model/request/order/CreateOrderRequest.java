package com.example.tradingbot.rest.model.request.order;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateOrderRequest {

    private String side;
    private String type;
    private String sz;
    private String px;
}
