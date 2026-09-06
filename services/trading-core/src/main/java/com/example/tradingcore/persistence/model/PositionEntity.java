package com.example.tradingcore.persistence.model;

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
 * Строка ЭПИЗОДА позиции сделки (таблица positions): одна биржевая
 * позиция — одна строка, живой эпизод не более одного, закрытые остаются.
 *
 * <p>Эпизод опознаётся тройкой «сделка, идентификатор площадки, момент
 * открытия»: одного идентификатора мало — источник переиспользует его у
 * переоткрытой позиции (docs/models/domain/core/Position.md). Собственного
 * {@code internalId} у эпизода нет.
 *
 * <p>Хранит сопровождение живого риска и положение закрытия эпизода;
 * заголовочный результат сделки — у {@link DealEntity}.
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

    @Column(name = "external_size", precision = 36, scale = 18)
    private BigDecimal externalSize;

    @Column(name = "external_average_entry_price", precision = 36, scale = 18)
    private BigDecimal externalAverageEntryPrice;

    @Column(name = "external_mark_price", precision = 36, scale = 18)
    private BigDecimal externalMarkPrice;

    @Column(name = "external_liquidation_price", precision = 36, scale = 18)
    private BigDecimal externalLiquidationPrice;

    @Column(name = "external_margin", precision = 36, scale = 18)
    private BigDecimal externalMargin;

    @Column(name = "external_unrealized_profit", precision = 36, scale = 18)
    private BigDecimal externalUnrealizedProfit;

    @Column(name = "external_realized_profit", precision = 36, scale = 18)
    private BigDecimal externalRealizedProfit;

    /** Правый операнд первой пары сверки — результат до вычета удержаний. */
    @Column(name = "external_realized_profit_gross", precision = 36, scale = 18)
    private BigDecimal externalRealizedProfitGross;

    @Column(name = "external_result_currency")
    private String externalResultCurrency;

    @Column(name = "external_close_average_price", precision = 36, scale = 18)
    private BigDecimal externalCloseAveragePrice;

    @Column(name = "external_close_type")
    private String externalCloseType;

    /** Правый операнд второй пары сверки. */
    @Column(name = "external_fee", precision = 36, scale = 18)
    private BigDecimal externalFee;

    /** Правый операнд третьей пары сверки. */
    @Column(name = "external_funding_cost", precision = 36, scale = 18)
    private BigDecimal externalFundingCost;

    /** Правый операнд четвёртой пары сверки. */
    @Column(name = "external_liquidation_penalty", precision = 36, scale = 18)
    private BigDecimal externalLiquidationPenalty;
}
