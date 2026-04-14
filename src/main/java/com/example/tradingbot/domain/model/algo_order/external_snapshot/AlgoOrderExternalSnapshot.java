package com.example.tradingbot.domain.model.algo_order.external_snapshot;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class AlgoOrderExternalSnapshot {

    /**
     * Клиентский идентификатор algo-ордера на бирже (algoClOrdId).
     */
    private String internalId;

    /**
     * Идентификатор algo-ордера на бирже (algoId).
     */
    private String externalId;

    /**
     * Биржевой тип algo-ордера (ordType): conditional | oco | move_order_stop | trigger ...
     */
    private String externalType;

    /**
     * Состояние algo-ордера на стороне биржи (state), например live/pause.
     */
    private String externalStatus;

    /**
     * Сторона algo-ордера на бирже (buy/sell).
     */
    private String externalDirection;

    /**
     * Сторона позиции на бирже (posSide): net | long | short.
     */
    private String externalPositionSide;

    /**
     * Внешний снимок условия algo-ордера.
     * Содержит только те вложенные поля, которые реально обновляются данными биржи.
     */
    private ConditionExternalSnapshot condition;

    @Getter
    @Setter
    public static class ConditionExternalSnapshot {

        /**
         * Внешний снимок trigger-части условия.
         */
        private TriggerExternalSnapshot trigger;

        /**
         * Внешний снимок trailing-части условия.
         */
        private TrailingExternalSnapshot trailing;
    }

    @Getter
    @Setter
    public static class TriggerExternalSnapshot {

        /**
         * Внешний снимок SL-триггера.
         */
        private TriggerPriceExternalSnapshot stopLoss;

        /**
         * Внешний снимок TP-триггера.
         */
        private TriggerPriceExternalSnapshot takeProfit;
    }

    @Getter
    @Setter
    public static class TriggerPriceExternalSnapshot {

        /**
         * Биржевой тип цены триггера (last/index/mark).
         */
        private String externalType;

        /**
         * Биржевое значение триггерной цены.
         */
        private BigDecimal externalValue;
    }

    @Getter
    @Setter
    public static class TrailingExternalSnapshot {

        /**
         * Внешний снимок цены активации trailing.
         * В OKX для activePx тип цены отдельно не приходит, поэтому обычно обновляется только значение.
         */
        private TriggerPriceExternalSnapshot activationPrice;

        /**
         * Текущее биржевое значение trailing (обычно moveTriggerPx).
         */
        private BigDecimal externalPrice;
    }
}
