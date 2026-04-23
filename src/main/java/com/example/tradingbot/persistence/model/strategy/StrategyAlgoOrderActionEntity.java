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

    private String actionType;

    private String conditionType;

    private Integer level;

    private StopLossSettingsEntity stopLossSettings;

    private TrailingSettingsEntity trailingSettings;

    private BigDecimal closeFractionPercents;

    private BigDecimal triggerProfitPercents;

    private String triggerPriceType;
}
