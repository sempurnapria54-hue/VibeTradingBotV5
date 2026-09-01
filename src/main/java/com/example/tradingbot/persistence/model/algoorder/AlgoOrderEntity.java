package com.example.tradingbot.persistence.model.algoorder;

import com.example.tradingbot.persistence.model.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Persistence-проекция {@link com.example.tradingbot.domain.model.core.algo_order.AlgoOrder}
 * (таблица algo_orders). condition (дерево trigger/trailing) и
 * linked_order_external_ids — JSONB на этой строке. Enum'ы строкой.
 */
@Getter
@Setter
@Entity
@Table(name = "algo_orders")
public class AlgoOrderEntity extends AuditableEntity {

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

    @Column(name = "condition_type", nullable = false)
    private String conditionType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "condition")
    private String condition;

    @Column(name = "size")
    private BigDecimal size;

    @Column(name = "direction")
    private String direction;

    @Column(name = "position_reducing_only")
    private Boolean positionReducingOnly;

    @Column(name = "replaces_internal_id")
    private String replacesInternalId;

    @Column(name = "external_status")
    private String externalStatus;

    @Column(name = "fail_code")
    private String failCode;

    @Column(name = "external_size")
    private BigDecimal externalSize;

    @Column(name = "external_price")
    private BigDecimal externalPrice;

    @Column(name = "external_trigger_time")
    private Instant externalTriggerTime;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "linked_order_external_ids")
    private String linkedOrderExternalIds;
}
