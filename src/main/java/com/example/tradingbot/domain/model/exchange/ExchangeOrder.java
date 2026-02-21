package com.example.tradingbot.domain.model.exchange;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ExchangeOrder {

    private String orderId;
    private String clientOrderId;
    private String instrumentId;
    private String instrumentType;
    private String side;
    private String positionSide;
    private String orderType;
    private String price;
    private String size;
    private String state;
    private String averagePrice;
    private String accumulatedFillSize;
    private String fee;
    private String createTime;
    private String updateTime;
    private String statusCode;
    private String statusMessage;
}
