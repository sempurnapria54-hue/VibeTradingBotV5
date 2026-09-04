package com.example.tradingbot.api.model.strategy;

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
 * Правило условия (API). Доменные правила — плоские (ruleType + простые
 * поля); сравнивающие — operator + операнды.
 */
@Getter
@Setter
public class StrategyConditionRuleApiModel {

    @NotNull
    @Positive
    @Schema(description = "Порядок проверки внутри условия (ASC)", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer level;

    @NotBlank
    @Schema(description = "Тип правила (StrategyConditionRuleType, например MARKET_PHASE_IS)",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String ruleType;

    @PositiveOrZero
    @Schema(description = "Простое процентное поле плоских правил (PROFIT_PERCENTS_REACHED и т. п.)")
    private BigDecimal percents;

    @Schema(description = "Таймфрейм плоских правил про серию свечей (CANDLE_CLOSED), имя TimeFrame")
    private String timeframe;

    @Schema(description = "Оператор сравнивающего правила (EQ/GT/CROSSED_ABOVE/...)")
    private String operator;

    @Valid
    @Schema(description = "Левый операнд сравнивающего правила")
    private StrategyConditionOperandApiModel leftOperand;

    @Valid
    @Schema(description = "Правый операнд сравнивающего правила")
    private StrategyConditionOperandApiModel rightOperand;
}
