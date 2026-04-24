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
public class StopLossSettingsEntity {

    /**
     * Способ расчёта stop-loss цены.
     */
    private String calculationType;

    /**
     * Дистанция stop-loss в процентах.
     */
    private BigDecimal distancePercents;

    /**
     * Тип trigger price на бирже: MARK, LAST или INDEX.
     */
    private String triggerPriceType;
}
