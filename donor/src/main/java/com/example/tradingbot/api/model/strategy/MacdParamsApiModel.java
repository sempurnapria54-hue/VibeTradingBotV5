package com.example.tradingbot.api.model.strategy;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

/** Параметры индикатора MACD в API (indicatorType = MACD). */
@Getter
@Setter
public class MacdParamsApiModel extends IndicatorParamsApiModel {

    @NotNull
    @Positive
    @Schema(description = "Период быстрой EMA, баров", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer fastPeriod;

    @NotNull
    @Positive
    @Schema(description = "Период медленной EMA, баров", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer slowPeriod;

    @NotNull
    @Positive
    @Schema(description = "Период сигнальной линии, баров", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer signalPeriod;
}
