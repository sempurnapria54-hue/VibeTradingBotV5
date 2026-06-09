package com.example.tradingbot.api.model.strategy;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

/**
 * Клауза классификации фазы (API): условие → фаза. Набор — first-match
 * по level; первая истинная клауза задаёт MarketPhase.Type.
 */
@Getter
@Setter
public class StrategyMarketPhaseRuleApiModel {

    @NotNull
    @Positive
    @Schema(description = "Порядок проверки клаузы (ASC); first-match", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer level;

    @NotBlank
    @Schema(description = "Назначаемая фаза: BULL_TREND/BEAR_TREND/RANGE/UNKNOWN",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String type;

    @Valid
    @NotNull
    @Schema(description = "Условие клаузы (контекст классификации фазы)",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private StrategyConditionApiModel condition;
}
