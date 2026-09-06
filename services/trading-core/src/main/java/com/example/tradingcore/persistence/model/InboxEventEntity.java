package com.example.tradingcore.persistence.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Строка inbox: отметка о том, что событие с такой идентичностью уже
 * обработано (docs/architecture/data-ownership.md §«Outbox и доставка»).
 *
 * <p><b>Это дедуп, а не журнал.</b> Содержимого события строка не несёт:
 * журнал событий по тенанту — предмет аудита, и он читает поток сам
 * (docs/rules/audit-not-runtime-source.md). Здесь достаточно
 * идентичности, класса и момента обработки.
 *
 * <p><b>Аудита у строки нет намеренно:</b> она сама и есть запись факта
 * обработки со своим моментом, а правок у неё не бывает.
 */
@Getter
@Setter
@Entity
@Table(name = "inbox_events")
public class InboxEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, updatable = false)
    private String eventId;

    @Column(name = "event_type", nullable = false, updatable = false)
    private String eventType;

    @Column(name = "consumed_at", nullable = false, updatable = false)
    private OffsetDateTime consumedAt;
}
