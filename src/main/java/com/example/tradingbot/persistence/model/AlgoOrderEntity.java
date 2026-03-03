package com.example.tradingbot.persistence.model;

import com.example.tradingbot.client.model.okx.response.AlgoOrderResponse;
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
@Table(name = "algo_orders", uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_internal_id",
                columnNames = {"internal_id"}
        )
})
public class AlgoOrderEntity extends AuditableEntity {

    /**
     * Внутренний идентификатор algo-ордера.
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
     * Межсервисный идентификатор algo-ордера.
     */
    @Column(name = "internal_id", nullable = false, updatable = false)
    private String internalId;

    /**
     * Идентификатор algo-ордера на бирже.
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
     * Для Type.CONDITIONAL_MARKET_TP, CONDITIONAL_MARKET_FULL
     * Триггерная цена take-profit.
     */
    @Column(name = "tp_trigger_price", precision = PRICE_PRECISION, scale = PRICE_SCALE)
    private BigDecimal takeProfitTriggerPrice;

    /**
     * Для Type.CONDITIONAL_MARKET_SL, CONDITIONAL_MARKET_FULL
     * Триггерная цена stop-loss.
     */
    @Column(name = "sl_trigger_price", precision = PRICE_PRECISION, scale = PRICE_SCALE)
    private BigDecimal stopLossTriggerPrice;

    /**
     * Для Type.TRAILING_MARKET_PERCENTS,
     * Коэффициент callback для trailing-механики.
     * Процент “отката” от экстремума. (Пример: 0.01 = 1%)
     */
    @Column(name = "trailing_fallen_percents", precision = PRICE_PRECISION, scale = PRICE_SCALE)
    private BigDecimal trailingFallenPercents;

    /**
     * Для Type.TRAILING_MARKET_VALUE
     * Абсолютный шаг callback для trailing-механики.
     */
    @Column(name = "trailing_fallen_absolute_value", precision = PRICE_PRECISION, scale = PRICE_SCALE)
    private BigDecimal trailingFallenAbsoluteValue;

    public void applyAlgoOrderResponse(AlgoOrderResponse responseOrder) {
        setExternalId(responseOrder.getAlgoId());
        setExternalStatus(responseOrder.getState());
        setStatus(Status.PENDING);
        setExternalType(responseOrder.getOrdType());
//        setSize(responseOrder.getSz());
//        setTakeProfitTriggerPrice(responseOrder.getTpTriggerPx());
//        setStopLossTriggerPrice(responseOrder.getSlTriggerPx());
//        setCallbackRatio(responseOrder.getCallbackRatio());
//        setCallbackStep(responseOrder.getCallbackSpread());
    }

    public enum Type {
        /**
         * Conditional SL (stop-loss) — срабатывает по slTriggerPrice.
         * После срабатывания закрываем позицию по рынку (slOrdPx = -1).
         * Используем только поле slTriggerPrice, takeProfitTriggerPrice = null, callback* = null.
         */
        CONDITIONAL_MARKET_SL,

        /**
         * Conditional TP (take-profit) — срабатывает по takeProfitTriggerPrice.
         * После срабатывания закрываем позицию по рынку (tpOrdPx = -1).
         * Используем только поле takeProfitTriggerPrice, stopLossTriggerPrice = null, callback* = null.
         */
        CONDITIONAL_MARKET_TP,

        /**
         * Conditional FULL (TP + SL одновременно) — один algo-ордер содержит и TP, и SL,
         * оба исполняются по рынку после срабатывания соответствующего триггера (tp/sl OrdPx = -1).
         * Используем takeProfitTriggerPrice + stopLossTriggerPrice, callback* = null.
         * <p>
         * Примечание: поддержка TP+SL в одном conditional зависит от режима/ограничений биржи.
         * Если на бирже есть ограничения — проще хранить как 2 отдельных algo-ордера (TP и SL).
         */
        CONDITIONAL_MARKET_FULL,

        /**
         * Trailing stop по проценту — ордер следует за экстремумом, срабатывает при откате на callbackRatio.
         * После срабатывания закрываем позицию по рынку.
         * Используем callbackRatio, callbackStep = null, tp/sl триггеры = null.
         */
        TRAILING_MARKET_PERCENTS,

        /**
         * Trailing stop по абсолютному шагу — ордер следует за экстремумом, срабатывает при откате на callbackStep.
         * После срабатывания закрываем позицию по рынку.
         * Используем callbackStep, callbackRatio = null, tp/sl триггеры = null.
         */
        TRAILING_MARKET_VALUE
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
         * Отменён/сработал
         */
        CLOSED,
        /**
         * Не удалось создать/обновить
         */
        FAILED
    }
}
