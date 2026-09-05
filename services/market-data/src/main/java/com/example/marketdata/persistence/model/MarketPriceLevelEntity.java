package com.example.marketdata.persistence.model;

import com.example.marketdata.util.Constants;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Persistence-проекция ценового уровня структуры (таблица
 * market_price_levels). Без родителя смысла не имеет — живёт и умирает
 * вместе со строкой структуры.
 */
@Getter
@Setter
@Entity
@Table(name = "market_price_levels")
public class MarketPriceLevelEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "price", nullable = false, precision = Constants.Price.PRECISION, scale = Constants.Price.SCALE)
    private BigDecimal price;

    @Column(name = "detected_at")
    private OffsetDateTime detectedAt;

    @Column(name = "confirmed_at")
    private OffsetDateTime confirmedAt;
}
