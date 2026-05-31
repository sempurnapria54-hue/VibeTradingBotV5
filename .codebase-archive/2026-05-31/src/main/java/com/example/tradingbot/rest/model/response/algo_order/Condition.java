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
public class Condition {

    private String type;

    private BigDecimal closeFraction;
    private Trigger trigger;
    private Trailing trailing;
}