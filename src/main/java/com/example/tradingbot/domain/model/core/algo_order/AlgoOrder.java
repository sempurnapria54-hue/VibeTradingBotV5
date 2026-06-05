package com.example.tradingbot.domain.model.core.algo_order;

/**
 * Standalone algo-order — самостоятельная условная заявка на бирже
 * (SL/TP/OCO/trailing/partial). Полная модель (condition-дерево,
 * external snapshots) дозревает на шаге 4 Фазы 1; на шаге 2 класс несёт
 * только {@link ConditionType} и {@link TriggerPriceType} — на них
 * ссылаются настройки действий стратегии. См.
 * docs/models/domain/core/AlgoOrder.md, docs/lifecycles/AlgoOrder.md.
 */
public class AlgoOrder {

    /** Тип условия срабатывания algo-order. */
    public enum ConditionType {

        /** Стоп-лосс на полное закрытие. */
        STOP_LOSS,

        /** Тейк-профит на полное закрытие. */
        TAKE_PROFIT,

        /** OCO: стоп-лосс + тейк-профит на полное закрытие. */
        OCO_FULL,

        /** Трейлинг-стоп с callback в процентах. */
        TRAILING_PERCENTS,

        /** Трейлинг-стоп с callback в абсолютном значении. */
        TRAILING_VALUE,

        /** Частичный тейк-профит (reduce-only доля позиции). */
        PARTIAL_TAKE_PROFIT,

        /** Частичный стоп-лосс (reduce-only доля позиции). */
        PARTIAL_STOP_LOSS
    }

    /** Внутренний тип trigger-цены биржи. */
    public enum TriggerPriceType {

        /** Последняя цена сделки (last). */
        LAST,

        /** Индексная цена (index). */
        INDEX,

        /** Марк-цена (mark). */
        MARK
    }
}
