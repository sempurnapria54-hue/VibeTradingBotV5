package com.example.tradingbot.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Базовый api-тип audit-полей ответов — <b>один на все поверхности</b>.
 * Аналог доменного {@code Auditable} и persistence-{@code AuditableEntity},
 * но собственный для api-слоя (.claude/rules/codestyle.md §«Auditable по
 * слоям»): доменный базовый тип в других слоях не переиспользуется.
 *
 * <p>Наследует его api-ответ той сущности, чья доменная модель наследует
 * {@code Auditable}. Состав полей повторяет бинарный состав колонок
 * (docs/models/domain/other/Auditable.md §«Правило состава колонок»):
 * половины набора не бывает ни в одном слое. Время — UTC.
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
