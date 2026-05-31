package com.example.tradingbot.persistence.model.exchange;

import com.example.tradingbot.domain.model.core.exchange.Exchange;
import com.example.tradingbot.persistence.model.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Persistence-проекция {@link Exchange} (таблица exchanges). Реальная
 * схема (уникальности, FK) — во Flyway-миграциях; здесь — ORM-маппинг.
 */
@Getter
@Setter
@Entity
@Table(name = "exchanges")
public class ExchangeEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "internal_id", nullable = false, updatable = false)
    private String internalId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "base_url", nullable = false)
    private String baseUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Exchange.Status status;
}
