package com.example.tradingbot.persistence.model.position;

import com.example.tradingbot.persistence.model.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/**
 * Persistence-проекция {@link com.example.tradingbot.domain.model.core.position.Position}
 * (таблица positions, ≤1 на deal). Хранит данные сопровождения live-risk;
 * итоговый PnL — у Deal. Enum'ы хранятся строкой.
 */
@Getter
@Setter
@Entity
@Table(name = "positions")
public class PositionEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "deal_id", nullable = false)
    private Long dealId;

    @Column(name = "external_id")
    private String externalId;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "close_reason")
    private String closeReason;

    @Column(name = "direction")
    private String direction;

    @Column(name = "external_size")
    private BigDecimal externalSize;

    @Column(name = "external_average_entry_price")
    private BigDecimal externalAverageEntryPrice;

    @Column(name = "external_mark_price")
    private BigDecimal externalMarkPrice;

    @Column(name = "external_liquidation_price")
    private BigDecimal externalLiquidationPrice;

    @Column(name = "external_margin")
    private BigDecimal externalMargin;

    @Column(name = "external_unrealized_profit")
    private BigDecimal externalUnrealizedProfit;
}
