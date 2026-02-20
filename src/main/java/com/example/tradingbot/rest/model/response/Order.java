package com.example.tradingbot.rest.model.response;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public class Order {

    private Long id;
    private Long exchangeId;
    private Long instrumentId;
    private String clientOrderId;
    private String exchangeOrderId;
    private String status;
    private String type;
    private String side;
    private String state;
    private String ordType;
    private String px;
    private String sz;
    private String fillSz;
    private String avgPx;
    private String fee;
    private Long cTime;
    private Long uTime;
    private OffsetDateTime createdAt;
    private String createdBy;
    private OffsetDateTime modifiedAt;
    private String modifiedBy;
}
