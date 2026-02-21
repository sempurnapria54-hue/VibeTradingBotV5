package com.example.tradingbot.domain.model.entity;

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

@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AuditableEntity {

    public static final int AUDIT_NAME_LENGTH = 255;

    /** Дата и время создания записи. */
    @CreatedDate
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    /** Пользователь/сервис, создавший запись. */
    @CreatedBy
    @Column(name = "created_by")
    private String createdBy;

    /** Дата и время последнего изменения записи. */
    @LastModifiedDate
    @Column(name = "modified_at")
    private OffsetDateTime modifiedAt;

    /** Пользователь/сервис, выполнивший последнее изменение. */
    @LastModifiedBy
    @Column(name = "modified_by")
    private String modifiedBy;
}
