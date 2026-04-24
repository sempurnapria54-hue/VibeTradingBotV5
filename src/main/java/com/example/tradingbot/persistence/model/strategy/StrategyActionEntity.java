package com.example.tradingbot.persistence.model.strategy;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "actionKind")
@JsonSubTypes({
        @JsonSubTypes.Type(value = StrategyOrderActionEntity.class, name = "ORDER"),
        @JsonSubTypes.Type(value = StrategyAlgoOrderActionEntity.class, name = "ALGO_ORDER"),
        @JsonSubTypes.Type(value = StrategyPositionActionEntity.class, name = "POSITION")
})
public interface StrategyActionEntity {

    Long getId();

    void setId(Long id);
}
