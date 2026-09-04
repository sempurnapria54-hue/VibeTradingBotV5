package com.example.tradingbot.persistence.model.order;

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
 * Persistence-проекция {@link com.example.tradingbot.domain.model.core.order.Order}
 * (таблица orders). Attached protection — дочерние строки
 * attached_algo_orders по order_id (грузятся отдельно DataService, не
 * cascade-коллекция). Enum'ы хранятся строкой.
 */
@Getter
@Setter
@Entity
@Table(name = "orders")
public class OrderEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "deal_id", nullable = false)
    private Long dealId;

    @Column(name = "deal_tranche_id")
    private Long dealTrancheId;

    @Column(name = "internal_id", nullable = false, updatable = false)
    private String internalId;

    @Column(name = "external_id")
    private String externalId;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "close_reason")
    private String closeReason;

    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "side")
    private String side;

    @Column(name = "external_status")
    private String externalStatus;

    @Column(name = "price")
    private BigDecimal price;

    @Column(name = "size")
    private BigDecimal size;

    @Column(name = "accumulated_fill_size")
    private BigDecimal accumulatedFillSize;

    @Column(name = "average_price")
    private BigDecimal averagePrice;

    @Column(name = "fee")
    private BigDecimal fee;

    @Column(name = "position_reducing_only")
    private Boolean positionReducingOnly;

    @Column(name = "replaces_internal_id")
    private String replacesInternalId;

    @Column(name = "position_id")
    private Long positionId;

    @Column(name = "planned_entry_price")
    private BigDecimal plannedEntryPrice;

    @Column(name = "planned_size_contracts")
    private BigDecimal plannedSizeContracts;

    @Column(name = "planned_risk_amount")
    private BigDecimal plannedRiskAmount;

    @Column(name = "planned_risk_currency")
    private String plannedRiskCurrency;

    @Column(name = "planned_contract_value")
    private BigDecimal plannedContractValue;

    @Column(name = "planned_stop_price")
    private BigDecimal plannedStopPrice;
}
