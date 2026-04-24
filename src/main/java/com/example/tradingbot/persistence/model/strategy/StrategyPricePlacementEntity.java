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

    /**
     * Базовая цена, от которой строится placement.
     */
    private String baseType;

    /**
     * Тип рыночной цены, если placement опирается на market snapshot.
     */
    private String marketPriceType;

    /**
     * Сторона смещения цены относительно базы.
     */
    private String offsetSide;

    /**
     * Величина смещения в процентах.
     */
    private BigDecimal percents;
}
