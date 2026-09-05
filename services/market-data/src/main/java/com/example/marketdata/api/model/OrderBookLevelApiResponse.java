package com.example.marketdata.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** Уровень книги заявок наружу. */
@Getter
@Setter
public class OrderBookLevelApiResponse {

    @Schema(description = "Цена уровня")
    private BigDecimal price;

    @Schema(description = "Объём на уровне")
    private BigDecimal size;

    @Schema(description = "Число заявок на уровне")
    private Integer orderCount;
}
