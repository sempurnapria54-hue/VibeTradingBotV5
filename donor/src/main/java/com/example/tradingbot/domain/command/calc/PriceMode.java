package com.example.tradingbot.domain.command.calc;

/**
 * Режим рассчитанной цены: нужно ли отправлять её на биржу. См.
 * docs/components/models/CalculatedPrice.md (§PriceMode).
 */
public enum PriceMode {

    /** Цену на биржу не отправляем, но reference price нужна для размера/риска/логов. */
    MARKET_LIKE,

    /** Конкретная цена должна быть отправлена на биржу. */
    EXPLICIT,

    /** Цена для команды не нужна. */
    NOT_REQUIRED
}
