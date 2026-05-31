package com.example.tradingbot.api.model.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Базовый api-тип audit-полей ответов нашего сервиса. Аналог доменного
 * {@code Auditable} и persistence-{@code AuditableEntity}, но
 * собственный для api-слоя (codestyle: Auditable по слоям). Каждый
 * api-ответ сущности, доменная модель которой наследует
 * {@code Auditable}, наследует этот тип. Время — UTC.
 */
@Getter
@Setter
public abstract class AuditableApiResponse {

    @Schema(description = "Время создания записи в системе (UTC)")
    private OffsetDateTime createdAt;

    @Schema(description = "Пользователь/сервис, создавший запись")
    private String createdBy;

    @Schema(description = "Время последнего изменения в системе (UTC)")
    private OffsetDateTime modifiedAt;

    @Schema(description = "Пользователь/сервис последнего изменения")
    private String modifiedBy;

    @Schema(description = "Время создания записи на стороне биржи (UTC, носитель свежести)")
    private OffsetDateTime externalCreatedAt;

    @Schema(description = "Время последнего обновления на стороне биржи (UTC, носитель свежести)")
    private OffsetDateTime externalModifiedAt;
}
