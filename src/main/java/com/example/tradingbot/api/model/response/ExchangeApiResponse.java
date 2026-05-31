package com.example.tradingbot.api.model.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/** Представление биржи в API нашего сервиса. Наружу — internalId, не id из БД. */
@Getter
@Setter
public class ExchangeApiResponse extends AuditableApiResponse {

    @Schema(description = "Межсервисный идентификатор биржи")
    private String internalId;

    @Schema(description = "Уникальное имя биржи (например, OKX)")
    private String name;

    @Schema(description = "Базовый URL API биржи")
    private String baseUrl;

    @Schema(description = "Текущий статус подключения/использования биржи")
    private String status;
}
