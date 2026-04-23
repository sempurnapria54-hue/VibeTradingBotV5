package com.example.tradingbot.domain.model.exchange;

import com.example.tradingbot.domain.model.Auditable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Биржа, с которой работает торговый бот.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Exchange extends Auditable {

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
