package com.example.strategies.api.model.strategy;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** Условие шага (API): шаг применим, когда истинны все правила. */
@Getter
@Setter
public class StrategyConditionApiModel {

    @Valid
    @Schema(description = "Правила условия; проверяются по level ASC, истинны должны быть все. "
            + "Перечень непуст — непустоту проверяет валидатор с кодом STRATEGY_CONDITION_EMPTY, "
            + "а не аннотация: код с аннотацией был бы недостижим",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private List<StrategyConditionRuleApiModel> rules;
}
