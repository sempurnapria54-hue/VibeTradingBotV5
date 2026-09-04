package com.example.tradingbot.api.model.strategy;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

/** Параметры Kaufman efficiency ratio в API (indicatorType = EFFICIENCY_RATIO). */
@Getter
@Setter
public class EfficiencyRatioParamsApiModel extends IndicatorParamsApiModel {

    @NotNull
    @Positive
    @Schema(description = "Окно расчёта efficiency ratio, баров", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer period;
}
