package com.example.tradingbot.api.model.strategy;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** Параметры алгоритма классификации фазы рынка в API. */
@Getter
@Setter
public class MarketPhaseParamsApiModel {

    @NotBlank
    @Schema(description = "Алгоритм: STRUCTURE_ONLY/INDICATORS_ONLY/STRUCTURE_AND_INDICATORS",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String algorithmType;

    @PositiveOrZero
    @Schema(description = "Минимальный балл признания тренда")
    private BigDecimal minTrendScore;

    @PositiveOrZero
    @Schema(description = "Минимальный балл признания диапазона")
    private BigDecimal minRangeScore;

    @PositiveOrZero
    @Schema(description = "Число баров подтверждения смены фазы")
    private Integer confirmationBars;
}
