package com.example.tradingbot.persistence.model;

import com.example.tradingbot.domain.model.okxproxy.Order;
import com.example.tradingbot.domain.model.trading.CreateOrderRequest;
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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "instrument_id", nullable = false, updatable = false, insertable = false)
    private Long instrumentId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instrument_id", nullable = false)
    private InstrumentEntity instrument;

    @Column(name = "client_order_id", nullable = false, length = CLIENT_ORDER_ID_LENGTH)
    private String clientOrderId;

    @Column(name = "exchange_order_id", length = EXCHANGE_ORDER_ID_LENGTH)
    private String exchangeOrderId;

    @Column(name = "status", nullable = false, length = STATUS_LENGTH)
    private String status;

    @Column(name = "type", length = TYPE_LENGTH)
    private String type;

    @Column(name = "side", length = SIDE_LENGTH)
    private String side;

    @Column(name = "state", length = 32)
    private String state;

    @Column(name = "ord_type", length = 32)
    private String ordType;

    @Column(name = "px", length = 64)
    private String px;

    @Column(name = "sz", length = 64)
    private String sz;

    @Column(name = "fill_sz", length = 64)
    private String fillSz;

    @Column(name = "avg_px", length = 64)
    private String avgPx;

    @Column(name = "fee", length = 64)
    private String fee;

    @Column(name = "c_time")
    private Long cTime;

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

    public void applyOrderResponse(Order responseOrder) {
        setExchangeOrderId(responseOrder.getOrderId());
        setState(responseOrder.getState());
        setStatus(ORDER_STATUS_IN_PROGRESS);
        setSide(responseOrder.getSide());
        setOrdType(responseOrder.getOrderType());
        setPx(responseOrder.getPrice());
        setSz(responseOrder.getSize());
        setFillSz(responseOrder.getAccumulatedFillSize());
        setAvgPx(responseOrder.getAveragePrice());
        setFee(responseOrder.getFee());
        setCTime(parseLongSafe(responseOrder.getCreateTime()));
        setUTime(parseLongSafe(responseOrder.getUpdateTime()));
    }
}
