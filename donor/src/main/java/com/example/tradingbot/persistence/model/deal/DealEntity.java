package com.example.tradingbot.persistence.model.deal;

import com.example.tradingbot.persistence.model.AuditableEntity;
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
 * Persistence-проекция {@link com.example.tradingbot.domain.model.aggregate.deal.Deal}
 * (таблица deals). Runtime graph (orders/algoOrders/position) — отдельные
 * таблицы по deal_id, не cascade-коллекции этой строки. Логика
 * финализации PnL — шаг 7; здесь — структура. Enum'ы хранятся строкой.
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

    @Column(name = "instrument_id", nullable = false)
    private Long instrumentId;

    @Column(name = "strategy_detail_id")
    private Long strategyDetailId;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "direction", nullable = false)
    private String direction;

    @Column(name = "entry_reason")
    private String entryReason;

    @Column(name = "entry_market_phase")
    private String entryMarketPhase;

    @Column(name = "shutdown_reason")
    private String shutdownReason;

    @Column(name = "close_reason")
    private String closeReason;

    @Column(name = "result_profit")
    private BigDecimal resultProfit;

    @Column(name = "result_profit_currency")
    private String resultProfitCurrency;

    @Column(name = "planned_risk_amount")
    private BigDecimal plannedRiskAmount;

    @Column(name = "incurred_risk_amount")
    private BigDecimal incurredRiskAmount;

    @Column(name = "current_risk_amount")
    private BigDecimal currentRiskAmount;

    @Column(name = "protection_relieved_risk_amount")
    private BigDecimal protectionRelievedRiskAmount;

    @Column(name = "planned_risk_currency")
    private String plannedRiskCurrency;

    @Column(name = "planned_risk_equity_base")
    private BigDecimal plannedRiskEquityBase;

    @Column(name = "coverage_proven_through")
    private OffsetDateTime coverageProvenThrough;

    @Column(name = "bills_window_begin")
    private OffsetDateTime billsWindowBegin;

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
