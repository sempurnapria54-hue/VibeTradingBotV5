package com.example.tradingbot.api.model.strategy;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** Объявление расчёта структуры рынка в стратегии (API). */
@Getter
@Setter
public class StrategyMarketStructureSettingApiModel {

    @NotBlank
    @Schema(description = "Стабильный ключ настройки; по нему ссылается операнд условия (structureKey)",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String key;

    @NotBlank
    @Schema(description = "Доменный таймфрейм серии (имя TimeFrame, например ONE_HOUR)",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String timeframe;

    @NotBlank
    @Schema(description = "Тип структуры: RANGE/UPTREND/DOWNTREND/UNKNOWN",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String structureType;

    @Valid
    @NotNull
    @Schema(description = "Параметры расчёта структуры", requiredMode = Schema.RequiredMode.REQUIRED)
    private MarketStructureParamsApiModel params;

    @NotBlank
    @Schema(description = "Назначение: MARKET_PHASE/ENTRY_CONDITION/ACTION_PRICE/PROTECTION/EXIT_CONDITION",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String destiny;

    @NotBlank
    @Schema(description = "Срок свежести структуры, ISO-8601 duration (например PT1H)",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String expirationDuration;
}
