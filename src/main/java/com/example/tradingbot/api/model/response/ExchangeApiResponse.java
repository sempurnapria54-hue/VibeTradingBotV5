package com.example.tradingbot.api.model.response;

import lombok.Getter;
import lombok.Setter;

/** Представление биржи в API нашего сервиса. */
@Getter
@Setter
public class ExchangeApiResponse {

    private Long id;

    private String internalId;

    private String name;

    private String baseUrl;

    private String status;
}
