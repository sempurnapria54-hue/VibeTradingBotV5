package com.example.tradingbot.rest.model.response;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public class AlgoOrderResponse {

    private String instrumentInternalId;
    private String internalOrderId;
    private String externalId;
    private String status;
    private String type;
    private String externalStatus;
    private String size;
    private String triggerPrice;
    private String orderPrice;
    private String takeProfitTriggerPrice;
    private String takeProfitOrderPrice;
    private String stopLossTriggerPrice;
    private String stopLossOrderPrice;
    private String callbackRatio;
    private String callbackStep;
    private Long createTime;
    private Long updateTime;
    private OffsetDateTime createdAt;
    private String createdBy;
    private OffsetDateTime modifiedAt;
    private String modifiedBy;
}
