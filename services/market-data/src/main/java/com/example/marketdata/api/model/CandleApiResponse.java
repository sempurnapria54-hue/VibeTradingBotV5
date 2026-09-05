package com.example.marketdata.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** Закрытая свеча наружу. */
@Getter
@Setter
public class CandleApiResponse {

    @Schema(description = "Время открытия свечи, UTC мс")
    private Long openTimestamp;

    @Schema(description = "Цена открытия")
    private BigDecimal open;

    @Schema(description = "Максимум за интервал")
    private BigDecimal high;

    @Schema(description = "Минимум за интервал")
    private BigDecimal low;

    @Schema(description = "Цена закрытия")
    private BigDecimal close;

    @Schema(description = "Объём торгов за интервал")
    private BigDecimal volume;
}
