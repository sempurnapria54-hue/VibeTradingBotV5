package com.example.tradingcore.persistence.model;

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
 * Строка агрегата сделки (таблица deals).
 *
 * <p><b>Радиус строки — биржевой счёт, а не площадка:</b> слот держит пара
 * «счёт, инструмент», и все выборки радиуса читаются от счёта
 * (docs/models/domain/aggregate/Deal.md §Персистентность). Тенанта строка
 * не несёт — он резолвится по счёту.
 *
 * <p>Транши, эпизоды позиции, ноги и разбивка движений — отдельные таблицы
 * по {@code deal_id}: каскадных коллекций у этой строки нет, граф собирает
 * контекст прохода. Енумы хранятся именем значения.
 */
@Getter
@Setter
@Entity
@Table(name = "deals")
public class DealEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "internal_id", nullable = false, updatable = false)
    private String internalId;

    @Column(name = "exchange_account_id", nullable = false)
    private Long exchangeAccountId;

    @Column(name = "instrument_id", nullable = false)
    private Long instrumentId;

    /** Закреплённая деталь объявления; пусто ровно у восстановленной сделки. */
    @Column(name = "strategy_detail_id")
    private Long strategyDetailId;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "direction", nullable = false)
    private String direction;

    @Column(name = "entry_reason", nullable = false)
    private String entryReason;

    @Column(name = "entry_market_phase")
    private String entryMarketPhase;

    @Column(name = "shutdown_reason")
    private String shutdownReason;

    @Column(name = "close_reason")
    private String closeReason;

    @Column(name = "result_profit", precision = 36, scale = 18)
    private BigDecimal resultProfit;

    @Column(name = "result_profit_currency")
    private String resultProfitCurrency;

    @Column(name = "planned_risk_amount", precision = 36, scale = 18)
    private BigDecimal plannedRiskAmount;

    @Column(name = "incurred_risk_amount", precision = 36, scale = 18)
    private BigDecimal incurredRiskAmount;

    @Column(name = "current_risk_amount", precision = 36, scale = 18)
    private BigDecimal currentRiskAmount;

    @Column(name = "protection_relieved_risk_amount", precision = 36, scale = 18)
    private BigDecimal protectionRelievedRiskAmount;

    @Column(name = "planned_risk_currency")
    private String plannedRiskCurrency;

    /** База риска на момент первого сайзинга. Write-once — охрана в запросе. */
    @Column(name = "planned_risk_equity_base", precision = 36, scale = 18)
    private BigDecimal plannedRiskEquityBase;

    /** Порог доказанного покрытия. Монотонен вперёд — охрана в запросе. */
    @Column(name = "coverage_proven_through")
    private OffsetDateTime coverageProvenThrough;

    /** Нижняя граница окна линковки движений. Write-once — охрана в запросе. */
    @Column(name = "bills_window_begin")
    private OffsetDateTime billsWindowBegin;

    /** До какого момента движения добыты. Монотонна вперёд — охрана в запросе. */
    @Column(name = "bills_fetched_through")
    private OffsetDateTime billsFetchedThrough;

    @Column(name = "close_outcome")
    private String closeOutcome;

    @Column(name = "reconciliation_status")
    private String reconciliationStatus;

    @Column(name = "breakdown_incomplete")
    private String breakdownIncomplete;

    @Column(name = "risk_benchmark_availability")
    private String riskBenchmarkAvailability;
}
