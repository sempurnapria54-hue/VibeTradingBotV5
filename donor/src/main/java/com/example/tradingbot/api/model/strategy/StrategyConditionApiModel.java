package com.example.tradingbot.api.model.strategy;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** Условие шага (API): шаг применим, когда истинны все правила. */
@Getter
@Setter
public class StrategyConditionApiModel {

    @Valid
    @NotEmpty
    @Schema(description = "Правила условия; проверяются по level ASC, истинны должны быть все",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private List<StrategyConditionRuleApiModel> rules;
}
