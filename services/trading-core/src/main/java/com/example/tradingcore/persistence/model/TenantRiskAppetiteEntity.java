package com.example.tradingcore.persistence.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/**
 * Числа риск-аппетита тенанта (таблица tenant_risk_appetites).
 *
 * <p><b>Колонки пустые намеренно:</b> пустота есть ОТКАЗ, а не ноль.
 * Значения назначает держатель — это дефицит подтверждения, который
 * машина из концепции не выводит (docs/models/domain/core/Tenant.md
 * §Персистентность, .claude/processes/question-delegation.md §«Число:
 * риск-аппетит против калибровки наблюдения»).
 *
 * <p>Строку заводит тик синка реестра счетов: тенанта ядро узнаёт из
 * счёта, потому что перечня тенантов у него нет и быть не должно.
 */
@Getter
@Setter
@Entity
@Table(name = "tenant_risk_appetites")
public class TenantRiskAppetiteEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_internal_id", nullable = false, updatable = false)
    private String tenantInternalId;

    /** Доля базы риска, которой тенант рискует одной сделкой. */
    @Column(name = "global_simultaneous_risk_per_deal_percent")
    private BigDecimal globalSimultaneousRiskPerDealPercent;

    /** Множитель катастрофического потолка сделки. */
    @Column(name = "global_catastrophic_risk_per_deal_multiplier")
    private BigDecimal globalCatastrophicRiskPerDealMultiplier;

    /** Длина серии убыточных сделок, останавливающая торговлю. */
    @Column(name = "global_consecutive_loss_limit")
    private Integer globalConsecutiveLossLimit;
}
