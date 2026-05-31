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

    ONE_MINUTE(60_000L),
    THREE_MINUTES(180_000L),
    FIVE_MINUTES(300_000L),
    FIFTEEN_MINUTES(900_000L),
    ONE_HOUR(3_600_000L),
    TWO_HOURS(7_200_000L),
    FOUR_HOURS(14_400_000L),
    ONE_DAY(86_400_000L);

    /** Длительность одного бара таймфрейма в миллисекундах. */
    private final long durationMillis;

    TimeFrame(long durationMillis) {
        this.durationMillis = durationMillis;
    }
}
