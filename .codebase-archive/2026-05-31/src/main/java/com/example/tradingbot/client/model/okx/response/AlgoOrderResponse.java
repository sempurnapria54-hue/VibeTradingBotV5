package com.example.tradingbot.client.model.okx.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Ответ одного элемента data[] для GET /api/v5/trade/orders-algo-pending.
 * Все числовые значения приходят строками.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class AlgoOrderResponse {

    // -------------------- Инструмент / режимы --------------------

    /**
     * Тип инструмента: SPOT | MARGIN | SWAP | FUTURES | OPTION.
     */
    private String instType;

    /**
     * Инструмент (например ETH-USDT-SWAP).
     */
    private String instId;

    /**
     * Валюта маржи.
     * Применимо для isolated MARGIN и некоторых режимов cross MARGIN (Futures mode),
     * а также для FUTURES/SWAP контрактов.
     */
    private String ccy;

    /**
     * Trade mode: cash | cross | isolated.
     */
    private String tdMode;

    /**
     * Плечо (0.01..125).
     * Применимо для MARGIN/FUTURES/SWAP.
     */
    private String lever;

    /**
     * Quick margin type (только для isolated quick margin):
     * manual | auto_borrow | auto_repay.
     */
    private String quickMgnType;

    // -------------------- Идентификаторы --------------------

    /**
     * Algo ID (идентификатор algo-ордера).
     */
    private String algoId;

    /**
     * Client-supplied Algo ID (твой id для algo-ордера).
     */
    private String algoClOrdId;

    /**
     * Client Order ID (твой clOrdId).
     */
    private String clOrdId;

    /**
     * Latest order ID (последний связанный обычный ордер).
     */
    private String ordId;

    /**
     * Список ordId. Может содержать несколько значений при split TP/SL.
     */
    private List<String> ordIdList;

    // -------------------- Тип / состояние / теги --------------------

    /**
     * Тип algo-ордера:
     * conditional | oco | trigger | move_order_stop.
     */
    private String ordType;

    /**
     * Состояние algo-ордера:
     * live | pause.
     */
    private String state;

    /**
     * Тэг (если задавался).
     */
    private String tag;

    // -------------------- Сторона / позиция / закрытие --------------------

    /**
     * Сторона: buy | sell.
     */
    private String side;

    /**
     * Сторона позиции: net или long/short (в зависимости от режима позиций).
     */
    private String posSide;

    /**
     * Reduce-only: true/false — может ли ордер только уменьшать позицию.
     */
    private String reduceOnly;

    /**
     * Доля позиции для закрытия (например 1 = 100%).
     */
    private String closeFraction;

    // -------------------- Количество / единицы --------------------

    /**
     * Количество купить/продать.
     */
    private String sz;

    /**
     * Единица измерения sz для SPOT market:
     * base_ccy | quote_ccy (только SPOT market).
     */
    private String tgtCcy;

    // -------------------- TP (Take Profit) --------------------

    /**
     * TP trigger price.
     */
    private String tpTriggerPx;

    /**
     * Тип цены для TP триггера: last | index | mark.
     */
    private String tpTriggerPxType;

    /**
     * TP order price. -1 означает market.
     */
    private String tpOrdPx;

    // -------------------- SL (Stop Loss) --------------------

    /**
     * SL trigger price.
     */
    private String slTriggerPx;

    /**
     * Тип цены для SL триггера: last | index | mark.
     */
    private String slTriggerPxType;

    /**
     * SL order price. -1 означает market.
     */
    private String slOrdPx;

    // -------------------- Trigger order --------------------

    /**
     * Trigger price.
     */
    private String triggerPx;

    /**
     * Тип цены триггера: last | index | mark.
     */
    private String triggerPxType;

    /**
     * Цена выставляемого ордера после trigger. -1 означает market.
     */
    private String ordPx;

    // -------------------- Trailing stop (move_order_stop) --------------------

    /**
     * Callback price ratio (только trailing).
     */
    private String callbackRatio;

    /**
     * Callback price variance (только trailing).
     */
    private String callbackSpread;

    /**
     * Active price (только trailing).
     */
    private String activePx;

    /**
     * Trigger price (только trailing).
     */
    private String moveTriggerPx;

    // -------------------- Фактические значения после срабатывания --------------------

    /**
     * Фактическое количество.
     */
    private String actualSz;

    /**
     * Фактическая цена.
     */
    private String actualPx;

    /**
     * Что сработало: tp или sl (только для oco и conditional).
     */
    private String actualSide;

    /**
     * Время срабатывания (Unix ms).
     */
    private String triggerTime;

    /**
     * Last filled price “while placing” (служебное поле).
     */
    private String last;

    // -------------------- Iceberg / TWAP (если применимо) --------------------

    /**
     * Price ratio (iceberg/twap).
     */
    private String pxVar;

    /**
     * Price variance (iceberg/twap).
     */
    private String pxSpread;

    /**
     * Average amount (iceberg/twap).
     */
    private String szLimit;

    /**
     * Price limit (iceberg/twap).
     */
    private String pxLimit;

    /**
     * Time interval (только TWAP).
     */
    private String timeInterval;

    // -------------------- Fail / спец-настройки --------------------

    /**
     * Fail code.
     * В доке отмечено: для pending list часто приходит пустая строка.
     */
    private String failCode;

    /**
     * Cost-price SL для некоторых режимов split TP:
     * 0 — disable, 1 — enable.
     */
    private String amendPxOnTriggerType;

    // -------------------- Attached TP/SL (вложенный массив) --------------------

    /**
     * Attached SL/TP orders info (встречается не во всех режимах).
     */
    private List<AttachAlgoOrd> attachAlgoOrds;

    // -------------------- Время --------------------

    /**
     * Время создания algo-ордера (Unix ms).
     */
    private String cTime;

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AttachAlgoOrd {

        /**
         * Client-supplied algo id для attached TP/SL (до 32 символов).
         */
        private String attachAlgoClOrdId;

        /**
         * TP trigger price.
         */
        private String tpTriggerPx;

        /**
         * Тип цены TP триггера: last | index | mark (default last).
         */
        private String tpTriggerPxType;

        /**
         * TP order price. -1 означает market.
         */
        private String tpOrdPx;

        /**
         * SL trigger price.
         */
        private String slTriggerPx;

        /**
         * Тип цены SL триггера: last | index | mark (default last).
         */
        private String slTriggerPxType;

        /**
         * SL order price. -1 означает market.
         */
        private String slOrdPx;
    }
}
