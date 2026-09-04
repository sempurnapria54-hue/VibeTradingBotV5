package com.example.tradingbot.api.model.strategy;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

/** Параметры индикатора EMA в API (indicatorType = EMA). */
@Getter
@Setter
public class EmaParamsApiModel extends IndicatorParamsApiModel {

    @NotNull
    @Positive
    @Schema(description = "Период сглаживания, баров", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer period;
}
