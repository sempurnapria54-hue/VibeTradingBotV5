package com.example.tradingcore.persistence.model;

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
 * <p>Системные поля проставляет JPA auditing ({@code JpaAuditConfig});
 * биржевые {@code external*} — код, производящий данные. Время — UTC.
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
