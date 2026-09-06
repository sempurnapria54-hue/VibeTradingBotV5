package com.example.strategies.persistence.model;

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
 * Строка outbox: конверт события колонками плюс содержимое JSONB'ом
 * (docs/architecture/contracts.md §«Конверт события»).
 *
 * <p><b>Поля конверта стоя́т колонками, а содержимое — нет.</b> Конверт
 * один на все события платформы, и по нему читает реле; форму содержимого
 * знает только производитель класса, и колонок под неё не заводится.
 *
 * <p><b>Аудита у строки нет намеренно:</b> она сама и есть запись факта со
 * своим моментом происшествия, а её единственная правка — отметка
 * публикации, у которой свой момент.
 */
@Getter
@Setter
@Entity
@Table(name = "outbox_events")
public class OutboxEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, updatable = false)
    private String eventId;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "event_type", nullable = false, updatable = false)
    private String eventType;

    @Column(name = "version", nullable = false, updatable = false)
    private Integer version;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private OffsetDateTime occurredAt;

    @Column(name = "trace_context", updatable = false)
    private String traceContext;

    @Column(name = "topic", nullable = false, updatable = false)
    private String topic;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, updatable = false)
    private String payload;

    /** Момент публикации; пусто — строка ждёт реле. */
    @Column(name = "published_at")
    private OffsetDateTime publishedAt;
}
