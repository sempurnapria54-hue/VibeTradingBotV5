package com.example.tradingbot.domain.model;

import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Базовый класс audit-полей доменных сущностей. Системные поля
 * (createdAt/modifiedAt/createdBy/modifiedBy) проставляет
 * persistence-слой (JPA auditing); биржевые
 * (externalCreatedAt/externalModifiedAt) — код, производящий данные,
 * из таймстемпов источника. Время — везде UTC
 * (docs/rules/time-utc.md, docs/models/domain/other/Auditable.md).
 */
@Getter
@Setter
public abstract class Auditable {

    /** Время создания записи в системе. */
    private OffsetDateTime createdAt;

    /** Пользователь/сервис, создавший запись. */
    private String createdBy;

    /** Время последнего изменения в системе. */
    private OffsetDateTime modifiedAt;

    /** Пользователь/сервис последнего изменения. */
    private String modifiedBy;

    /** Время создания записи на стороне биржи (носитель свежести). */
    private OffsetDateTime externalCreatedAt;

    /** Время последнего обновления на стороне биржи (носитель свежести). */
    private OffsetDateTime externalModifiedAt;
}
