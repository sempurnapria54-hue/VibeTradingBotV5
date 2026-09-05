package com.example.marketdata.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** Срез книги заявок наружу. */
@Getter
@Setter
public class MarketOrderBookApiResponse {

    @Schema(description = "Инструмент, чья книга снята")
    private String instrumentInternalId;

    @Schema(description = "Метка времени площадки: момент, к которому относится книга")
    private Long externalTimestamp;

    @Schema(description = "Наша метка приёма: разность с биржевой и есть задержка")
    private Long observedTimestamp;

    @Schema(description = "Уровни покупки, от лучшего к худшему")
    private List<OrderBookLevelApiResponse> bids;

    @Schema(description = "Уровни продажи, от лучшего к худшему")
    private List<OrderBookLevelApiResponse> asks;
}
