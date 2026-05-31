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
public class StrategyOrderActionEntity implements StrategyActionEntity {

    /**
     * Стабильный id action внутри JSON стратегии.
     */
    private Long id;

    /**
     * Тип действия стратегии: CREATE, AMEND или CANCEL.
     */
    private String actionType;

    /**
     * Доменный тип runtime order.
     */
    private String orderType;

    /**
     * Направление стратегии: LONG или SHORT.
     */
    private String direction;

    /**
     * Доля расчётного объёма, которую должен занять этот order.
     */
    private BigDecimal allocationPercents;

    /**
     * Уровень действия внутри grid/лесенки.
     */
    private Integer level;

    /**
     * Правило определения цены order.
     */
    private StrategyPricePlacementEntity placement;

    /**
     * Настройки attached protection, если order создаётся вместе с SL.
     */
    private StrategyAttachedProtectionSettingsEntity attachedProtection;
}
