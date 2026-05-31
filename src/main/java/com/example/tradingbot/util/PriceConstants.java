package com.example.tradingbot.util;

/**
 * Общие константы точности цены/объёма для рыночных данных
 * (свечи OHLCV, цены тикера). Едины для domain-расчётов и
 * precision/scale persistence-колонок.
 */
public final class PriceConstants {

    /** Точность (всего значащих цифр) для денежных/ценовых величин. */
    public static final int PRICE_PRECISION = 36;

    /** Масштаб (знаков после запятой) для денежных/ценовых величин. */
    public static final int PRICE_SCALE = 18;

    private PriceConstants() {
    }
}
