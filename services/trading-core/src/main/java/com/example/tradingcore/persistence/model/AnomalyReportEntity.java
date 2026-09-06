package com.example.tradingcore.persistence.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Строка отчёта о происшествии
 * (docs/models/domain/other/AnomalyReport.md).
 *
 * <p>Снимки состояния лежат JSONB'ом строкой: форму снимка знает только
 * тропа-производитель, и колонок под неё не заводится.
 *
 * <p>Перечни хранятся строкой без {@code @Enumerated} — конвертацию на
 * границе делает маппер.
 */
@Getter
@Setter
@Entity
@Table(name = "anomaly_reports")
public class AnomalyReportEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "internal_id", nullable = false)
    private String internalId;

    @Column(name = "exchange_account_id", nullable = false)
    private Long exchangeAccountId;

    @Column(name = "instrument_id")
    private Long instrumentId;

    @Column(name = "subject_external_id")
    private String subjectExternalId;

    @Column(name = "scope", nullable = false)
    private String scope;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "severity", nullable = false)
    private String severity;

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "message")
    private String message;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "internal_before")
    private String internalBefore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "external_before")
    private String externalBefore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "internal_after")
    private String internalAfter;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "external_after")
    private String externalAfter;
}
