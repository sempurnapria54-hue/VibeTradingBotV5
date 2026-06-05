package com.example.tradingbot.api.model.strategy;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

/** Параметры стохастического осциллятора в API (indicatorType = STOCHASTIC). */
@Getter
@Setter
public class StochasticParamsApiModel extends IndicatorParamsApiModel {

    @NotNull
    @Positive
    @Schema(description = "Период линии %K, баров", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer kPeriod;

    @NotNull
    @Positive
    @Schema(description = "Период линии %D, баров", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer dPeriod;

    @NotNull
    @Positive
    @Schema(description = "Период дополнительного сглаживания, баров", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer smoothPeriod;
}
