package com.example.tradingbot.rest.model.response;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public class Order {

    private Long id;
    private Long instrumentId;
    private String internalId;
    private String externalId;
    private String status;
    private String type;
    private String side;
    private String externalStatus;
    private String price;
    private String size;
    private String accumulatedFillSize;
    private String averagePrice;
    private String fee;
    private Long createTime;
    private Long updateTime;
    private OffsetDateTime createdAt;
    private String createdBy;
    private OffsetDateTime modifiedAt;
    private String modifiedBy;
}
