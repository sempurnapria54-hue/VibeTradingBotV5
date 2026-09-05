package com.example.auth.persistence.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Реестровая часть биржевого счёта тенанта
 * (docs/models/domain/core/ExchangeAccount.md §Персистентность).
 *
 * <p><b>Ключей здесь нет ни в каком виде</b> — они в Vault по пути,
 * выводимому из {@code internal_id}. Торговое состояние счёта (база
 * риска, серия убытков, счётчик слепых проходов) — вторая таблица в базе
 * `trading_core`, у своего писателя; заводит её шаг 7 фазы 2.
 */
@Getter
@Setter
@Entity
@Table(name = "exchange_accounts")
public class ExchangeAccountEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "internal_id", nullable = false, unique = true)
    private String internalId;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "exchange_code", nullable = false)
    private String exchangeCode;

    @Column(name = "label", nullable = false)
    private String label;

    @Column(name = "contour", nullable = false)
    private String contour;

    @Column(name = "status", nullable = false)
    private String status;
}
