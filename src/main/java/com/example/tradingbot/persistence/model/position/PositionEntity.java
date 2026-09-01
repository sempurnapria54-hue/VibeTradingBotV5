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
 * (таблица positions — строка на ЭПИЗОД, живой не более одного).
 * Хранит данные сопровождения живого риска и положение закрытия
 * эпизода; заголовочное число сделки — у Deal. Уникальна тройка
 * (deal_id, external_id, external_created_at). Enum'ы хранятся строкой.
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

    @Column(name = "external_realized_profit")
    private BigDecimal externalRealizedProfit;

    @Column(name = "external_result_currency")
    private String externalResultCurrency;

    @Column(name = "external_close_average_price")
    private BigDecimal externalCloseAveragePrice;

    @Column(name = "external_close_type")
    private String externalCloseType;

    @Column(name = "external_realized_profit_gross")
    private BigDecimal externalRealizedProfitGross;

    @Column(name = "external_fee")
    private BigDecimal externalFee;

    @Column(name = "external_funding_cost")
    private BigDecimal externalFundingCost;

    @Column(name = "external_liquidation_penalty")
    private BigDecimal externalLiquidationPenalty;
}
