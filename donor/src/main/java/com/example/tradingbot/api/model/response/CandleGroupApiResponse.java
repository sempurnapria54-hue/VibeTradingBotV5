package com.example.tradingbot.api.model.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * Представление группы свечей в API нашего сервиса. Наружу — internalId
 * группы и instrumentInternalId инструмента, не id/instrument_id из БД.
 */
@Getter
@Setter
public class CandleGroupApiResponse extends AuditableApiResponse {

    @Schema(description = "Межсервисный идентификатор группы свечей")
    private String internalId;

    @Schema(description = "Межсервисный идентификатор инструмента-владельца")
    private String instrumentInternalId;

    @Schema(description = "Канонический таймфрейм группы")
    private String timeframe;

    @Schema(description = "Таймфрейм в формате биржи (сырой, например 1H)")
    private String externalTimeframe;

    @Schema(description = "Статус жизненного цикла загрузки свечей")
    private String status;

    @Schema(description = "Время открытия первой фактически загруженной свечи (UTC мс)")
    private Long actualFirstUtcMillis;

    @Schema(description = "Время открытия последней фактически загруженной свечи (UTC мс)")
    private Long actualLastUtcMillis;

    @Schema(description = "Поддерживаемое число свечей в группе")
    private Long count;
}
