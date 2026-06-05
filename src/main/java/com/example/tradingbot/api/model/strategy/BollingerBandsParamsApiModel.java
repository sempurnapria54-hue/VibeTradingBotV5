package com.example.tradingbot.api.model.strategy;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** Параметры полос Боллинджера в API (indicatorType = BOLLINGER_BANDS). */
@Getter
@Setter
public class BollingerBandsParamsApiModel extends IndicatorParamsApiModel {

    @NotNull
    @Positive
    @Schema(description = "Период скользящей средней, баров", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer period;

    @NotNull
    @Positive
    @Schema(description = "Множитель стандартного отклонения границ полос",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal deviationMultiplier;
}
