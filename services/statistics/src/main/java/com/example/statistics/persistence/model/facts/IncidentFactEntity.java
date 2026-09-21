package com.example.statistics.persistence.model.facts;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Строка факта происшествия
 * (docs/models/domain/other/StatisticsFact.md §«Факт происшествия»).
 *
 * <p><b>Разрезы законно пусты</b> и означают «разрез у этого класса не
 * определён», а не «значение потеряно»
 * (docs/rules/absent-value-semantics.md).
 *
 * <p><b>Ключ СОСТАВНОЙ и естественный, суррогата у ряда нет</b>
 * ({@link IncidentFactId}) — по тому же требованию Timescale, что у соседа
 * по зерну ({@link DealFactEntity}).
 */
@Getter
@Setter
@Entity
@Table(name = "incident_facts")
@IdClass(IncidentFactId.class)
public class IncidentFactEntity {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private String eventId;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "exchange_account_internal_id", nullable = false, updatable = false)
    private String exchangeAccountInternalId;

    @Id
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private OffsetDateTime occurredAt;

    @Column(name = "event_type", nullable = false, updatable = false)
    private String eventType;

    @Column(name = "hold_rung", updatable = false)
    private String holdRung;

    @Column(name = "anomaly_severity", updatable = false)
    private String anomalySeverity;

    @Column(name = "operation_code", updatable = false)
    private String operationCode;
}
