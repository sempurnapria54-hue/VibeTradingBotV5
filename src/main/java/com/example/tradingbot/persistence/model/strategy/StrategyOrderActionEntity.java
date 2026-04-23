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

    private String actionType;

    private String orderType;

    private String direction;

    private BigDecimal allocationPercents;

    private Integer level;

    private StrategyPricePlacementEntity placement;

    private StrategyAttachedProtectionSettingsEntity attachedProtection;
}
