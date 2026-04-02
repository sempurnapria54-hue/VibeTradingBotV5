package com.example.tradingbot.rest.model.response.order;

import com.example.tradingbot.rest.model.response.Auditable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Order extends Auditable {

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
    private List<AttachedStopLoss> attachedStopLosses;
}
