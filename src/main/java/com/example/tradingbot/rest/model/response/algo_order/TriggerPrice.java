package com.example.tradingbot.rest.model.response.algo_order;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TriggerPrice {

    private String type;
    private BigDecimal value;
    private String externalType;
    private BigDecimal externalValue;
}