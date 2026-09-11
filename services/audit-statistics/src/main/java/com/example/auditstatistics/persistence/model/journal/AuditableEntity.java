package com.example.auditstatistics.persistence.model.journal;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Базовый тип audit-полей persistence-слоя. Свой в каждом слое, с
 * постфиксом слоя (.claude/rules/codestyle.md §«Auditable по слоям»);
 * доменный {@code Auditable} здесь не переиспользуется.
 *
 * <p><b>Лежит в пакете отображения ЖУРНАЛА, а не рядом с ним, и это не
 * раскладка по вкусу.</b> Отображений схемы на классы у процесса три, и
 * область сканирования у каждого своя ({@code JournalPersistenceConfig}
 * §{@code ENTITY_PACKAGE}); базовый тип, положенный в родительский пакет,
 * попал бы либо мимо области сканирования, либо — вместе с пакетом — в
 * область соседнего отображения. Носитель полей аудита у процесса сегодня
 * один, и он журнальный: строка отвергнутого вызова
 * (docs/models/domain/other/AccessDenial.md).
 *
 * <p><b>Строка журнала событий этого типа НЕ наследует, и это названо</b>
 * ({@code AuditRecordEntity}): событие произошло однажды, правок у записи
 * о нём не бывает, а набор колонок аудита бинарен — три его системных поля
 * остались бы пустыми навсегда
 * (docs/models/domain/other/Auditable.md §«Правило состава колонок»).
 *
 * <p>Системные поля проставляет JPA auditing ({@code JpaAuditConfig});
 * биржевые {@code external*} — код, производящий данные. Время — UTC
 * (docs/rules/time-utc.md).
 */
@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AuditableEntity {

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private String createdBy;

    @LastModifiedDate
    @Column(name = "modified_at")
    private OffsetDateTime modifiedAt;

    @LastModifiedBy
    @Column(name = "modified_by")
    private String modifiedBy;

    @Column(name = "external_created_at")
    private OffsetDateTime externalCreatedAt;

    @Column(name = "external_modified_at")
    private OffsetDateTime externalModifiedAt;
}
