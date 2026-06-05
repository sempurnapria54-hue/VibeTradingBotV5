package com.example.tradingbot.domain.model.trade.indicator;

/**
 * Готовое значение технического индикатора, рассчитываемое
 * IndicatorJob по закрытым свечам и настройке
 * {@code StrategyIndicatorSetting}. Полная модель (abstract-база +
 * наследники по типам) дозревает на шаге 3 Фазы 1 (индикаторы); на
 * шаге 2 класс несёт только {@link Type} — дискриминатор типа
 * индикатора в настройках стратегии. См.
 * docs/models/domain/other/IndicatorValue.md.
 */
public class IndicatorValue {

    /** Тип технического индикатора. */
    public enum Type {

        /** Average True Range (волатильность). */
        ATR,

        /** Экспоненциальная скользящая средняя. */
        EMA,

        /** Relative Strength Index (осциллятор). */
        RSI,

        /** Moving Average Convergence Divergence. */
        MACD,

        /** Стохастический осциллятор. */
        STOCHASTIC,

        /** Полосы Боллинджера. */
        BOLLINGER_BANDS,

        /** On-Balance Volume (объёмный индикатор). */
        OBV
    }
}
