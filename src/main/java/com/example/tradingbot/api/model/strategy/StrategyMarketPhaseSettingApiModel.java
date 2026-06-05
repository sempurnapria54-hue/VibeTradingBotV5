package com.example.tradingbot.api.model.strategy;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Настройка расчёта фазы рынка (API): одна на стратегию, фаза нужна
 * до выбора детали.
 */
@Getter
@Setter
public class StrategyMarketPhaseSettingApiModel {

    @NotBlank
    @Schema(description = "Доменный таймфрейм классификации фазы (имя TimeFrame)",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String timeframe;

    @Valid
    @NotNull
    @Schema(description = "Параметры алгоритма классификации", requiredMode = Schema.RequiredMode.REQUIRED)
    private MarketPhaseParamsApiModel params;

    @Valid
    @Schema(description = "Индикаторы для расчёта фазы (destiny = MARKET_PHASE)")
    private List<StrategyIndicatorSettingApiModel> indicatorSettings;

    @Valid
    @Schema(description = "Структуры рынка для расчёта фазы (destiny = MARKET_PHASE)")
    private List<StrategyMarketStructureSettingApiModel> marketStructureSettings;

    @NotBlank
    @Schema(description = "Срок свежести последней рассчитанной фазы, ISO-8601 duration",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String expirationDuration;
}
