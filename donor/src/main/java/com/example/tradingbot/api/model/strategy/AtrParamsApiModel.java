package com.example.tradingbot.api.model.strategy;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

/** Параметры индикатора ATR в API (indicatorType = ATR). */
@Getter
@Setter
public class AtrParamsApiModel extends IndicatorParamsApiModel {

    @NotNull
    @Positive
    @Schema(description = "Период усреднения true range, баров", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer period;
}
