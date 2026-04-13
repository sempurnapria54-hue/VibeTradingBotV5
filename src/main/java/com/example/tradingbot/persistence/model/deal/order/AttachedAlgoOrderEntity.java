package com.example.tradingbot.persistence.model.deal.order;

import com.example.tradingbot.persistence.model.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

import static com.example.tradingbot.util.Constant.Service.PRICE_PRECISION;
import static com.example.tradingbot.util.Constant.Service.PRICE_SCALE;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "attached_algo_orders",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_internal_id",
                        columnNames = {"internal_id"}
                )
        }
)
public class AttachedAlgoOrderEntity extends AuditableEntity {

    /**
     * Внутренний идентификатор.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    /**
     * Ссылка на ордер.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    private OrderEntity order;

    /**
     * Межсервисный идентификатор.
     */
    @Column(name = "internal_id", nullable = false, updatable = false)
    private String internalId;

    /**
     * Идентификатор привязанного algo-ордера на бирже, пока он не выполнен.
     */
    @Column(name = "external_attached_id")
    private String externalAttachedId;

    /**
     * Идентификатор algo-ордера на бирже, когда он уже создан на бирже.
     */
    @Column(name = "external_id")
    private String externalId;

    /**
     * Текущий внутренний статус.
     */
    @Column(name = "status", nullable = false)
    private String status;

    /**
     * Внутренний тип algo-ордера.
     */
    @Column(name = "type", nullable = false)
    private String type;

    /**
     * Состояние algo-ордера на стороне биржи.
     */
    @Column(name = "external_status")
    private String externalStatus;

    /**
     * Биржевой тип algo-ордера.
     */
    @Column(name = "external_type")
    private String externalType;

    /**
     * Объём algo-ордера.
     */
    @Column(name = "size", nullable = false, precision = PRICE_PRECISION, scale = PRICE_SCALE)
    private BigDecimal size;

    /**
     * Триггерная цена stop-loss. (Всегда заходим по рынку в slOrdPx)
     */
    @Column(name = "sl_trigger_price", nullable = false, precision = PRICE_PRECISION, scale = PRICE_SCALE)
    private BigDecimal stopLossTriggerPrice;

}

