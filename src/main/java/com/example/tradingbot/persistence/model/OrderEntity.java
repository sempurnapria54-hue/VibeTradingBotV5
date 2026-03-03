package com.example.tradingbot.persistence.model;

import com.example.tradingbot.client.model.okx.response.OrderResponse;
import com.example.tradingbot.rest.model.request.CreateOrderRequest;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static com.example.tradingbot.util.Constant.Service.PRICE_PRECISION;
import static com.example.tradingbot.util.Constant.Service.PRICE_SCALE;
import static com.example.tradingbot.util.Constant.Status.Order.ORDER_STATUS_CREATED;
import static com.example.tradingbot.util.Constant.Status.Order.ORDER_STATUS_IN_PROGRESS;
import static com.example.tradingbot.util.NumberUtils.parseOffsetDateTimeFromMillisSafe;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "orders", uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_internal_id",
                columnNames = {"internal_id"}
        )
})
public class OrderEntity extends AuditableEntity {

    /**
     * Внутренний идентификатор ордера.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    /**
     * Идентификатор сделки.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "deal_id", nullable = false, updatable = false)
    private DealEntity deal;

    /**
     * Межсервисный идентификатор ордера.
     */
    @Column(name = "internal_id", nullable = false, updatable = false)
    private String internalId;

    /**
     * Идентификатор ордера на бирже.
     */
    @Column(name = "external_id")
    private String externalId;

    /**
     * Текущий внутренний статус ордера.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    /**
     * Тип ордера в бизнес-терминах.
     */
    @Column(name = "type")
    private String type;

    /**
     * Сторона ордера (buy/sell).
     */
    @Column(name = "side")
    private String side;

    /**
     * Состояние ордера на стороне биржи.
     */
    @Column(name = "external_status")
    private String externalStatus;

    /**
     * Цена ордера.
     */
    @Column(name = "price", precision = PRICE_PRECISION, scale = PRICE_SCALE)
    private BigDecimal price;

    /**
     * Объём ордера.
     */
    @Column(name = "size", nullable = false, precision = PRICE_PRECISION, scale = PRICE_SCALE)
    private BigDecimal size;

    /**
     * Накопленный исполненный объём.
     */
    @Column(name = "accumulated_fill_size", precision = PRICE_PRECISION, scale = PRICE_SCALE)
    private BigDecimal accumulatedFillSize;

    /**
     * Средняя цена исполнения.
     */
    @Column(name = "average_price", precision = PRICE_PRECISION, scale = PRICE_SCALE)
    private BigDecimal averagePrice;

    /**
     * Комиссия по ордеру.
     */
    @Column(name = "fee", precision = PRICE_PRECISION, scale = PRICE_SCALE)
    private BigDecimal fee;

    /**
     * Прикреплённый SL при создании.
     */
    @OneToMany(mappedBy = "order", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<AttachedStopLossEntity>attachedStopLossEntities;

    public void applyOrderResponse(OrderResponse responseOrder) {
        setExternalId(responseOrder.getOrdId());
        setExternalStatus(responseOrder.getState());
//        setStatus(ORDER_STATUS_IN_PROGRESS);
        setSide(responseOrder.getSide());
        setType(responseOrder.getOrdType());
//        setPrice(responseOrder.getPx());
//        setSize(responseOrder.getSz());
//        setAccumulatedFillSize(responseOrder.getAccFillSz());
//        setAveragePrice(responseOrder.getAvgPx());
//        setFee(responseOrder.getFee());
    }

    public enum Status {
        /**
         * Запись создана локально, ещё не отправляли
         */
        CREATED,
        /**
         * Отправили, но ещё не активен
         */
        PENDING,
        /**
         * Реально активен на бирже (после fill)
         */
        ACTIVE,
        /**
         * Полностью выполнен на бирже
         */
        COMPLETED,
        /**
         * Частично выполнен на бирже
         */
        PARTIALLY_COMPLETED,
        /**
         * Отменён
         */
        CLOSED,
        /**
         * Не удалось создать/обновить
         */
        FAILED
    }
}
