package com.example.marketdata.persistence.model;

import com.example.marketdata.util.Constants;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Persistence-проекция {@code MarketStructure} (таблица market_structures).
 * Ключ — идентичность вычисления плюс конец окна; при изменившейся
 * структуре пишется НОВАЯ строка (новый window_end_at), старая не
 * правится: ряд есть история, а не текущее значение.
 *
 * <p>Ценовые уровни — свои строки, а не JSONB: их несколько на структуру,
 * они адресуются по типу и читаются поштучно
 * (docs/rules/persistence-representation.md).
 */
@Getter
@Setter
@Entity
@Table(name = "market_structures")
public class MarketStructureEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "instrument_id", nullable = false, updatable = false)
    private Long instrumentId;

    @Column(name = "market_structure_config_id", nullable = false, updatable = false)
    private Long marketStructureConfigId;

    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "window_start_at", nullable = false)
    private OffsetDateTime windowStartAt;

    @Column(name = "window_end_at", nullable = false, updatable = false)
    private OffsetDateTime windowEndAt;

    @Column(name = "confirmed_at")
    private OffsetDateTime confirmedAt;

    @Column(name = "breakout_broken_level_type")
    private String breakoutBrokenLevelType;

    @Column(name = "breakout_direction")
    private String breakoutDirection;

    @Column(name = "breakout_level_price",
            precision = Constants.Price.PRECISION, scale = Constants.Price.SCALE)
    private BigDecimal breakoutLevelPrice;

    @Column(name = "breakout_confirmed_at")
    private OffsetDateTime breakoutConfirmedAt;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "market_structure_id", nullable = false)
    private List<MarketPriceLevelEntity> levels;
}
