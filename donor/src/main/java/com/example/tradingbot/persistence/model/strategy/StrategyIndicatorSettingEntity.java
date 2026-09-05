package com.example.tradingbot.persistence.model.strategy;

import com.example.tradingbot.domain.model.aggregate.strategy.setting.StrategyIndicatorSetting;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Persistence-проекция {@link StrategyIndicatorSetting} (таблица
 * strategy_indicator_settings) — strategy-scope-строка, объявляется раз
 * (UNIQUE(strategy_id, key)); цель FK результата расчёта IndicatorValue
 * (ключевание идентичностью вычисления, трек D). {@code indicator_type} — дискриминатор
 * подтипа params (колонка-владелец строки); {@code params} — JSONB на
 * строке без дублирования тега (десериализуется в подтип по indicator_type,
 * docs/rules/persistence-representation.md). Не Auditable (доменная
 * настройка — value, не Auditable).
 */
@Getter
@Setter
@Entity
@Table(name = "strategy_indicator_settings")
public class StrategyIndicatorSettingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "strategy_id", nullable = false, updatable = false)
    private StrategyEntity strategy;

    @Column(name = "key", nullable = false, updatable = false)
    private String key;

    @Column(name = "indicator_type", nullable = false)
    private String indicatorType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "params", nullable = false)
    private String params;

    @Column(name = "destiny", nullable = false)
    private String destiny;

    @Column(name = "expiration_duration", nullable = false)
    private String expirationDuration;
}
