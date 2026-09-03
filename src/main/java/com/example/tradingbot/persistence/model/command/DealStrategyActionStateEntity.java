package com.example.tradingbot.persistence.model.command;

import com.example.tradingbot.persistence.model.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Persistence-проекция СТРАТЕГИЙНОГО исполнения
 * {@link com.example.tradingbot.domain.command.DealActionState}
 * (таблица deal_strategy_action_states). Ссылка на узел стратегии
 * обязательна — вид действия кодируется таблицей, колонки рода нет.
 * Цель — две скалярные колонки, а не JSONB: тип цели входит в ключ
 * уникальности. last_error — JSONB; retry-скаляры — обычные колонки.
 * См. docs/models/domain/other/DealActionState.md §Инварианты.
 */
@Getter
@Setter
@Entity
@Table(name = "deal_strategy_action_states")
public class DealStrategyActionStateEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "dealActionStateSequence")
    @SequenceGenerator(name = "dealActionStateSequence", sequenceName = "deal_action_state_seq", allocationSize = 1)
    private Long id;

    @Column(name = "deal_id", nullable = false)
    private Long dealId;

    @Column(name = "deal_tranche_id")
    private Long dealTrancheId;

    @Column(name = "tranche_episode_seq")
    private Integer trancheEpisodeSeq;

    @Column(name = "strategy_action_id", nullable = false)
    private Long strategyActionId;

    @Column(name = "target_entity_type")
    private String targetEntityType;

    @Column(name = "target_entity_id")
    private Long targetEntityId;

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
