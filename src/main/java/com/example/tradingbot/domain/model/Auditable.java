package com.example.tradingbot.domain.model;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Общие audit-поля доменных сущностей.
 */
@Getter
@Setter
public class Auditable {

    /**
     * Дата и время создания записи в системе.
     */
    private OffsetDateTime createdAt;

    /**
     * Пользователь или сервис, создавший запись.
     */
    private String createdBy;

    /**
     * Дата и время последнего изменения записи в системе.
     */
    private OffsetDateTime modifiedAt;

    /**
     * Пользователь или сервис, выполнивший последнее изменение.
     */
    private String modifiedBy;

    /**
     * Дата и время создания записи на стороне биржи.
     */
    private OffsetDateTime externalCreatedAt;

    /**
     * Дата и время последнего обновления записи на стороне биржи.
     */
    private OffsetDateTime externalModifiedAt;
}
