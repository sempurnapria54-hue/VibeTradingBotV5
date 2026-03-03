package com.example.tradingbot.domain.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class AlgoOrder extends Auditable {

    /**
     * Внутренний идентификатор algo-ордера.
     */
    private Long id;

    /**
     * Идентификатор сделки.
     */
    private Long dealId;

    /**
     * Межсервисный идентификатор algo-ордера.
     */
    private String internalId;

    /**
     * Идентификатор algo-ордера на бирже.
     */
    private String externalId;

    /**
     * Текущий внутренний статус.
     */
    private Status status;

    /**
     * Внутренний тип algo-ордера.
     */
    private Type type;

    /**
     * Состояние algo-ордера на стороне биржи.
     */
    private String externalStatus;

    /**
     * Биржевой тип algo-ордера.
     */
    private String externalType;

    /**
     * Объём algo-ордера.
     */
    private BigDecimal size;

    /**
     * Для Type.CONDITIONAL_MARKET_TP, CONDITIONAL_MARKET_FULL
     * Триггерная цена take-profit.
     */
    private BigDecimal takeProfitTriggerPrice;

    /**
     * Для Type.CONDITIONAL_MARKET_SL, CONDITIONAL_MARKET_FULL
     * Триггерная цена stop-loss.
     */
    private BigDecimal stopLossTriggerPrice;

    /**
     * Для Type.TRAILING_MARKET_PERCENTS,
     * Коэффициент callback для trailing-механики.
     * Процент “отката” от экстремума. (Пример: 0.01 = 1%)
     */
    private BigDecimal trailingFallenPercents;

    /**
     * Для Type.TRAILING_MARKET_VALUE
     * Абсолютный шаг callback для trailing-механики.
     */
    private BigDecimal trailingFallenAbsoluteValue;

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
