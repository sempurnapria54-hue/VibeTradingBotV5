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
public class StrategyPositionActionEntity implements StrategyActionEntity {

    /**
     * Стабильный id action внутри JSON стратегии.
     */
    private Long id;

    /**
     * Тип действия стратегии: CLOSE_FULL или CLOSE_PARTIAL.
     */
    private String actionType;

    /**
     * Уровень действия внутри exit-лесенки.
     */
    private Integer level;

    /**
     * Доля закрываемой позиции в процентах.
     */
    private BigDecimal closeFractionPercents;
}
