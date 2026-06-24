package com.example.tradingbot.persistence.model.anomaly;

import com.example.tradingbot.persistence.model.AuditableEntity;
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
 * Persistence-проекция {@link com.example.tradingbot.domain.safety.AnomalyReport}
 * (таблица anomaly_reports). Четыре слепка состояния
 * (internal/external × before/after) — JSONB на этой строке. Enum'ы (scope,
 * status, severity) строкой.
 */
@Getter
@Setter
@Entity
@Table(name = "anomaly_reports")
public class AnomalyReportEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "internal_id", nullable = false, updatable = false)
    private String internalId;

    @Column(name = "exchange_id", nullable = false)
    private Long exchangeId;

    @Column(name = "instrument_id")
    private Long instrumentId;

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
