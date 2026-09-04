package com.example.tradingbot.persistence.model.balance;

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
 * Persistence-проекция currency-level Balance (таблица balances, FK
 * balance_container_id).
 */
@Getter
@Setter
@Entity
@Table(name = "balances")
public class BalanceEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "balance_container_id", nullable = false)
    private Long balanceContainerId;

    @Column(name = "external_currency", nullable = false)
    private String externalCurrency;

    @Column(name = "external_updated_at")
    private OffsetDateTime externalUpdatedAt;

    @Column(name = "external_equity")
    private BigDecimal externalEquity;

    @Column(name = "external_cash_balance")
    private BigDecimal externalCashBalance;

    @Column(name = "external_available_balance")
    private BigDecimal externalAvailableBalance;

    @Column(name = "external_frozen_balance")
    private BigDecimal externalFrozenBalance;
}
