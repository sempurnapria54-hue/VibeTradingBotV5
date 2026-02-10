package com.example.tradingbot.rest.model.okxproxy;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Order {

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
    private String createTime;
    private String updateTime;
    private String statusCode;
    private String statusMessage;
}
