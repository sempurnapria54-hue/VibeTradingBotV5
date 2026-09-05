package com.example.marketdata.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/** Заказанная идентичность вычисления индикатора наружу. */
@Getter
@Setter
public class IndicatorConfigApiResponse {

    @Schema(description = "Межсервисный идентификатор идентичности: им её называют в чтениях")
    private String internalId;

    @Schema(description = "Тип индикатора")
    private String indicatorType;

    @Schema(description = "Таймфрейм серии")
    private String timeframe;
}
