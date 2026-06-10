package com.example.tradingbot.domain.model.trade.indicator;

import com.example.tradingbot.domain.model.Auditable;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Готовое значение технического индикатора, рассчитанное
 * {@code IndicatorJob} по закрытым свечам для конкретной
 * настройки-владельца {@code StrategyIndicatorSetting}. Ключуется
 * настройкой-владельцем ({@code strategyIndicatorSettingId}), не шарится и
 * не ключуется по идентичности конфигурации — реестр убран (трек D).
 * Конкретное значение лежит в наследнике по типу индикатора.
 * Persisted-модель рыночных данных, не про бизнес-цикл сделки. См.
 * docs/models/domain/other/IndicatorValue.md,
 * docs/decisions/market-data-result-identity-keying.md.
 */
@Getter
@Setter
@NoArgsConstructor
public abstract class IndicatorValue extends Auditable {

    /** Технический ID значения. */
    private Long id;

    /** Внутренний ID инструмента. */
    private Long instrumentId;

    /** FK на настройку-владельца StrategyIndicatorSetting (owner-ключевание). */
    private Long strategyIndicatorSettingId;

    /** Время свечи, на которой рассчитан индикатор. */
    private OffsetDateTime candleTimestamp;

    /** Тип индикатора (дискриминатор наследника-значения). */
    public abstract Type getType();

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
        OBV,

        /** Kaufman efficiency ratio — мера тренд/шум, скаляр ∈ [0,1]. */
        EFFICIENCY_RATIO
    }
}
