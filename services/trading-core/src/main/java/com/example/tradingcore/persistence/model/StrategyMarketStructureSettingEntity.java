package com.example.tradingcore.persistence.model;

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
 * Настройка рыночной структуры из каталога стратегии (таблица
 * strategy_market_structure_settings). Индикаторы, от которых структура
 * зависит, адресуются ключами каталога, а не идентификаторами строк:
 * ключ переживает пересборку копии дерева.
 */
@Getter
@Setter
@Entity
@Table(name = "strategy_market_structure_settings")
public class StrategyMarketStructureSettingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "strategy_id", nullable = false, updatable = false)
    private StrategyEntity strategy;

    @Column(name = "key", nullable = false, updatable = false)
    private String key;

    @Column(name = "timeframe", nullable = false)
    private String timeframe;

    @Column(name = "efficiency_ratio_key")
    private String efficiencyRatioKey;

    @Column(name = "atr_key")
    private String atrKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "params", nullable = false)
    private String params;

    @Column(name = "destiny", nullable = false)
    private String destiny;

    @Column(name = "expiration_duration", nullable = false)
    private String expirationDuration;

    /**
     * Идентичность вычисления у {@code market-data}; пусто — потребность
     * ещё не объявлена. Пишет тик объявления потребности точечным
     * запросом, а не сохранением строки целиком: строка принадлежит копии
     * определения, и переписывание её целиком затирало бы копию
     * значениями, устаревшими на возраст прохода.
     */
    @Column(name = "computation_config_internal_id")
    private String computationConfigInternalId;
}
