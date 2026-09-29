package com.example.marketdata.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Цены момента инструмента наружу.
 *
 * <p><b>Своя форма, а не доменная модель на проводе.</b> У доменной модели
 * есть вычисляемые ответы, и её правка становилась бы правкой контракта
 * (.claude/rules/codestyle.md §Маппинг). Числовой ключ инструмента наружу
 * не едет — его место занимает идентичность (§«Идентичность наружу»).
 */
@Getter
@Setter
public class MarketPriceDataApiResponse {

    @Schema(description = "Инструмент, чьи цены сняты")
    private String instrumentInternalId;

    @Schema(description = "Тип инструмента в словаре площадки")
    private String externalInstrumentType;

    @Schema(description = "Инструмент в словаре площадки")
    private String externalInstrumentId;

    @Schema(description = "Последняя цена сделки")
    private BigDecimal externalLastPrice;

    @Schema(description = "Лучшая цена продажи")
    private BigDecimal externalAskPrice;

    @Schema(description = "Лучшая цена покупки")
    private BigDecimal externalBidPrice;

    @Schema(description = "Объём на лучшей цене продажи")
    private BigDecimal externalAskSize;

    @Schema(description = "Объём на лучшей цене покупки")
    private BigDecimal externalBidSize;

    @Schema(description = "Момент цен по часам площадки, UTC")
    private OffsetDateTime externalTimestamp;
}
