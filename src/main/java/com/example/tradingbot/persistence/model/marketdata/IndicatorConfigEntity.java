package com.example.tradingbot.persistence.model.marketdata;

import com.example.tradingbot.persistence.model.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Строка реестра конфигураций индикатора (таблица indicator_configs):
 * уникальная конфигурация расчёта (тип + timeframe + canonical-params) с
 * собственным id. Результаты IndicatorValue ссылаются на неё по
 * config_id; одна конфигурация считается раз на инструмент и шарится
 * всеми настройками, которые её просят. UNIQUE по канонической форме —
 * во Flyway. См. docs/decisions/market-data-result-identity-keying.md.
 */
@Getter
@Setter
@Entity
@Table(name = "indicator_configs")
public class IndicatorConfigEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "indicator_type", nullable = false, updatable = false)
    private String indicatorType;

    @Column(name = "timeframe", nullable = false, updatable = false)
    private String timeframe;

    @Column(name = "params_canonical", nullable = false, updatable = false)
    private String paramsCanonical;
}
