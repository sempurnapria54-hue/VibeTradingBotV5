package com.example.tradingbot.domain.model.aggregate.strategy.condition;

import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyPriceSource;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Операнд условия — самоописательный: sourceType + ссылка/значение по
 * источнику. Ссылка на настройку — «мягкая», по ключу настройки
 * (резолвит приложение): индикаторный операнд — indicatorKey, операнд
 * market-structure — structureKey. Литерал (valueType + value) — только
 * у CONSTANT; у вычисляемых источников значение приходит в рантайме.
 * См. docs/models/domain/aggregate/Strategy.md
 * (§StrategyConditionOperand),
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

    /** Ключ настройки структуры рынка (только для sourceType = MARKET_STRUCTURE). */
    private String structureKey;

    /** Конкретный источник цены (только для sourceType = PRICE). */
    private StrategyPriceSource priceSource;

    /** Тип литерала (только для sourceType = CONSTANT). */
    private ConstantValueType valueType;

    /** Литерал-значение (только для sourceType = CONSTANT), интерпретируется по valueType. */
    private String value;
}
