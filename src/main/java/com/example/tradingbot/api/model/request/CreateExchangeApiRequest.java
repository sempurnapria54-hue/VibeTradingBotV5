package com.example.tradingbot.api.model.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** Запрос на заведение биржи. Статус проставляется системой (CREATED). */
@Getter
@Setter
public class CreateExchangeApiRequest {

    @NotBlank
    private String internalId;

    @NotBlank
    private String name;

    @NotBlank
    private String baseUrl;
}
