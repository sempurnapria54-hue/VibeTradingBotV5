package com.example.tradingbot.rest.model.strategy;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Schema(description = "Одно правило внутри condition стратегии.")
public class StrategyConditionRuleModel {

    @NotNull(message = "Strategy condition rule level is required")
    @Schema(description = "Порядок проверки правила внутри condition.", example = "1")
    private Integer level;

    @NotBlank(message = "Strategy condition ruleType is required")
    @Schema(description = "Тип проверяемого правила.", example = "NO_OPEN_POSITION")
    private String ruleType;

    @Schema(description = "Процентный параметр правила, если данный ruleType его использует.", example = "1.0")
    private BigDecimal percents;
}
