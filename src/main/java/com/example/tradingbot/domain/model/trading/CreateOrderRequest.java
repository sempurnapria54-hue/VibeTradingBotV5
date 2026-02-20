package com.example.tradingbot.domain.model.trading;

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
