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
 * Строка реестра конфигураций структуры рынка (таблица
 * market_structure_configs): уникальная конфигурация расчёта (timeframe +
 * canonical-params) с собственным id. Вид расчёта структуры один
 * (MarketStructureResolver выводит Type как выход), поэтому
 * type-дискриминатора в идентичности нет. Результаты MarketStructure
 * ссылаются по config_id. UNIQUE по канонической форме — во Flyway. См.
 * docs/decisions/market-data-result-identity-keying.md.
 */
@Getter
@Setter
@Entity
@Table(name = "market_structure_configs")
public class MarketStructureConfigEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "timeframe", nullable = false, updatable = false)
    private String timeframe;

    @Column(name = "params_canonical", nullable = false, updatable = false)
    private String paramsCanonical;
}
