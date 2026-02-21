package com.example.tradingbot.domain.model.entity;

import com.example.tradingbot.client.model.okx.AlgoOrderResponse;
import com.example.tradingbot.rest.model.request.CreateAlgoOrderRequest;
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

import java.time.OffsetDateTime;
import java.util.UUID;

import static com.example.tradingbot.util.Constant.Status.AlgoOrder.ALGO_ORDER_STATUS_CREATED;
import static com.example.tradingbot.util.Constant.Status.AlgoOrder.ALGO_ORDER_STATUS_IN_PROGRESS;
import static com.example.tradingbot.util.NumberUtils.parseOffsetDateTimeFromMillisSafe;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "algo_order", uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_algo_order_exchange_instr_client_algo_order",
                columnNames = {"exchange_id", "instrument_id", "client_algo_order_id"}
        )
})
public class AlgoOrderEntity extends AuditableEntity {

    /**
     * Внутренний идентификатор algo-ордера.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    /**
     * Идентификатор инструмента algo-ордера.
     */
    @Column(name = "instrument_id", nullable = false, updatable = false, insertable = false)
    private Long instrumentId;

    /**
     * Ссылка на инструмент, к которому относится algo-ордер.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instrument_id", nullable = false)
    private InstrumentEntity instrument;

    /**
     * Клиентский идентификатор algo-ордера.
     */
    @Column(name = "client_algo_order_id", nullable = false)
    private String internalOrderId;

    /**
     * Идентификатор algo-ордера на бирже.
     */
    @Column(name = "exchange_algo_order_id")
    private String externalId;

    /**
     * Текущий внутренний статус algo-ордера.
     */
    @Column(name = "status", nullable = false)
    private String status;

    /**
     * Тип algo-ордера.
     */
    @Column(name = "algo_type")
    private String type;

    /**
     * Состояние algo-ордера на стороне биржи.
     */
    @Column(name = "state")
    private String externalStatus;

    /**
     * Объём algo-ордера.
     */
    @Column(name = "sz")
    private String size;

    /**
     * Триггерная цена активации algo-ордера.
     */
    @Column(name = "trigger_px")
    private String triggerPrice;

    /**
     * Цена выставляемого ордера после срабатывания триггера.
     */
    @Column(name = "ord_px")
    private String orderPrice;

    /**
     * Триггерная цена take-profit.
     */
    @Column(name = "tp_trigger_px")
    private String takeProfitTriggerPrice;

    /**
     * Цена ордера для take-profit.
     */
    @Column(name = "tp_ord_px")
    private String takeProfitOrderPrice;

    /**
     * Триггерная цена stop-loss.
     */
    @Column(name = "sl_trigger_px")
    private String stopLossTriggerPrice;

    /**
     * Цена ордера для stop-loss.
     */
    @Column(name = "sl_ord_px")
    private String stopLossOrderPrice;

    /**
     * Коэффициент callback для trailing-механики.
     */
    @Column(name = "callback_ratio")
    private String callbackRatio;

    /**
     * Абсолютный шаг callback для trailing-механики.
     */
    @Column(name = "callback_spread")
    private String callbackStep;

    /**
     * Время создания algo-ордера на бирже в UTC миллисекундах.
     */
    @Column(name = "c_time")
    private OffsetDateTime exchangeCreatedAt;

    /**
     * Время обновления algo-ордера на бирже в UTC миллисекундах.
     */
    @Column(name = "u_time")
    private OffsetDateTime exchangeModifiedAt;

    public void initOnCreate(InstrumentEntity instrument, CreateAlgoOrderRequest request) {
        setInstrument(instrument);
        setInternalOrderId(UUID.randomUUID().toString());
        setStatus(ALGO_ORDER_STATUS_CREATED);
        setType(request.getType());
        setSize(request.getSize());
        setTriggerPrice(request.getTriggerPrice());
        setOrderPrice(request.getOrderPrice());
    }

    public void applyAlgoOrderResponse(AlgoOrderResponse responseOrder) {
        setExternalId(responseOrder.getAlgoId());
        setExternalStatus(responseOrder.getState());
        setStatus(ALGO_ORDER_STATUS_IN_PROGRESS);
        setType(responseOrder.getOrdType());
        setSize(responseOrder.getSz());
        setTriggerPrice(responseOrder.getTriggerPx());
        setOrderPrice(responseOrder.getOrdPx());
        setTakeProfitTriggerPrice(responseOrder.getTpTriggerPx());
        setTakeProfitOrderPrice(responseOrder.getTpOrdPx());
        setStopLossTriggerPrice(responseOrder.getSlTriggerPx());
        setStopLossOrderPrice(responseOrder.getSlOrdPx());
        setCallbackRatio(responseOrder.getCallbackRatio());
        setCallbackStep(responseOrder.getCallbackSpread());
        setExchangeCreatedAt(parseOffsetDateTimeFromMillisSafe(responseOrder.getCTime()));
        setExchangeModifiedAt(parseOffsetDateTimeFromMillisSafe(responseOrder.getUTime()));
    }
}
