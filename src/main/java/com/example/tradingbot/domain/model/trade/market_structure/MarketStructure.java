package com.example.tradingbot.domain.model.trade.market_structure;

/**
 * Структура рынка — готовый результат расчёта уровней/диапазонов/тренда,
 * рассчитываемый MarketStructureJob по настройке
 * {@code StrategyMarketStructureSetting}. Полная модель (с уровнями
 * MarketPriceLevel) дозревает на шаге расчёта рыночных данных; на
 * шаге 2 класс несёт только {@link Type} — дискриминатор типа структуры
 * в настройках стратегии. См.
 * docs/models/domain/other/MarketStructure.md.
 */
public class MarketStructure {

    /** Тип структуры рынка. */
    public enum Type {

        /** Диапазон (боковик с границами). */
        RANGE,

        /** Восходящий тренд. */
        UPTREND,

        /** Нисходящий тренд. */
        DOWNTREND,

        /** Структура не определена. */
        UNKNOWN
    }
}
