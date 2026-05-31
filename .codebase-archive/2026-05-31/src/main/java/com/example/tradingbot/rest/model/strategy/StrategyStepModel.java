package com.example.tradingbot.rest.model.strategy;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Schema(description = "Один шаг стратегии: общее condition и пакет действий, которые нужно выполнить целиком.")
public class StrategyStepModel {

    @NotBlank(message = "Strategy stepType is required")
    @Schema(description = "Бизнес-смысл шага стратегии.", example = "ENTRY")
    private String stepType;

    @Valid
    @NotNull(message = "Strategy step condition is required")
    @Schema(description = "Общее условие применимости шага.")
    private StrategyConditionModel condition;

    @Valid
    @NotEmpty(message = "Strategy step actions must not be empty")
    @Schema(description = "Набор действий, который выполняется, если condition шага истинно.", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<StrategyActionModel> actions;
}
