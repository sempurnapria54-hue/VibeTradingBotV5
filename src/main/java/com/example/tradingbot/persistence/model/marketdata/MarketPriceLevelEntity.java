package com.example.tradingbot.persistence.model.marketdata;

import com.example.tradingbot.persistence.model.AuditableEntity;
import com.example.tradingbot.util.Constants;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Persistence-проекция MarketPriceLevel (таблица market_price_levels) —
 * ценовой уровень внутри MarketStructure. Контейнмент — bidirectional
 * @ManyToOne (FK market_structure_id выставляется на вставке, как в
 * дереве стратегии). См.
 * docs/models/domain/other/MarketStructure.md (§MarketPriceLevel).
 */
@Getter
@Setter
@Entity
@Table(name = "market_price_levels")
public class MarketPriceLevelEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "market_structure_id", nullable = false, updatable = false)
    private MarketStructureEntity structure;

    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "price", nullable = false, precision = Constants.Price.PRECISION, scale = Constants.Price.SCALE)
    private BigDecimal price;

    @Column(name = "detected_at")
    private OffsetDateTime detectedAt;

    @Column(name = "confirmed_at")
    private OffsetDateTime confirmedAt;
}
