package com.example.tradingbot.rest.model.response.order;

import com.example.tradingbot.rest.model.response.Auditable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AttachedStopLoss extends Auditable {

    private String internalId;
    private String externalAttachedId;
    private String externalId;
    private String status;
    private String type;
    private String externalStatus;
    private String externalType;
    private BigDecimal size;
    private BigDecimal stopLossTriggerPrice;
}
