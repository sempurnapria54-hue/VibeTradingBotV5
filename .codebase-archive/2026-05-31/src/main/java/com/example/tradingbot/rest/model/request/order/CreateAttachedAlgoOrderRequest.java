package com.example.tradingbot.rest.model.request.order;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateAttachedAlgoOrderRequest {

    private String type;
    private BigDecimal size;
    private BigDecimal triggerPrice;
}
