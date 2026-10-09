package com.example.strategies.api.model.strategy;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/**
 * Правило условия (API). Какие поля правило несёт, задаёт его тип — и
 * задаёт точно: поле, которого тип не читает, отвергает создание
 * (docs/rules/strategy-condition-contract.md §«Правило и операнды»).
 *
 * <p><b>Ключ {@code timeframe} форма узнаёт только ради отказа:</b> поля
 * таймфрейма у правила нет, и объявленный он отвергается валидатором при
 * любом значении — иначе судьба ключа зависела бы от того, как поверхность
 * читает неизвестное поле (.claude/decisions/condition-grammar-executable-only.md
 * §Цена; условие снятия — строгое чтение тела создания).
 */
@Getter
@Setter
public class StrategyConditionRuleApiModel {

    @NotNull
    @Positive
    @Schema(description = "Порядковый номер правила внутри условия; ответ конъюнкции от порядка не зависит",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer level;

    @NotBlank
    @Schema(description = "Тип правила (StrategyConditionRuleType, например MARKET_PHASE_IS)",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String ruleType;

    @PositiveOrZero
    @Schema(description = "Порог в процентах хода от цены входа — только у PROFIT_PERCENTS_REACHED "
            + "и LOSS_PERCENTS_REACHED")
    private BigDecimal percents;

    @Schema(description = "Не объявляется: поля таймфрейма у правила нет, и объявленный он отвергается "
            + "(STRATEGY_CONDITION_FIELD_NOT_READ); таймфрейм — у настройки, на которую ссылается операнд")
    private String timeframe;

    @Schema(description = "Оператор: у сравнения — EQ/NE/GT/GTE/LT/LTE, у пересечения — "
            + "CROSSED_ABOVE/CROSSED_BELOW, у пробоя, структуры и фазы — EQ/NE")
    private String operator;

    @Valid
    @Schema(description = "Левый операнд — у типов, которые операнды читают")
    private StrategyConditionOperandApiModel leftOperand;

    @Valid
    @Schema(description = "Правый операнд — у типов, которые читают оба операнда")
    private StrategyConditionOperandApiModel rightOperand;
}
