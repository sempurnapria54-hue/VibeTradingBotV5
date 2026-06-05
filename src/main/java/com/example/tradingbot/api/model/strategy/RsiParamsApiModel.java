package com.example.tradingbot.api.model.strategy;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

/** Параметры индикатора RSI в API (indicatorType = RSI). */
@Getter
@Setter
public class RsiParamsApiModel extends IndicatorParamsApiModel {

    @NotNull
    @Positive
    @Schema(description = "Период расчёта RSI, баров", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer period;
}
