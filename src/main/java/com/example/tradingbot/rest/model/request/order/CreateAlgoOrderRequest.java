package com.example.tradingbot.rest.model.request.order;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateAlgoOrderRequest {

    private String side;
    private String type;
    private String size;
    private String triggerPrice;
    private String orderPrice;
}
