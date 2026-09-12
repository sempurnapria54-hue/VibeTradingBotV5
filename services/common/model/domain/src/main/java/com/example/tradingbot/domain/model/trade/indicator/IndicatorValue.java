package com.example.tradingbot.domain.model.trade.indicator;

import com.example.tradingbot.domain.model.Auditable;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Готовое значение технического индикатора, рассчитанное по закрытым
 * свечам.
 *
 * <p><b>Ключуется идентичностью вычисления</b> — типом, таймфреймом и
 * каноническими параметрами ({@code indicatorConfigId}), а не настройкой
 * стратегии: настройка живёт в базе другого сервиса, а у фич по всему
 * листингу для детекторов советника владельца нет вовсе. Дом решения —
 * {@code docs/models/domain/other/IndicatorValue.md} §«Ключевание —
 * идентичностью вычисления».
 *
 * <p><b>Срока свежести строка не несёт.</b> Толерантность приносит
 * читатель и применяет к {@code candleTimestamp}: одно значение для
 * одной настройки свежее, для другой уже нет
 * ({@code docs/rules/market-data-freshness.md}).
 *
 * <p><b>В доноре та же идентичность материализована строкой настройки</b>
 * — одна настройка есть одно вычисление, — и колонка там названа по
 * настройке. Это деталь его схемы, а не второй смысл поля.
 */
@Getter
@Setter
@NoArgsConstructor
public abstract class IndicatorValue extends Auditable {

    /** Технический ID значения. */
    private Long id;

    /** Внутренний ID инструмента. */
    private Long instrumentId;

    /** Идентичность вычисления: тип индикатора, таймфрейм, канонические параметры. */
    private Long indicatorConfigId;

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
