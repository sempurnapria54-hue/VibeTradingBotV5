package com.example.tradingbot.domain.model.okxproxy;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrdersPendingRequest {

    private String instrumentId;
    private String instrumentType;
}
