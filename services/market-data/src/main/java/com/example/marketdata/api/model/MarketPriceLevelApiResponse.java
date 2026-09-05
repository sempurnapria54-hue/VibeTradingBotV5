package com.example.marketdata.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

/** Ценовой уровень структуры рынка наружу. */
@Getter
@Setter
public class MarketPriceLevelApiResponse {

    @Schema(description = "Тип уровня")
    private String type;

    @Schema(description = "Цена уровня")
    private BigDecimal price;

    @Schema(description = "Свеча, на которой уровень найден")
    private OffsetDateTime detectedAt;

    @Schema(description = "Свеча, на которой уровень подтверждён")
    private OffsetDateTime confirmedAt;
}
