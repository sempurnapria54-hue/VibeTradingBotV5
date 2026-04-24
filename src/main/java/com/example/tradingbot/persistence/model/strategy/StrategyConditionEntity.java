package com.example.tradingbot.persistence.model.strategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class StrategyConditionEntity {

    /**
     * JSONB-список rules. Все rules внутри condition должны быть истинны.
     */
    private List<StrategyConditionRuleEntity> rules;
}
