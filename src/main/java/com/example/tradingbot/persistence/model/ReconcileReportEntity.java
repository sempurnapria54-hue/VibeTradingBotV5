package com.example.tradingbot.persistence.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "sync_execution_environment_report")
public class ReconcileReportEntity {

    /**
     * Внутренний идентификатор отчёта синхронизации.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    /** Межсервисный идентификатор отчёта синхронизации. */
    @Column(name = "internal_id", nullable = false)
    private String internalId;

    /**
     * Идентификатор биржи, для которой сформирован отчёт.
     */
    @Column(name = "exchange_id", nullable = false, updatable = false)
    private Long exchangeId;

    /**
     * Время начала процесса синхронизации.
     */
    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    /**
     * Время завершения процесса синхронизации.
     */
    @Column(name = "finished_at")
    private Instant finishedAt;

    /**
     * Триггер запуска синхронизации (manual/scheduler и т.д.).
     */
    @Column(name = "trigger", nullable = false)
    private String trigger;

    /**
     * Признак наличия аномалий в процессе синхронизации.
     */
    @Column(name = "has_anomalies", nullable = false)
    private boolean hasAnomalies;

    /**
     * Максимальный уровень серьёзности обнаруженных аномалий.
     */
    @Column(name = "max_severity", nullable = false)
    private String maxSeverity;

    /**
     * JSON-снимок базы данных до синхронизации.
     */
    @Column(name = "database_before_json", nullable = false, columnDefinition = "jsonb")
    private String databaseBeforeJson;

    /**
     * JSON-снимок биржи до синхронизации.
     */
    @Column(name = "exchange_before_json", nullable = false, columnDefinition = "jsonb")
    private String exchangeBeforeJson;

    /**
     * JSON-снимок базы данных после синхронизации.
     */
    @Column(name = "database_after_json", columnDefinition = "jsonb")
    private String databaseAfterJson;

    /**
     * JSON-снимок биржи после синхронизации.
     */
    @Column(name = "exchange_after_json", columnDefinition = "jsonb")
    private String exchangeAfterJson;

    /**
     * Список зафиксированных аномалий по отчёту.
     */
    @OneToMany(mappedBy = "report", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReconcileAnomalyEntity> anomalies = new ArrayList<>();

    public void initOnCreate() {
        setInternalId(UUID.randomUUID().toString());
    }
}
