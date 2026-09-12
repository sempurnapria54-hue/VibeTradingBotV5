package com.example.strategy.engine.calc;

/**
 * Режим рассчитанной цены: нужно ли отправлять её на биржу. Режим
 * определяет, какой биржевой предел размера применяется при проверке
 * (docs/components/models/CalculatedPrice.md).
 */
public enum PriceMode {

    /** На биржу цена не уходит, но нужна как ориентир размера и логов. */
    MARKET_LIKE,

    /** Конкретная цена уходит на биржу. */
    EXPLICIT,

    /** Цена действию не нужна. */
    NOT_REQUIRED
}
