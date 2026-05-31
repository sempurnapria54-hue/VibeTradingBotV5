package com.example.tradingbot.rest.model.request.order;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ActivateOrderRequest {

    private String exchangeInternalId;
    private String instrumentInternalId;
    private String dealInternalId;
    private String internalId;
}
