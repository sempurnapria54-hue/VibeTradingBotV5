package com.example.tradingbot.persistence.model.command;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Persistence-проекция {@link com.example.tradingbot.domain.command.DealFinalizationState}
 * (таблица deal_finalization_states). last_error — JSONB на строке;
 * retry-скаляры — обычные колонки; deal_id — скалярная колонка (без FK,
 * deals materialized отдельно). UNIQUE(deal_id, type) — идемпотентность
 * финализации (одна строка на финализационную команду сделки). Не
 * наследует AuditableEntity (доменный DealFinalizationState не Auditable),
 * по аналогии с DealActionStateEntity. См.
 * docs/models/domain/other/DealFinalizationState.md.
 */
@Getter
@Setter
@Entity
@Table(name = "deal_finalization_states")
public class DealFinalizationStateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "deal_id", nullable = false)
    private Long dealId;

    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "attempt_count")
    private Integer attemptCount;

    @Column(name = "max_attempts")
    private Integer maxAttempts;

    @Column(name = "next_retry_at")
    private OffsetDateTime nextRetryAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "last_error")
    private String lastError;
}
