package com.example.tradingbot.domain.command.calc;

/**
 * Политика округления цены по tick size. См.
 * docs/components/models/CalculatedPrice.md (§PriceRoundingPolicy),
 * docs/components/PriceCalculator.md (§Округление).
 */
public enum PriceRoundingPolicy {

    /** Безопасное округление, не ухудшает смысл защитной цены (первый этап). */
    CONSERVATIVE,

    /** Округление для повышения шанса исполнения. */
    AGGRESSIVE,

    /** К ближайшему tick. */
    NEAREST
}
