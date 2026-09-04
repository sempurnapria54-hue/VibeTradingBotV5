package com.example.tradingbot.persistence.model.strategy;

import com.example.tradingbot.domain.model.aggregate.strategy.Strategy;
import com.example.tradingbot.persistence.model.AuditableEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

/**
 * Persistence-проекция {@link Strategy} (таблица strategies) — корень
 * реляционного каркаса immutable-дерева. Владеет настройкой фазы и
 * деталями (агрегат: cascade ALL + orphanRemoval). Реальная схема
 * (уникальность internal_id, частичный UNIQUE «одна ACTIVE на
 * инструмент», FK) — во Flyway. Enum'ы — только в домене; статус
 * хранится строкой (значение = {@code name()}). Коллекции — Set:
 * дерево грузится одним JOIN FETCH-запросом без
 * MultipleBagFetchException; порядок восстанавливает маппер.
 */
@Getter
@Setter
@Entity
@Table(name = "strategies")
public class StrategyEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "internal_id", nullable = false, updatable = false)
    private String internalId;

    @Column(name = "instrument_id", nullable = false, updatable = false)
    private Long instrumentId;

    @Column(name = "name", nullable = false, updatable = false)
    private String name;

    @Column(name = "status", nullable = false)
    private String status;

    @OneToOne(mappedBy = "strategy", cascade = CascadeType.ALL, orphanRemoval = true)
    private StrategyMarketPhaseSettingEntity marketPhaseSetting;

    @OneToMany(mappedBy = "strategy", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<StrategyDetailEntity> details;

    @OneToMany(mappedBy = "strategy", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<StrategyIndicatorSettingEntity> indicatorSettings;

    @OneToMany(mappedBy = "strategy", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<StrategyMarketStructureSettingEntity> marketStructureSettings;
}
