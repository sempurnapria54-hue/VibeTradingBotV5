package com.example.tradingbot.persistence.model;

import jakarta.persistence.*;
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
        name = "attached_stop_losses",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_internal_id",
                        columnNames = {"internal_id"}
                )
        }
)
public class AttachedStopLossEntity extends AuditableEntity {

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
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    /**
     * Внутренний тип algo-ордера.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private Type type;

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

    public enum Type {
        ATTACHED_STOP_LOSS,
    }

    public enum Status {
        /**
         * Запись создана локально, ещё не отправляли
         */
        CREATED,
        /**
         * Запрос на attach ушёл/принят (или внешний attached id получен)
         */
        ATTACHED,
        /**
         * SL реально активен на бирже (после fill)
         */
        ACTIVE,
        /**
         * Отменён/сработал
         */
        CLOSED,
        /**
         * Не удалось создать/обновить
         */
        FAILED
    }

}

