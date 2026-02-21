package com.example.tradingbot.domain.model.entity;

import com.example.tradingbot.client.model.okx.OrderResponse;
import com.example.tradingbot.rest.model.request.order.CreateOrderRequest;
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

import java.util.UUID;

import static com.example.tradingbot.util.Constant.Status.Order.ORDER_STATUS_CREATED;
import static com.example.tradingbot.util.Constant.Status.Order.ORDER_STATUS_IN_PROGRESS;
import static com.example.tradingbot.util.NumberUtils.parseLongSafe;

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

    public static final int CLIENT_ORDER_ID_LENGTH = 128;
    public static final int EXCHANGE_ORDER_ID_LENGTH = 128;
    public static final int STATUS_LENGTH = 50;
    public static final int TYPE_LENGTH = 50;
    public static final int SIDE_LENGTH = 20;

    /** Внутренний идентификатор ордера. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    /** Идентификатор инструмента ордера. */
    @Column(name = "instrument_id", nullable = false, updatable = false, insertable = false)
    private Long instrumentId;

    /** Ссылка на инструмент, к которому относится ордер. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instrument_id", nullable = false)
    private InstrumentEntity instrument;

    /** Клиентский идентификатор ордера. */
    @Column(name = "client_order_id", nullable = false, length = CLIENT_ORDER_ID_LENGTH)
    private String clientOrderId;

    /** Идентификатор ордера на бирже. */
    @Column(name = "exchange_order_id", length = EXCHANGE_ORDER_ID_LENGTH)
    private String exchangeOrderId;

    /** Текущий внутренний статус ордера. */
    @Column(name = "status", nullable = false, length = STATUS_LENGTH)
    private String status;

    /** Тип ордера в бизнес-терминах. */
    @Column(name = "type", length = TYPE_LENGTH)
    private String type;

    /** Сторона ордера (buy/sell). */
    @Column(name = "side", length = SIDE_LENGTH)
    private String side;

    /** Состояние ордера на стороне биржи. */
    @Column(name = "state", length = 32)
    private String state;

    /** Тип ордера на бирже (ordType). */
    @Column(name = "ord_type", length = 32)
    private String ordType;

    /** Цена ордера. */
    @Column(name = "px", length = 64)
    private String px;

    /** Объём ордера. */
    @Column(name = "sz", length = 64)
    private String sz;

    /** Накопленный исполненный объём. */
    @Column(name = "fill_sz", length = 64)
    private String fillSz;

    /** Средняя цена исполнения. */
    @Column(name = "avg_px", length = 64)
    private String avgPx;

    /** Комиссия по ордеру. */
    @Column(name = "fee", length = 64)
    private String fee;

    /** Время создания ордера на бирже в UTC миллисекундах. */
    @Column(name = "c_time")
    private Long cTime;

    /** Время последнего обновления ордера на бирже в UTC миллисекундах. */
    @Column(name = "u_time")
    private Long uTime;

    public void initOnCreate(InstrumentEntity instrument, CreateOrderRequest request) {
        setInstrument(instrument);
        setClientOrderId(UUID.randomUUID().toString());
        setStatus(ORDER_STATUS_CREATED);
        setSide(request.getSide());
        setOrdType(request.getType());
        setSz(request.getSz());
        setPx(request.getPx());
    }

    public void applyOrderResponse(OrderResponse responseOrder) {
        setExchangeOrderId(responseOrder.getOrdId());
        setState(responseOrder.getState());
        setStatus(ORDER_STATUS_IN_PROGRESS);
        setSide(responseOrder.getSide());
        setOrdType(responseOrder.getOrdType());
        setPx(responseOrder.getPx());
        setSz(responseOrder.getSz());
        setFillSz(responseOrder.getAccFillSz());
        setAvgPx(responseOrder.getAvgPx());
        setFee(responseOrder.getFee());
        setCTime(parseLongSafe(responseOrder.getCTime()));
        setUTime(parseLongSafe(responseOrder.getUTime()));
    }
}
