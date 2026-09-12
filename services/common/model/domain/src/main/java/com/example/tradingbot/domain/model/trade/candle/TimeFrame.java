package com.example.tradingbot.domain.model.trade.candle;

import lombok.Getter;

/**
 * Доменный enum таймфреймов свечей/индикаторов. Первоисточник —
 * свечная подсистема (CandleGroup структурно определяется
 * таймфреймом). Строк биржи enum не хранит; маппинг домен ↔ строка
 * OKX живёт в TimeFrameMapper (docs/models/mapping/TimeFrame.md).
 * Длительность бара несёт сам enum — она нужна доменной проверке
 * плотности ряда (density-инвариант CandleGroup).
 */
@Getter
public enum TimeFrame {

    /**
     * Бар 1 секунда. Свечных групп на нём не заводится — таймфрейм несёт
     * координату курса пересчёта чужой валюты: лестница огрубления
     * пробует секундную свечу пары котировки раньше минутной
     * (docs/components/RefreshBillsExecutor.md §«Лестница огрубления
     * разрешения»).
     */
    ONE_SECOND(1_000L),

    /** Бар 1 минута. */
    ONE_MINUTE(60_000L),

    /** Бар 3 минуты. */
    THREE_MINUTES(180_000L),

    /** Бар 5 минут. */
    FIVE_MINUTES(300_000L),

    /** Бар 15 минут. */
    FIFTEEN_MINUTES(900_000L),

    /** Бар 1 час. */
    ONE_HOUR(3_600_000L),

    /** Бар 2 часа. */
    TWO_HOURS(7_200_000L),

    /** Бар 4 часа. */
    FOUR_HOURS(14_400_000L),

    /** Бар 1 день (UTC-выровненный). */
    ONE_DAY(86_400_000L);

    /** Длительность одного бара таймфрейма в миллисекундах. */
    private final Long durationMillis;

    TimeFrame(Long durationMillis) {
        this.durationMillis = durationMillis;
    }
}
