package com.example.tradingbot.persistence.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "sync_execution_environment_report_anomaly")
public class ReconcileAnomalyEntity {

    /**
     * Внутренний идентификатор аномалии.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    /**
     * Идентификатор отчёта, к которому относится аномалия.
     */
    @Column(name = "report_id", nullable = false, updatable = false, insertable = false)
    private Long reportId;

    /**
     * Ссылка на родительский отчёт синхронизации.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "report_id", nullable = false)
    private ReconcileReportEntity report;

    /**
     * Идентификатор инструмента на бирже, если аномалия относится к инструменту.
     */
    @Column(name = "external_instrument_id")
    private String externalInstrumentId;

    /**
     * Тип аномалии.
     */
    @Column(name = "type", nullable = false)
    private String type;

    /**
     * Уровень серьёзности аномалии.
     */
    @Column(name = "severity", nullable = false)
    private String severity;

    /**
     * Краткое текстовое описание аномалии.
     */
    @Column(name = "summary", nullable = false)
    private String summary;

    /**
     * Детали аномалии в формате JSON.
     */
    @Column(name = "details_json", nullable = false, columnDefinition = "jsonb")
    private String detailsJson;

    /**
     * Время создания записи аномалии.
     */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
