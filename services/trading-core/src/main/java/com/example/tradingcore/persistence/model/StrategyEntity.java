package com.example.tradingcore.persistence.model;

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
 * Копия определения стратегии в базе ядра (таблица strategies) — корень
 * реляционного каркаса неизменяемого дерева.
 *
 * <p><b>Это копия, а не оригинал:</b> определением владеет сервис
 * стратегий, ядро держит закреплённый снимок, по которому ведёт открытые
 * сделки даже после того, как оригинал изменился
 * (docs/models/domain/aggregate/Strategy.md §«Где живёт определение и где
 * — его копия»).
 *
 * <p><b>Радиус — биржевой счёт:</b> инвариант «одна активная стратегия»
 * стои́т на паре «счёт, инструмент», а не на инструменте.
 *
 * <p>Владеет настройкой фазы, каталогом настроек и деталями агрегатно
 * (cascade ALL + orphanRemoval). Коллекции — {@code Set}: дерево грузится
 * одним запросом с {@code join fetch} без {@code MultipleBagFetchException},
 * порядок восстанавливает маппер.
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

    /**
     * Идентичность счёта, как её прислал владелец определений: часть
     * присланного снимка, хранится как есть.
     */
    @Column(name = "exchange_account_internal_id", nullable = false, updatable = false)
    private String exchangeAccountInternalId;

    /** Идентичность инструмента из того же снимка. */
    @Column(name = "instrument_internal_id", nullable = false, updatable = false)
    private String instrumentInternalId;

    /**
     * Числовой ключ строки счёта <b>в базе ядра</b>: локальная связь, а не
     * часть определения. Резолвится из присланной идентичности на границе
     * domain → persistence (docs/models/mapping/Strategy.md).
     */
    @Column(name = "exchange_account_id", nullable = false, updatable = false)
    private Long exchangeAccountId;

    /** То же для инструмента: ключ строки проекции каталога у ядра. */
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
