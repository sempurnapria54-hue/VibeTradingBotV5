package com.example.tradingcore.integration.internal.api.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Готовое значение индикатора от владельца рыночных данных.
 *
 * <p><b>Форма плоская, а не иерархия по типам.</b> Так её отдаёт владелец:
 * читатель адресует компонент по имени, и незаполненный компонент означает
 * «этот тип его не несёт», а не пустое значение. Подтип доменной модели
 * восстанавливает маппер по названному типу.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class IndicatorValueResponse {

    /** Тип индикатора — дискриминатор подтипа доменного значения. */
    private String indicatorType;

    /** Время свечи, на которой посчитано значение; точка отсчёта свежести. */
    private OffsetDateTime candleTimestamp;

    /** Average True Range. */
    private BigDecimal atr;

    /** Экспоненциальная скользящая средняя. */
    private BigDecimal ema;

    /** Relative Strength Index. */
    private BigDecimal rsi;

    /** Линия MACD. */
    private BigDecimal macdLine;

    /** Сигнальная линия MACD. */
    private BigDecimal signalLine;

    /** Гистограмма MACD. */
    private BigDecimal histogram;

    /** Верхняя полоса Боллинджера. */
    private BigDecimal upperBand;

    /** Средняя полоса Боллинджера. */
    private BigDecimal middleBand;

    /** Нижняя полоса Боллинджера. */
    private BigDecimal lowerBand;

    /** Ширина полос Боллинджера. */
    private BigDecimal bandwidth;

    /** Положение цены в полосах Боллинджера. */
    private BigDecimal percentB;

    /** Стохастик K. */
    private BigDecimal k;

    /** Стохастик D. */
    private BigDecimal d;

    /** On-Balance Volume. */
    private BigDecimal obv;

    /** Kaufman efficiency ratio. */
    private BigDecimal efficiencyRatio;
}
