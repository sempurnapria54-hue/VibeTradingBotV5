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
 * Persistence-проекция AttachedAlgoOrder (таблица attached_algo_orders,
 * FK order_id). Embedded protection parent order со своей identity и
 * статусом; в standalone AlgoOrder не материализуется. Enum'ы строкой.
 * См. docs/models/domain/core/Order.md (§AttachedAlgoOrder).
 */
@Getter
@Setter
@Entity
@Table(name = "attached_algo_orders")
public class AttachedAlgoOrderEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "internal_id", nullable = false, updatable = false)
    private String internalId;

    @Column(name = "external_attached_id")
    private String externalAttachedId;

    @Column(name = "external_id")
    private String externalId;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "close_reason")
    private String closeReason;

    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "external_status")
    private String externalStatus;

    /** Код отказа источника — операнд разбора тропы потери покрытия. */
    @Column(name = "fail_code")
    private String failCode;

    @Column(name = "external_type")
    private String externalType;

    /** Ценовая база триггера; nullable — пустое эхо есть недобытый факт. */
    @Column(name = "trigger_price_type")
    private String triggerPriceType;

    @Column(name = "size")
    private BigDecimal size;

    @Column(name = "stop_loss_trigger_price")
    private BigDecimal stopLossTriggerPrice;
}
