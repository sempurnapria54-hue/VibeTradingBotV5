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

    private String calculationType;

    private BigDecimal distancePercents;

    private String triggerPriceType;
}
