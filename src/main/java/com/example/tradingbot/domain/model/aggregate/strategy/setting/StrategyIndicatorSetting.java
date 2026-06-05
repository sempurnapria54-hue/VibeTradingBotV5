package com.example.tradingbot.domain.model.aggregate.strategy.setting;

import com.example.tradingbot.domain.model.trade.indicator.IndicatorValue;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.time.Duration;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Объявление индикатора в стратегии: { key, type, params }. Хранится
 * JSONB внутри строки контейнера (StrategyMarketPhaseSetting /
 * StrategyDetail), своей строки/таблицы не имеет; адресуется по
 * {@code key} (операнд условия {@code indicatorKey}, «мягкие» ссылки
 * JSON-листьев). Доменный таймфрейм и warmup живут внутри params.
 * Дискриминатор подтипа params — {@code indicatorType} (Jackson
 * EXTERNAL_PROPERTY, в payload params не дублируется). См.
 * docs/models/domain/aggregate/Strategy.md (§StrategyIndicatorSetting),
 * docs/decisions/strategy-condition-authoring-contract.md.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StrategyIndicatorSetting {

    /** Стабильный ключ настройки — по нему ссылается индикаторный операнд условия. */
    private String key;

    /**
     * Тип индикатора; дискриминатор подтипа params. В JSON пишется один
     * раз — как внешний тег params (WRITE_ONLY гасит дубль ключа);
     * при чтении заполняется из того же тега (visible).
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private IndicatorValue.Type indicatorType;

    /** Параметры расчёта (таймфрейм, warmup-override, математические параметры по типу). */
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, visible = true,
            include = JsonTypeInfo.As.EXTERNAL_PROPERTY, property = "indicatorType")
    private IndicatorParams params;

    /** Назначение результата расчёта внутри стратегии. */
    private Destiny destiny;

    /** Срок свежести рассчитанного значения (MarketDataExpirationChecker). */
    private Duration expirationDuration;
}
