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
public class StrategyAlgoOrderActionEntity implements StrategyActionEntity {

    /**
     * Стабильный id action внутри JSON стратегии.
     */
    private Long id;

    /**
     * Тип действия стратегии: CREATE, AMEND или CANCEL.
     */
    private String actionType;

    /**
     * Доменный тип условия algo-order.
     */
    private String conditionType;

    /**
     * Уровень действия внутри TP/SL/grid-лесенки.
     */
    private Integer level;

    /**
     * Настройки stop-loss части условия.
     */
    private StopLossSettingsEntity stopLossSettings;

    /**
     * Настройки trailing части условия.
     */
    private TrailingSettingsEntity trailingSettings;

    /**
     * Доля закрываемой позиции в процентах.
     */
    private BigDecimal closeFractionPercents;

    /**
     * Порог профита в процентах, при котором action становится актуальным.
     */
    private BigDecimal triggerProfitPercents;

    /**
     * Тип trigger price на бирже: MARK, LAST или INDEX.
     */
    private String triggerPriceType;
}
