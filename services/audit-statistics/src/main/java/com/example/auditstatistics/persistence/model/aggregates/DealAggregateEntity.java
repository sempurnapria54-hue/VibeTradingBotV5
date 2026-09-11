package com.example.auditstatistics.persistence.model.aggregates;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Строка сделочного зерна агрегатов
 * (docs/rules/statistics-aggregates.md §Персистентность).
 *
 * <p><b>Строка — проекция журнала, а не накопитель:</b> её числа
 * пересчитываются из журнала целиком, и событием она не двигается. Отсюда
 * единственная тропа записи — upsert джобы пересчёта по ключу зерна, и
 * живёт он нативным запросом репозитория: сохранение сущности потребовало
 * бы сперва найти строку по зерну, то есть завести седьмую точку формы
 * «спросить и вставить» (docs/rules/idempotency-via-unique.md).
 *
 * <p><b>Класс держит отображение схемы</b> — по нему строки читаются
 * агрегатной выборкой, и по нему же разбирает запросы модуля сборщик
 * живого прогона. Полей аудита у строки нет вовсе: набор {@code Auditable}
 * бинарен, а писатель у строки один и момент своей сборки она несёт
 * собственной колонкой.
 *
 * <p><b>Две колонки ключа зерна законно пусты</b> — определение стратегии
 * («сделка стратегии не имеет») и расчётная валюта («валюта результата не
 * резолвилась»); уникальность зерна держит {@code uk_deal_aggregate_grain}
 * с клаузой {@code nulls not distinct}, а не первичный ключ.
 */
@Getter
@Setter
@Entity
@Table(name = "deal_aggregates")
public class DealAggregateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Тенант-владелец строки: ключ зерна и радиус чтения. */
    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    /** Биржевой счёт: ключ зерна — радиус, по которому работают остановки. */
    @Column(name = "exchange_account_internal_id", nullable = false)
    private String exchangeAccountInternalId;

    /** Определение стратегии: ключ зерна; пусто законно. */
    @Column(name = "strategy_internal_id")
    private String strategyInternalId;

    /** Сутки UTC: ключ зерна и порция прохода пересчёта. */
    @Column(name = "bucket_date", nullable = false)
    private LocalDate bucketDate;

    /** Расчётная валюта результата: ключ зерна; пусто законно. */
    @Column(name = "result_currency")
    private String resultCurrency;

    /** Закрытых сделок за сутки — оба терминала. */
    @Column(name = "closed_deals", nullable = false)
    private Integer closedDeals;

    /** Из них принявших риск: популяция всех долей. */
    @Column(name = "risk_bearing_deals", nullable = false)
    private Integer riskBearingDeals;

    /** Из принявших риск — с положительным результатом до финансирования. */
    @Column(name = "winning_deals", nullable = false)
    private Integer winningDeals;

    /** Из принявших риск — с отрицательным результатом до финансирования. */
    @Column(name = "losing_deals", nullable = false)
    private Integer losingDeals;

    /** Из принявших риск — с нулевым результатом до финансирования. */
    @Column(name = "neutral_deals", nullable = false)
    private Integer neutralDeals;

    /** Из принявших риск — те, у кого результат недоступен. */
    @Column(name = "result_unavailable_deals", nullable = false)
    private Integer resultUnavailableDeals;

    /** Из принявших риск — те, у кого расчётная валюта не резолвилась. */
    @Column(name = "currency_unresolved_deals", nullable = false)
    private Integer currencyUnresolvedDeals;

    /** Из принявших риск — те, у кого плановый риск нулевой. */
    @Column(name = "risk_unsized_deals", nullable = false)
    private Integer riskUnsizedDeals;

    /** Из принявших риск — закрытые биржей по марже. */
    @Column(name = "liquidated_deals", nullable = false)
    private Integer liquidatedDeals;

    /** Из принявших риск — принудительно сокращённые биржей. */
    @Column(name = "forced_reduction_deals", nullable = false)
    private Integer forcedReductionDeals;

    /** Из принявших риск — с неустановленным торговым исходом. */
    @Column(name = "outcome_undetermined_deals", nullable = false)
    private Integer outcomeUndeterminedDeals;

    /** Из принявших риск — с несошедшейся сверкой. */
    @Column(name = "reconciliation_mismatched_deals", nullable = false)
    private Integer reconciliationMismatchedDeals;

    /** Из принявших риск — с непроверенной сверкой. */
    @Column(name = "reconciliation_not_run_deals", nullable = false)
    private Integer reconciliationNotRunDeals;

    /** Из принявших риск — с неполной разбивкой движений. */
    @Column(name = "breakdown_incomplete_deals", nullable = false)
    private Integer breakdownIncompleteDeals;

    /** Из принявших риск — с неоценённой полнотой разбивки. */
    @Column(name = "breakdown_not_assessed_deals", nullable = false)
    private Integer breakdownNotAssessedDeals;

    /** Из принявших риск — с потерянной базой риска. */
    @Column(name = "risk_benchmark_missing_deals", nullable = false)
    private Integer riskBenchmarkMissingDeals;

    /** Сколько сделок вошло в сумму R-мультипликаторов. */
    @Column(name = "r_denominator_deals", nullable = false)
    private Integer rDenominatorDeals;

    /** Сумма результатов до накопленного финансирования. */
    @Column(name = "result_before_funding_sum", nullable = false)
    private BigDecimal resultBeforeFundingSum;

    /** Сумма итогов — net по всем издержкам, включая финансирование. */
    @Column(name = "net_result_sum", nullable = false)
    private BigDecimal netResultSum;

    /** Комиссии обеих ног, издержкой положительные. */
    @Column(name = "fee_sum", nullable = false)
    private BigDecimal feeSum;

    /** Накопленное финансирование, издержкой положительное. */
    @Column(name = "funding_sum", nullable = false)
    private BigDecimal fundingSum;

    /** Штрафы принудительного закрытия, издержкой положительные. */
    @Column(name = "liquidation_penalty_sum", nullable = false)
    private BigDecimal liquidationPenaltySum;

    /** Сумма выигрышей: без неё профит-фактор не вычислим никак. */
    @Column(name = "win_result_sum", nullable = false)
    private BigDecimal winResultSum;

    /** Сумма убытков, знак сохранён. */
    @Column(name = "loss_result_sum", nullable = false)
    private BigDecimal lossResultSum;

    /** Плановый риск сделок, вошедших в денежные суммы. */
    @Column(name = "planned_risk_sum", nullable = false)
    private BigDecimal plannedRiskSum;

    /** Плановый риск сделок, выведенных из этих сумм. */
    @Column(name = "planned_risk_excluded_sum", nullable = false)
    private BigDecimal plannedRiskExcludedSum;

    /** Сумма R-мультипликаторов сделок. */
    @Column(name = "r_sum", nullable = false)
    private BigDecimal rSum;

    /**
     * Момент сборки строки: показание читателю, а не операнд выбора
     * пересчитываемого (docs/rules/statistics-aggregates.md §«Пересчёт —
     * проекция, а не накопитель»).
     */
    @Column(name = "assembled_at", nullable = false)
    private OffsetDateTime assembledAt;
}
