package com.example.tradingbot.domain.model.aggregate.strategy.setting;

import com.example.tradingbot.domain.model.trade.indicator.IndicatorValue;
import java.time.Duration;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Объявление индикатора в стратегии: { key, type, params }. Собственная
 * реляционная строка strategy-scope (таблица strategy_indicator_settings,
 * UNIQUE(strategy_id, key)); адресуется по {@code key} (операнд условия
 * {@code indicatorKey}, «мягкие» ссылки JSON-листьев, ER/ATR-входы
 * структуры). Результат расчёта {@code IndicatorValue} на неё НЕ
 * ссылается: он ключуется идентичностью вычисления
 * (docs/models/domain/other/IndicatorValue.md). Дискриминатор строки —
 * {@code indicatorType} (колонка-владелец строки); сам params — JSONB на
 * строке без дублирования тега. Доменный таймфрейм и warmup живут внутри
 * params. См. docs/models/domain/aggregate/Strategy.md
 * (§StrategyIndicatorSetting), docs/decisions/strategy-tree-persistence.md.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StrategyIndicatorSetting {

    /** Технический ID настройки (strategy-scope-строка; цель FK результата расчёта). */
    private Long id;

    /** Стабильный ключ настройки — по нему ссылается индикаторный операнд условия. */
    private String key;

    /** Тип индикатора; дискриминатор подтипа params (колонка-владелец строки). */
    private IndicatorValue.Type indicatorType;

    /** Параметры расчёта (таймфрейм, warmup-override, математические параметры по типу). */
    private IndicatorParams params;

    /** Назначение результата расчёта внутри стратегии. */
    private Destiny destiny;

    /** Срок свежести рассчитанного значения (MarketDataExpirationChecker). */
    private Duration expirationDuration;
}
