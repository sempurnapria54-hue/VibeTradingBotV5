package com.example.tradingbot.domain.model.exchange;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Exchange {

    /**
     * Внутренний идентификатор биржи.
     */
    private Long id;
    /**
     * Межсервисный идентификатор биржи.
     */
    private String internalId;
    /**
     * Уникальное имя биржи (например OKX).
     */
    private String name;
    /**
     * Базовый URL для API биржи.
     */
    private String baseUrl;
    /**
     * Текущий статус подключения/использования биржи.
     */
    private Status status;

    public enum Status {
        CREATED,
        PENDING,
        ACTIVE,
        CLOSED,
        ERROR
    }
}
