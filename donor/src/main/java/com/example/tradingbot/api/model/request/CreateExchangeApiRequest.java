package com.example.tradingbot.api.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** Запрос на заведение биржи. Статус проставляется системой (CREATED). */
@Getter
@Setter
public class CreateExchangeApiRequest {

    @NotBlank
    @Schema(description = "Межсервисный идентификатор биржи", requiredMode = Schema.RequiredMode.REQUIRED)
    private String internalId;

    @NotBlank
    @Schema(description = "Уникальное имя биржи (например, OKX)", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @NotBlank
    @Schema(description = "Базовый URL API биржи", requiredMode = Schema.RequiredMode.REQUIRED)
    private String baseUrl;
}
