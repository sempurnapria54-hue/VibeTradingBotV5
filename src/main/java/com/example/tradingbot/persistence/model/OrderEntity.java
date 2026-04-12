package com.example.tradingbot.persistence.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

import static com.example.tradingbot.util.Constant.Service.PRICE_PRECISION;
import static com.example.tradingbot.util.Constant.Service.PRICE_SCALE;

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
     * Идентификатор сделки-владельца.
     */
    @Column(name = "deal_id", nullable = false, updatable = false)
    private Long dealId;

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
    @Column(name = "status", nullable = false)
    private String status;

    /**
     * Причина закрытия ордера.
     */
    @Column(name = "close_reason")
    private String closeReason;

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
    private List<AttachedAlgoOrderEntity> attachedAlgoOrders;

}
