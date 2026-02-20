package com.example.tradingbot.rest.model.request.order;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateAlgoOrderRequest {

    private String side;
    private String ordType;
    private String sz;
    private String triggerPx;
    private String ordPx;
}
