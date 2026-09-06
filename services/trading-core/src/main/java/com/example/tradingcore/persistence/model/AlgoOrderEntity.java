package com.example.tradingcore.persistence.model;

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
 * Строка отдельной условной заявки (таблица algo_orders) — защита транша,
 * стоящая самостоятельно, а не внутри ноги.
 *
 * <p>Дерево условия и перечень идентификаторов порождённых заявок лежат
 * навесом JSONB на этой же строке: разбор — забота
 * {@code RuntimeJsonConverter}, а не переноса полей
 * (docs/models/domain/core/AlgoOrder.md).
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

    @Column(name = "size", precision = 36, scale = 18)
    private BigDecimal size;

    @Column(name = "direction")
    private String direction;

    @Column(name = "position_reducing_only")
    private Boolean positionReducingOnly;

    /** Клиентский идентификатор замещённой заявки — цепочка замещений. */
    @Column(name = "replaces_internal_id")
    private String replacesInternalId;

    @Column(name = "external_status")
    private String externalStatus;

    @Column(name = "fail_code")
    private String failCode;

    @Column(name = "external_size", precision = 36, scale = 18)
    private BigDecimal externalSize;

    @Column(name = "external_price", precision = 36, scale = 18)
    private BigDecimal externalPrice;

    @Column(name = "external_trigger_time")
    private Instant externalTriggerTime;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "linked_order_external_ids")
    private String linkedOrderExternalIds;
}
