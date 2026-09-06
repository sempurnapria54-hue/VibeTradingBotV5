package com.example.strategies.persistence.model;

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
 * Определение стратегии у его владельца (таблица strategies) — корень
 * реляционного каркаса неизменяемого дерева.
 *
 * <p><b>Это ОРИГИНАЛ:</b> ядро держит с него закреплённый снимок и ведёт
 * по нему открытые сделки даже после деактивации; само определение не
 * правится вовсе — для изменения правил заводится новая стратегия
 * (docs/models/domain/aggregate/Strategy.md §«Где живёт определение и где
 * — его копия»).
 *
 * <p><b>Ссылочной целостности к чужим реестрам схема не держит:</b> счёт
 * и инструмент живут у соседей, и проверяет их существование валидация,
 * читающая соседа (docs/rules/strategy-validation.md).
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
     * Тенант-владелец определения.
     *
     * <p><b>Несётся строкой потому, что строки счёта у этого сервиса
     * нет:</b> резолв «тенант по счёту», которым пользуются торговые
     * строки ядра, здесь неисполним — признак применимости и оба его
     * случая живут в доме (docs/architecture/tenant-and-exchange.md
     * §«Торговая строка называет счёт, и радиусы читаются от него»).
     *
     * <p>Пишется приёмником создания <b>из контекста вызова</b>, не из
     * тела команды.
     */
    @Column(name = "tenant_internal_id", nullable = false, updatable = false)
    private String tenantInternalId;

    /**
     * Биржевой счёт, на котором стратегия торгует, — его идентичность, а
     * не ключ чьей-либо базы: числовые ключи баз границу сервиса не
     * пересекают (docs/architecture/data-ownership.md §Идентификаторы).
     */
    @Column(name = "exchange_account_internal_id", nullable = false, updatable = false)
    private String exchangeAccountInternalId;

    /** Инструмент стратегии — его идентичность. */
    @Column(name = "instrument_internal_id", nullable = false, updatable = false)
    private String instrumentInternalId;

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
