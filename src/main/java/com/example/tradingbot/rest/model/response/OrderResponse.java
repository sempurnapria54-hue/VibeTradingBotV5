package com.example.tradingbot.rest.model.response;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public class OrderResponse extends Auditable {

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
    private OffsetDateTime exchangeCreatedAt;
    private OffsetDateTime exchangeModifiedAt;
}
