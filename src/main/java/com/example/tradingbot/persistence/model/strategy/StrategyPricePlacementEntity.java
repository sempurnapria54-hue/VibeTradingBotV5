package com.example.tradingbot.persistence.model.strategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class StrategyPricePlacementEntity {

    private String baseType;

    private String marketPriceType;

    private String offsetSide;

    private BigDecimal percents;
}
