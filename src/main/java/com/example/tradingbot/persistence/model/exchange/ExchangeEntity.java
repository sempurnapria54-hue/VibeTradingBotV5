package com.example.tradingbot.persistence.model.exchange;

import com.example.tradingbot.domain.model.core.exchange.Exchange;
import com.example.tradingbot.persistence.model.AuditableEntity;
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
 * Persistence-проекция {@link Exchange} (таблица exchanges). Реальная
 * схема (уникальности, FK) — во Flyway-миграциях; здесь — ORM-маппинг.
 * Enum'ы — только в домене; статус хранится строкой (значение =
 * {@code name()} доменного enum).
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

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "risk_base")
    private BigDecimal riskBase;

    @Column(name = "risk_base_currency")
    private String riskBaseCurrency;

    @Column(name = "consecutive_loss_count", nullable = false)
    private Integer consecutiveLossCount;

    /** Подряд идущие НЕПОЛНЫЕ проходы проактивной детекции. */
    @Column(name = "blind_pass_count", nullable = false)
    private Integer blindPassCount;
}
