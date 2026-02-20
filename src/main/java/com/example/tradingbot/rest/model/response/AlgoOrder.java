package com.example.tradingbot.rest.model.response;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public class AlgoOrder {

    private Long id;
    private Long instrumentId;
    private String clientAlgoOrderId;
    private String exchangeAlgoOrderId;
    private String status;
    private String algoType;
    private String state;
    private String sz;
    private String triggerPx;
    private String ordPx;
    private String tpTriggerPx;
    private String tpOrdPx;
    private String slTriggerPx;
    private String slOrdPx;
    private String callbackRatio;
    private String callbackSpread;
    private Long cTime;
    private Long uTime;
    private OffsetDateTime createdAt;
    private String createdBy;
    private OffsetDateTime modifiedAt;
    private String modifiedBy;
}
