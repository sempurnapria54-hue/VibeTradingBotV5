package com.example.marketdata.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** Срез цен наружу. */
@Getter
@Setter
public class MarketTickerApiResponse {

    @Schema(description = "Инструмент, чьи цены сняты")
    private String instrumentInternalId;

    @Schema(description = "Метка времени площадки")
    private Long externalTimestamp;

    @Schema(description = "Наша метка приёма")
    private Long observedTimestamp;

    @Schema(description = "Последняя цена сделки")
    private BigDecimal lastPrice;

    @Schema(description = "Объём за сутки в форме площадки")
    private BigDecimal volume;

    @Schema(description = "Марк-цена; пустота означает, что чтение не дошло — подстановка последней цены запрещена")
    private BigDecimal markPrice;

    @Schema(description = "Цена индекса; пустота с тем же смыслом, что у марк-цены")
    private BigDecimal indexPrice;
}
