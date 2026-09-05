package com.example.marketdata.persistence.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Persistence-проекция заказанной идентичности вычисления структуры рынка
 * (таблица market_structure_configs).
 *
 * <p><b>Идентичности входов входят в идентичность результата:</b> два
 * вычисления с разными ER/ATR дают разные строки, иначе последнее
 * записанное затирало бы чужое
 * (docs/models/domain/other/MarketStructure.md).
 */
@Getter
@Setter
@Entity
@Table(name = "market_structure_configs")
public class MarketStructureConfigEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "internal_id", nullable = false, updatable = false)
    private String internalId;

    @Column(name = "timeframe", nullable = false, updatable = false)
    private String timeframe;

    @Column(name = "params_canonical", nullable = false, updatable = false)
    private String paramsCanonical;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "params", nullable = false, updatable = false)
    private String params;

    @Column(name = "efficiency_ratio_config_id", updatable = false)
    private Long efficiencyRatioConfigId;

    @Column(name = "atr_config_id", updatable = false)
    private Long atrConfigId;
}
