package com.example.tradingcore.persistence.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Строка ступени и торговых настроек счёта на инструменте (таблица
 * account_instrument_states, docs/models/domain/core/Instrument.md
 * §«Ступень и настройки счёта на инструменте — своя таблица ядра»).
 *
 * <p>Уникальный ключ пары «счёт, инструмент» объявлен схемой: он же гасит
 * гонку двух ленивых читателей (docs/rules/idempotency-via-unique.md).
 *
 * <p>Перечни хранятся строкой, без {@code @Enumerated}
 * (.claude/rules/codestyle.md §«Слои моделей и enum'ы»).
 */
@Getter
@Setter
@Entity
@Table(name = "account_instrument_states")
public class AccountInstrumentStateEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "exchange_account_id", nullable = false, updatable = false)
    private Long exchangeAccountId;

    @Column(name = "instrument_id", nullable = false, updatable = false)
    private Long instrumentId;

    @Column(name = "safety_rung", nullable = false)
    private String safetyRung;

    @Column(name = "margin_mode")
    private String marginMode;

    @Column(name = "leverage")
    private Integer leverage;
}
