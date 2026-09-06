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
 * Строка встроенной защиты ноги (таблица attached_algo_orders, FK
 * {@code order_id}).
 *
 * <p>У защиты своя идентичность и свой статус: на площадке она
 * материализуется самостоятельной условной заявкой при непустом наливе
 * родителя и переживает его терминал
 * (docs/models/domain/core/Order.md §«Встроенная защита»).
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

    /** Идентификатор защиты в составе родителя — не equals материализованному. */
    @Column(name = "external_attached_id")
    private String externalAttachedId;

    /** Идентификатор материализованной условной заявки площадки. */
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

    @Column(name = "external_type")
    private String externalType;

    /** Код отказа источника — операнд разбора тропы потери покрытия. */
    @Column(name = "fail_code")
    private String failCode;

    /** Ценовая база триггера; пусто — эхо не добыто, а не «базы нет». */
    @Column(name = "trigger_price_type")
    private String triggerPriceType;

    @Column(name = "size", precision = 36, scale = 18)
    private BigDecimal size;

    @Column(name = "stop_loss_trigger_price", precision = 36, scale = 18)
    private BigDecimal stopLossTriggerPrice;
}
