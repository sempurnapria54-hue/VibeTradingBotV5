package com.example.marketdata.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/** Единица сбора свечей наружу. */
@Getter
@Setter
public class CandleGroupApiResponse {

    @Schema(description = "Межсервисный идентификатор единицы сбора")
    private String internalId;

    @Schema(description = "Таймфрейм серии")
    private String timeframe;

    @Schema(description = "Статус жизненного цикла загрузки")
    private String status;

    @Schema(description = "Горизонт бэкфилла: заказанная нижняя граница истории, UTC мс")
    private Long plannedFirstUtcMillis;

    @Schema(description = "Открытие первой фактически загруженной свечи, UTC мс")
    private Long actualFirstUtcMillis;

    @Schema(description = "Открытие последней фактически загруженной свечи, UTC мс")
    private Long actualLastUtcMillis;

    @Schema(description = "Число свечей в ряду группы")
    private Long count;
}
