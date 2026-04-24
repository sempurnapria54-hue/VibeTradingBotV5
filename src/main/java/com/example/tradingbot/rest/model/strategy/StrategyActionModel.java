package com.example.tradingbot.rest.model.strategy;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "actionKind")
@JsonSubTypes({
        @JsonSubTypes.Type(value = StrategyOrderActionModel.class, name = "ORDER"),
        @JsonSubTypes.Type(value = StrategyAlgoOrderActionModel.class, name = "ALGO_ORDER"),
        @JsonSubTypes.Type(value = StrategyPositionActionModel.class, name = "POSITION")
})
@Schema(
        description = "Базовый action стратегии. Конкретный подтип определяется JSON discriminator `actionKind`.",
        discriminatorProperty = "actionKind",
        oneOf = {
                StrategyOrderActionModel.class,
                StrategyAlgoOrderActionModel.class,
                StrategyPositionActionModel.class
        },
        discriminatorMapping = {
                @DiscriminatorMapping(value = "ORDER", schema = StrategyOrderActionModel.class),
                @DiscriminatorMapping(value = "ALGO_ORDER", schema = StrategyAlgoOrderActionModel.class),
                @DiscriminatorMapping(value = "POSITION", schema = StrategyPositionActionModel.class)
        }
)
public abstract class StrategyActionModel {
}
