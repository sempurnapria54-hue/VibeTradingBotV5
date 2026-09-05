package com.example.marketdata.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Готовое значение индикатора наружу.
 *
 * <p><b>Плоская форма со всеми компонентами, а не иерархия по типам.</b>
 * Читатель адресует компонент по имени, и плоская форма отвечает ровно на
 * это; полиморфная потребовала бы от каждого потребителя знать иерархию
 * наших классов ради того же ответа. Незаполненный компонент — не пустота
 * значения, а «этот тип его не несёт».
 */
@Getter
@Setter
public class IndicatorValueApiResponse {

    @Schema(description = "Тип индикатора")
    private String indicatorType;

    @Schema(description = "Идентичность вычисления, из которой получено значение")
    private String indicatorConfigInternalId;

    @Schema(description = "Инструмент, по которому посчитано значение")
    private String instrumentInternalId;

    @Schema(description = "Время свечи, на которой посчитано значение; точка отсчёта свежести")
    private OffsetDateTime candleTimestamp;

    @Schema(description = "Average True Range")
    private BigDecimal atr;

    @Schema(description = "Экспоненциальная скользящая средняя")
    private BigDecimal ema;

    @Schema(description = "Relative Strength Index")
    private BigDecimal rsi;

    @Schema(description = "Линия MACD")
    private BigDecimal macdLine;

    @Schema(description = "Сигнальная линия MACD")
    private BigDecimal signalLine;

    @Schema(description = "Гистограмма MACD")
    private BigDecimal histogram;

    @Schema(description = "Верхняя полоса Боллинджера")
    private BigDecimal upperBand;

    @Schema(description = "Средняя полоса Боллинджера")
    private BigDecimal middleBand;

    @Schema(description = "Нижняя полоса Боллинджера")
    private BigDecimal lowerBand;

    @Schema(description = "Ширина полос Боллинджера")
    private BigDecimal bandwidth;

    @Schema(description = "Положение цены в полосах Боллинджера")
    private BigDecimal percentB;

    @Schema(description = "Стохастик K")
    private BigDecimal k;

    @Schema(description = "Стохастик D")
    private BigDecimal d;

    @Schema(description = "On-Balance Volume")
    private BigDecimal obv;

    @Schema(description = "Kaufman efficiency ratio")
    private BigDecimal efficiencyRatio;
}
