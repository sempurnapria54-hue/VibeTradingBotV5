package com.example.tradingbot.domain.model.entity;

import com.example.tradingbot.client.model.okx.OrderResponse;
import com.example.tradingbot.rest.model.request.CreateOrderRequest;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

import static com.example.tradingbot.util.Constant.Status.Order.ORDER_STATUS_CREATED;
import static com.example.tradingbot.util.Constant.Status.Order.ORDER_STATUS_IN_PROGRESS;
import static com.example.tradingbot.util.NumberUtils.parseOffsetDateTimeFromMillisSafe;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "\"order\"", uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_order_exchange_instr_client_order",
                columnNames = {"exchange_id", "instrument_id", "client_order_id"}
        )
})
public class OrderEntity extends AuditableEntity {

    /**
     * Внутренний идентификатор ордера.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    /**
     * Идентификатор инструмента ордера.
     */
    @Column(name = "instrument_id", nullable = false, updatable = false)
    private Long instrumentId;

    /**
     * Клиентский идентификатор ордера.
     */
    @Column(name = "client_order_id", nullable = false)
    private String internalId;

    /**
     * Идентификатор ордера на бирже.
     */
    @Column(name = "exchange_order_id")
    private String externalId;

    /**
     * Текущий внутренний статус ордера.
     */
    @Column(name = "status", nullable = false)
    private String status;

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
    @Column(name = "state")
    private String externalStatus;

    /**
     * Цена ордера.
     */
    @Column(name = "px")
    private String price;

    /**
     * Объём ордера.
     */
    @Column(name = "sz")
    private String size;

    /**
     * Накопленный исполненный объём.
     */
    @Column(name = "fill_sz")
    private String accumulatedFillSize;

    /**
     * Средняя цена исполнения.
     */
    @Column(name = "avg_px")
    private String averagePrice;

    /**
     * Комиссия по ордеру.
     */
    @Column(name = "fee")
    private String fee;

    /**
     * Время создания ордера на бирже в UTC миллисекундах.
     */
    @Column(name = "c_time")
    private OffsetDateTime exchangeCreatedAt;

    /**
     * Время последнего обновления ордера на бирже в UTC миллисекундах.
     */
    @Column(name = "u_time")
    private OffsetDateTime exchangeModifiedAt;

    public void initOnCreate(InstrumentEntity instrument, CreateOrderRequest request) {
        setInstrumentId(instrument.getId());
        setInternalId(UUID.randomUUID().toString());
        setStatus(ORDER_STATUS_CREATED);
        setSide(request.getSide());
        setType(request.getType());
        setSize(request.getSize());
        setPrice(request.getPrice());
    }

    public void applyOrderResponse(OrderResponse responseOrder) {
        setExternalId(responseOrder.getOrdId());
        setExternalStatus(responseOrder.getState());
        setStatus(ORDER_STATUS_IN_PROGRESS);
        setSide(responseOrder.getSide());
        setType(responseOrder.getOrdType());
        setPrice(responseOrder.getPx());
        setSize(responseOrder.getSz());
        setAccumulatedFillSize(responseOrder.getAccFillSz());
        setAveragePrice(responseOrder.getAvgPx());
        setFee(responseOrder.getFee());
        setExchangeCreatedAt(parseOffsetDateTimeFromMillisSafe(responseOrder.getcTime()));
        setExchangeModifiedAt(parseOffsetDateTimeFromMillisSafe(responseOrder.getuTime()));
    }
}
