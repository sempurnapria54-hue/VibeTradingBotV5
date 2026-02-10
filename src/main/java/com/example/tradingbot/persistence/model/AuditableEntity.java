package com.example.tradingbot.persistence.model;

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

    @CreatedDate
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @CreatedBy
    @Column(name = "created_by", length = AUDIT_NAME_LENGTH)
    private String createdBy;

    @LastModifiedDate
    @Column(name = "modified_at")
    private OffsetDateTime modifiedAt;

    @LastModifiedBy
    @Column(name = "modified_by", length = AUDIT_NAME_LENGTH)
    private String modifiedBy;
}
