package com.example.tradingbot.api.model.strategy;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * Объявление индикатора стратегии в API: { key, indicatorType, params }.
 * Подтип params определяется полем indicatorType (EXTERNAL_PROPERTY).
 */
@Getter
@Setter
public class StrategyIndicatorSettingApiModel {

    @NotBlank
    @Schema(description = "Стабильный ключ настройки; по нему ссылается операнд условия (indicatorKey)",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String key;

    @NotBlank
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Schema(description = "Тип индикатора: ATR/EMA/RSI/MACD/STOCHASTIC/BOLLINGER_BANDS/OBV; "
            + "в ответе пишется один раз — как внешний тег params",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String indicatorType;

    @Valid
    @NotNull
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, visible = true,
            include = JsonTypeInfo.As.EXTERNAL_PROPERTY, property = "indicatorType")
    @Schema(description = "Параметры расчёта по типу индикатора (таймфрейм, warmup-override, периоды)",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private IndicatorParamsApiModel params;

    @NotBlank
    @Schema(description = "Назначение: MARKET_PHASE/ENTRY_CONDITION/ACTION_PRICE/PROTECTION/EXIT_CONDITION",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String destiny;

    @NotBlank
    @Schema(description = "Срок свежести значения, ISO-8601 duration (например PT15M)",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String expirationDuration;
}
