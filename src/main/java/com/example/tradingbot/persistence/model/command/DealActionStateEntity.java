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
 * Persistence-проекция {@link com.example.tradingbot.domain.command.DealActionState}
 * (таблица deal_action_states). target и last_error — JSONB на строке;
 * retry-скаляры — обычные колонки. deal_id — скалярная колонка (без FK,
 * deals materialized отдельно), strategy_action_id — FK на
 * strategy_actions. UNIQUE(deal_id, strategy_action_id) —
 * идемпотентность исполнения. Не наследует AuditableEntity (доменный
 * DealActionState не Auditable). См.
 * docs/models/domain/other/DealActionState.md.
 */
@Getter
@Setter
@Entity
@Table(name = "deal_action_states")
public class DealActionStateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "deal_id", nullable = false)
    private Long dealId;

    @Column(name = "strategy_action_id", nullable = false)
    private Long strategyActionId;

    @Column(name = "status", nullable = false)
    private String status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "target")
    private String target;

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
