package com.example.statistics.persistence.model.facts;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Строка сделочного факта
 * (docs/models/domain/other/StatisticsFact.md §«Сделочный факт»).
 *
 * <p><b>Колонки неизменяемы:</b> факт есть след принятого события, и
 * правки его не бывает. Повторная доставка поглощается конфликтом по
 * ключу {@code event_id}, а не обновлением строки.
 *
 * <p><b>Содержимого как доставлено здесь нет вовсе</b> — колонка с ним
 * сделала бы факт журналом (там же, §«Почему это не второй журнал»).
 */
@Getter
@Setter
@Entity
@Table(name = "deal_facts")
public class DealFactEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, updatable = false)
    private String eventId;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "exchange_account_internal_id", nullable = false, updatable = false)
    private String exchangeAccountInternalId;

    @Column(name = "strategy_internal_id", updatable = false)
    private String strategyInternalId;

    @Column(name = "result_currency", updatable = false)
    private String resultCurrency;

    @Column(name = "closed_at", nullable = false, updatable = false)
    private OffsetDateTime closedAt;

    @Column(name = "took_risk", updatable = false)
    private Boolean tookRisk;

    @Column(name = "graph_complete", updatable = false)
    private Boolean graphComplete;

    @Column(name = "net_result", precision = 36, scale = 18, updatable = false)
    private BigDecimal netResult;

    @Column(name = "fee", precision = 36, scale = 18, updatable = false)
    private BigDecimal fee;

    @Column(name = "funding", precision = 36, scale = 18, updatable = false)
    private BigDecimal funding;

    @Column(name = "liquidation_penalty", precision = 36, scale = 18, updatable = false)
    private BigDecimal liquidationPenalty;

    @Column(name = "planned_risk", precision = 36, scale = 18, updatable = false)
    private BigDecimal plannedRisk;

    @Column(name = "close_outcome", updatable = false)
    private String closeOutcome;

    @Column(name = "reconciliation_status", updatable = false)
    private String reconciliationStatus;

    @Column(name = "breakdown_incomplete", updatable = false)
    private String breakdownIncomplete;

    @Column(name = "risk_benchmark_availability", updatable = false)
    private String riskBenchmarkAvailability;
}
