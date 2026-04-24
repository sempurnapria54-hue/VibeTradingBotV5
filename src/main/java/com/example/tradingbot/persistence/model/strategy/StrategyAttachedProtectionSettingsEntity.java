package com.example.tradingbot.persistence.model.strategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class StrategyAttachedProtectionSettingsEntity {

    /**
     * Тип attached protection внутри entry order.
     */
    private String attachedType;

    /**
     * Настройки стартового stop-loss для attached protection.
     */
    private StopLossSettingsEntity stopLossSettings;
}
