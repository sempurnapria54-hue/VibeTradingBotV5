package com.example.strategies.persistence.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

/**
 * Объявление транша (таблица strategy_tranches): сколько экземпляров
 * материализовать и с каким смещением уровня.
 *
 * <p><b>У числа уровней умолчания нет</b>, и это держится схемой: пустое
 * место мажорировалось бы единицей, то есть в разрешающую сторону
 * неравенства статического запаса (docs/rules/risk-policy.md). Шаг
 * уровня осмыслен только у сетки и обязателен у неё.
 */
@Getter
@Setter
@Entity
@Table(name = "strategy_tranches")
public class StrategyTrancheEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "strategy_detail_id", nullable = false, updatable = false)
    private StrategyDetailEntity detail;

    @Column(name = "key", nullable = false, updatable = false)
    private String key;

    @Column(name = "level_count", nullable = false)
    private Integer levelCount;

    @Column(name = "level_step", precision = 36, scale = 18)
    private BigDecimal levelStep;

    @Column(name = "position_reopen_allowed")
    private Boolean positionReopenAllowed;

    @OneToMany(mappedBy = "tranche", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<StrategyStepEntity> steps;
}
