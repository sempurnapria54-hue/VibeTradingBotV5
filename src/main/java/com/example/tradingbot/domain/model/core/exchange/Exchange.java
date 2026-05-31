package com.example.tradingbot.domain.model.core.exchange;

import com.example.tradingbot.domain.model.Auditable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Биржа, с которой работает бот: идентичность, точка подключения и
 * статус использования. Инструменты ссылаются на биржу через
 * {@code Instrument.exchangeId} (docs/models/domain/core/Exchange.md).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Exchange extends Auditable {

    /** Внутренний идентификатор биржи. */
    private Long id;

    /** Межсервисный идентификатор биржи. */
    private String internalId;

    /** Уникальное имя биржи (например, OKX). */
    private String name;

    /** Базовый URL API биржи. */
    private String baseUrl;

    /** Текущий статус подключения/использования биржи. */
    private Status status;

    /** Статус подключения/использования биржи. */
    public enum Status {

        /** Биржа заведена, подключение ещё не настраивалось. */
        CREATED,

        /** Идёт настройка/проверка подключения. */
        PENDING,

        /** Биржа активна, доступна для работы. */
        ACTIVE,

        /** Биржа выведена из использования. */
        CLOSED,

        /** Ошибка подключения/использования. */
        ERROR
    }
}
