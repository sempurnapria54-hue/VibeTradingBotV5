package com.example.tradingbot.rest.model.strategy;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Schema(description = "Общее condition шага стратегии. Все rules внутри condition должны быть истинны.")
public class StrategyConditionModel {

    @Valid
    @NotEmpty(message = "Strategy condition rules must not be empty")
    @Schema(description = "Набор правил, которые проверяются по `level ASC`.", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<StrategyConditionRuleModel> rules;
}
