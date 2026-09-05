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
 * Проекция реестра счетов ПЛЮС торговое состояние счёта (таблица
 * exchange_accounts базы trading_core).
 *
 * <p><b>Одна таблица, а не две:</b> ключ у них общий, а правило «у таблицы
 * один писатель» адресует сервисы, не тропы. Тропы разведены по колонкам —
 * проекционные ({@code tenantInternalId}, {@code exchangeCode},
 * {@code label}, {@code contour}, {@code status}, {@code projectedAt})
 * пишет тик синка, торговые ({@code riskBase}, счётчики,
 * {@code safetyRung}) пишет торговый код ядра
 * (docs/models/domain/core/ExchangeAccount.md §Персистентность).
 *
 * <p><b>Тенант назван внешней идентичностью:</b> числовой ключ базы
 * {@code auth} границу сервиса не пересекает.
 *
 * <p>Ключей счёта здесь нет ни в каком виде: они в Vault по пути,
 * выводимому из {@code internalId}.
 */
@Getter
@Setter
@Entity
@Table(name = "exchange_accounts")
public class ExchangeAccountEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "internal_id", nullable = false, updatable = false)
    private String internalId;

    @Column(name = "tenant_internal_id", nullable = false)
    private String tenantInternalId;

    @Column(name = "exchange_code", nullable = false)
    private String exchangeCode;

    @Column(name = "label")
    private String label;

    @Column(name = "contour", nullable = false)
    private String contour;

    @Column(name = "status", nullable = false)
    private String status;

    /** Момент снимка проекционных колонок. Пишет только тик синка. */
    @Column(name = "projected_at", nullable = false)
    private OffsetDateTime projectedAt;

    /**
     * База риска счёта. Пусто — отказ risk-creating действия, а не ноль
     * (docs/rules/risk-policy.md).
     */
    @Column(name = "risk_base")
    private BigDecimal riskBase;

    @Column(name = "risk_base_currency")
    private String riskBaseCurrency;

    @Column(name = "consecutive_loss_count", nullable = false)
    private Integer consecutiveLossCount;

    @Column(name = "blind_pass_count", nullable = false)
    private Integer blindPassCount;

    /** Ступень лестницы реакций, стоящая на счёте (docs/rules/exchange-hold.md). */
    @Column(name = "safety_rung", nullable = false)
    private String safetyRung;
}
