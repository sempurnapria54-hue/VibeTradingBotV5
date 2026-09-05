package com.example.tradingbot.domain.model.aggregate.strategy.condition;

import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyPriceSource;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Операнд условия — самоописательный: sourceType + ссылка/значение по
 * источнику. Ссылка на настройку — «мягкая», по ключу настройки из
 * **каталога стратегии** ({@code Strategy.indicatorSettings} /
 * {@code marketStructureSettings}): индикаторный операнд — indicatorKey,
 * операнд market-structure — structureKey; резолвит приложение в пределах
 * стратегии. Ссылаться на необъявленную настройку нельзя — ключ обязан
 * резолвиться в каталог (create-валидация, strategy-scope ref-resolution).
 * Операнд-CONSTANT (литерал valueType + value) и PRICE-операнд (priceSource)
 * — **не** ссылки на настройку, ограничение их не касается; у вычисляемых
 * источников значение приходит в рантайме. См.
 * docs/models/domain/aggregate/Strategy.md (§StrategyConditionOperand),
 * docs/decisions/strategy-condition-authoring-contract.md.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StrategyConditionOperand {

    /** Источник значения операнда. */
    private StrategyConditionSourceType sourceType;

    /** Ключ настройки индикатора (только для sourceType = INDICATOR). */
    private String indicatorKey;

    /**
     * Адресуемый компонент многокомпонентного индикатора (только для
     * INDICATOR): для MACD/Stochastic/Bollinger обязателен (автор
     * выбирает осмысленную часть), для одно-компонентных не задаётся.
     */
    private IndicatorComponent indicatorComponent;

    /** Ключ настройки структуры рынка (только для sourceType = MARKET_STRUCTURE). */
    private String structureKey;

    /** Конкретный источник цены (только для sourceType = PRICE). */
    private StrategyPriceSource priceSource;

    /** Тип литерала (только для sourceType = CONSTANT). */
    private ConstantValueType valueType;

    /** Литерал-значение (только для sourceType = CONSTANT), интерпретируется по valueType. */
    private String value;
}
