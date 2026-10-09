package com.example.tradingbot.domain.model.aggregate.strategy.condition;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Операнд условия — самоописательный: sourceType плюс ссылка либо
 * значение по источнику. Набор полей задан типом источника точно
 * (docs/rules/strategy-condition-contract.md §«Грамматика объявляет только
 * исполняемое»): индикаторный — ключ настройки и, у многокомпонентного
 * типа, адресный компонент; структурный — ключ настройки структуры;
 * константа — тип значения и значение; у ценового и фазового полей нет.
 *
 * <p>Ссылка на настройку — «мягкая», по ключу из <b>каталога стратегии</b>
 * ({@code Strategy.indicatorSettings} / {@code marketStructureSettings});
 * ключ обязан резолвиться в каталог (create-валидация). Ценовой операнд —
 * последняя цена сделки инструмента в момент оценки, и источника цены он не
 * несёт: источник рыночной цены объявляет только размещение. См.
 * docs/models/domain/aggregate/Strategy.md (§Условия).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StrategyConditionOperand {

    /** Источник значения операнда — задаёт, какие из прочих полей операнд несёт. */
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

    /** Тип литерала (только для sourceType = CONSTANT). */
    private ConstantValueType valueType;

    /** Литерал-значение (только для sourceType = CONSTANT), интерпретируется по valueType. */
    private String value;
}
