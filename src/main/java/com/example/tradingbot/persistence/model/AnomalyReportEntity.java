package com.example.tradingbot.persistence.model;

import com.example.tradingbot.domain.model.anomaly.AnomalyReport;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "anomaly_reports")
public class AnomalyReportEntity extends AuditableEntity {

    /**
     * Внутренний идентификатор отчёта об аномалии.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    /**
     * Идентификатор биржи.
     */
    @Column(name = "exchange_id", nullable = false)
    private Long exchangeId;

    /**
     * Идентификатор инструмента.
     */
    @Column(name = "instrument_id")
    private Long instrumentId;

    /**
     * Текущий статус обработки аномалии.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AnomalyReport.Status status;

    /**
     * Уровень серьёзности аномалии.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false)
    private AnomalyReport.Severity severity;

    /**
     * Код аномалии.
     */
    @Column(name = "code", nullable = false)
    private String code;

    /**
     * Текстовое описание аномалии.
     */
    @Column(name = "message")
    private String message;

    /**
     * Внутренний снимок до обработки в JSON.
     */
    @Column(name = "internal_before", nullable = false, columnDefinition = "jsonb")
    private String internalBefore;

    /**
     * Внешний снимок до обработки в JSON.
     */
    @Column(name = "external_before", nullable = false, columnDefinition = "jsonb")
    private String externalBefore;

    /**
     * Внутренний снимок после обработки в JSON.
     */
    @Column(name = "internal_after", columnDefinition = "jsonb")
    private String internalAfter;

    /**
     * Внешний снимок после обработки в JSON.
     */
    @Column(name = "external_after", columnDefinition = "jsonb")
    private String externalAfter;
}
