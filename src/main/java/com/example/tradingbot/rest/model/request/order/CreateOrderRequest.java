package com.example.tradingbot.rest.model.request.order;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequest {

    private String dealInternalId;
    private String type;
    private String side;
    private BigDecimal price;
    private BigDecimal size;
    private List<CreateAttachedAlgoOrderRequest> attachedAlgoOrderRequests;
}
