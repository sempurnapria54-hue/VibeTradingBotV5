package com.example.auditstatistics.persistence.model.aggregates;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Строка зерна происшествий
 * (docs/rules/statistics-aggregates.md §«Счётчики происшествий — своё
 * зерно, а не строка сделочного агрегата»).
 *
 * <p><b>Зерно у неё СВОЁ, и разводит зёрна состав события, а не то, сумма
 * это или счёт:</b> расчётной валюты не несёт ни одно из четырёх её
 * событий, а ключом сделочного зерна она стои́т. Уложенные в сделочную
 * строку, эти счётчики попали бы в клетку с пустой валютой, где пустота
 * уже занята другим смыслом.
 *
 * <p><b>Пустых колонок в ключе зерна нет ни одной</b>, поэтому и клауза
 * {@code nulls not distinct} этой таблице не нужна; суррогатный первичный
 * ключ оставлен ради единой формы строки с соседней таблицей.
 *
 * <p>Тропа записи и довод её формы — те же, что у
 * {@link DealAggregateEntity}: upsert джобы пересчёта по ключу зерна.
 */
@Getter
@Setter
@Entity
@Table(name = "incident_aggregates")
public class IncidentAggregateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Тенант-владелец строки: ключ зерна и радиус чтения. */
    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    /** Биржевой счёт: ключ зерна. */
    @Column(name = "exchange_account_internal_id", nullable = false)
    private String exchangeAccountInternalId;

    /** Сутки UTC: ключ зерна и порция прохода пересчёта. */
    @Column(name = "bucket_date", nullable = false)
    private LocalDate bucketDate;

    /** Заведённых сделок за сутки. */
    @Column(name = "opened_deals", nullable = false)
    private Integer openedDeals;

    /** Решений о создании обычной заявки; перестановка ноги даёт своё решение. */
    @Column(name = "order_decisions", nullable = false)
    private Integer orderDecisions;

    /** Поднятых ступеней — обе тропы вместе. */
    @Column(name = "raised_holds", nullable = false)
    private Integer raisedHolds;

    /** Из них жёстких. */
    @Column(name = "hard_raised_holds", nullable = false)
    private Integer hardRaisedHolds;

    /** Из них поднятых ручной тропой; тропу различает код операции, а не актор. */
    @Column(name = "manually_raised_holds", nullable = false)
    private Integer manuallyRaisedHolds;

    /** Отчётов о происшествиях — обе тропы вместе. */
    @Column(name = "anomaly_reports", nullable = false)
    private Integer anomalyReports;

    /** Из них критичных. */
    @Column(name = "critical_anomaly_reports", nullable = false)
    private Integer criticalAnomalyReports;

    /** Из них заведённых ручной тропой, а не детекцией. */
    @Column(name = "manual_operation_reports", nullable = false)
    private Integer manualOperationReports;

    /** Момент сборки строки. */
    @Column(name = "assembled_at", nullable = false)
    private OffsetDateTime assembledAt;
}
