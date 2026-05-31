package com.example.tradingbot.client.model.okx.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderResponse {

    // -------------------- Инструмент и идентификаторы --------------------

    /**
     * Тип инструмента (например SWAP, SPOT и т.д.).
     */
    private String instType;

    /**
     * Инструмент на бирже (например ETH-USDT-SWAP).
     */
    private String instId;

    /**
     * ID ордера на стороне OKX.
     */
    private String ordId;

    /**
     * Клиентский ID ордера (задаёшь сам при создании). Удобно для идемпотентности и поиска.
     */
    private String clOrdId;

    /**
     * Тэг/метка ордера (если передавал).
     */
    private String tag;

    // -------------------- Сторона / режим позиции / режим торговли --------------------

    /**
     * Сторона ордера: buy или sell.
     */
    private String side;

    /**
     * Сторона позиции: в net-режиме обычно net, в hedge-режиме — long/short.
     */
    private String posSide;

    /**
     * Режим торговли (trade mode): isolated / cross / cash.
     */
    private String tdMode;

    // -------------------- Тип и параметры ордера --------------------

    /**
     * Тип ордера (limit, market, post_only, fok, ioc, optimal_limit_ioc и т.д.).
     */
    private String ordType;

    /**
     * Цена (для limit). Для market часто пусто.
     */
    private String px;

    /**
     * Размер ордера. Для SWAP — обычно контракты.
     */
    private String sz;

    /**
     * Только для SPOT market: в чём задан sz:
     * base_ccy (в базовой валюте) или quote_ccy (в котируемой валюте).
     */
    private String tgtCcy;

    // -------------------- Статус и прогресс исполнения --------------------

    /**
     * Состояние ордера: live или partially_filled.
     */
    private String state;

    /**
     * Сколько уже исполнено (накопленно).
     */
    private String accFillSz;

    /**
     * Цена последнего исполнения (если были сделки).
     */
    private String fillPx;

    /**
     * Размер последнего исполнения.
     */
    private String fillSz;

    /**
     * Время последнего исполнения.
     */
    private String fillTime;

    /**
     * Средняя цена исполнения (если ничего не исполнялось — пусто).
     */
    private String avgPx;

    /**
     * ID последней сделки по этому ордеру.
     */
    private String tradeId;

    // -------------------- Плечо / reduce-only / валюта маржи --------------------

    /**
     * Валюта маржи (для isolated в MARGIN/FUTURES/SWAP особенно важно).
     */
    private String ccy;

    /**
     * Плечо (строкой), актуально для MARGIN/FUTURES/SWAP.
     */
    private String lever;

    /**
     * reduceOnly=true/false (строкой): ордер только уменьшает позицию и не может её увеличить.
     */
    private String reduceOnly;

    /**
     * Quick margin type (обычно пусто).
     */
    private String quickMgnType;

    // -------------------- Комиссии / ребейт / PnL --------------------

    /**
     * Накопленная комиссия по ордеру (часто отрицательная строка, если уже были исполнения).
     */
    private String fee;

    /**
     * Валюта комиссии.
     */
    private String feeCcy;

    /**
     * Ребейт (возврат) для maker-сделок (если применимо).
     */
    private String rebate;

    /**
     * Валюта ребейта.
     */
    private String rebateCcy;

    /**
     * PnL без учёта комиссии. Обычно заполняется когда ордер реально закрывает позицию и есть сделки.
     */
    private String pnl;

    // -------------------- TP/SL, прикреплённые к ордеру (attach) --------------------

    /**
     * Клиентский ID для прикреплённых algo-ордеров (TP/SL), если задавал.
     */
    private String attachAlgoClOrdId;

    /**
     * Триггер-цена тейк-профита (если TP прикреплён).
     */
    private String tpTriggerPx;

    /**
     * Тип цены триггера TP: last / index / mark.
     */
    private String tpTriggerPxType;

    /**
     * Цена исполнения TP (для limit-TP; для market-TP может быть пусто).
     */
    private String tpOrdPx;

    /**
     * Триггер-цена стоп-лосса (если SL прикреплён).
     */
    private String slTriggerPx;

    /**
     * Тип цены триггера SL: last / index / mark.
     */
    private String slTriggerPxType;

    /**
     * Цена исполнения SL.
     */
    private String slOrdPx;

    /**
     * Список прикреплённых деталей TP/SL (массив объектов).
     */
    private List<AttachAlgoOrd> attachAlgoOrds;

    // -------------------- Связь с algo-ордерами --------------------

    /**
     * Client algo ID (если задавал).
     */
    private String algoClOrdId;

    /**
     * Algo ID. Может заполниться, когда algo-ордер сработал (triggered), иначе часто пусто.
     */
    private String algoId;

    /**
     * Связанный algo-ордер (используется, например, в OCO).
     */
    private LinkedAlgoOrd linkedAlgoOrd;

    /**
     * true/false: это TP-limit ордер или нет.
     */
    private String isTpLimit;

    // -------------------- Параметры опционов (для SWAP обычно пусто) --------------------

    /**
     * Тип цены для опционов: px / pxVol / pxUsd.
     */
    private String pxType;

    /**
     * Цена опциона в USD (только для OPTION).
     */
    private String pxUsd;

    /**
     * Implied volatility (только для OPTION).
     */
    private String pxVol;

    // -------------------- Self-Trade Prevention --------------------

    /**
     * STP id (deprecated, обычно пусто).
     */
    private String stpId;

    /**
     * Режим самоторговли (пример: cancel_maker).
     */
    private String stpMode;

    // -------------------- Источник ордера и отмены --------------------

    /**
     * Источник ордера (код строкой). Например:
     * 6 — создан trigger-ордером, 7 — создан TP/SL, 13 — создан algo, 25 — trailing stop, 34 — chase.
     */
    private String source;

    /**
     * Источник отмены (код), если ордер отменён.
     */
    private String cancelSource;

    /**
     * Причина отмены (если биржа её дала).
     */
    private String cancelSourceReason;

    // -------------------- Прочее --------------------

    /**
     * Котируемая валюта торговли (например USDT).
     */
    private String tradeQuoteCcy;

    /**
     * Категория ордера (например normal).
     */
    private String category;

    // -------------------- Времена --------------------

    /**
     * Время создания ордера (Unix ms).
     */
    private String cTime;

    /**
     * Время последнего обновления ордера (Unix ms).
     */
    private String uTime;

    // -------------------- Ошибки (иногда встречаются в batch-ответах) --------------------
    // В orders-pending обычно пусто, но ты можешь переиспользовать модель на других эндпоинтах.

    /**
     * Sub-code (код ошибки на уровне элемента), если применимо.
     */
    private String sCode;

    /**
     * Sub-message (сообщение ошибки на уровне элемента), если применимо.
     */
    private String sMsg;

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LinkedAlgoOrd {

        /**
         * ID связанного algo-ордера.
         */
        private String algoId;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AttachAlgoOrd {

        /**
         * ID прикреплённого algo-ордера на OKX (по нему можно потом amend-ить TP/SL).
         */
        private String attachAlgoId;

        /**
         * Твой клиентский ID прикреплённого algo-ордера.
         */
        private String attachAlgoClOrdId;

        /**
         * Вид TP: condition или limit.
         */
        private String tpOrdKind;

        /**
         * Триггер TP.
         */
        private String tpTriggerPx;

        /**
         * Триггер TP в процентах (например 0.3 = 30%). Только для FUTURES/SWAP.
         */
        private String tpTriggerRatio;

        /**
         * Тип цены триггера TP: last / index / mark.
         */
        private String tpTriggerPxType;

        /**
         * Цена TP.
         */
        private String tpOrdPx;

        /**
         * Триггер SL.
         */
        private String slTriggerPx;

        /**
         * Триггер SL в процентах (например 0.3 = 30%). Только для FUTURES/SWAP.
         */
        private String slTriggerRatio;

        /**
         * Тип цены триггера SL: last / index / mark.
         */
        private String slTriggerPxType;

        /**
         * Цена SL.
         */
        private String slOrdPx;

        /**
         * Размер (актуально для split-TP, когда тейки дробятся).
         */
        private String sz;

        /**
         * “Cost-price SL” для некоторых режимов split-TP:
         * 0 — выключено, 1 — включено.
         */
        private String amendPxOnTriggerType;

        /**
         * Код ошибки, если TP/SL не удалось поставить.
         */
        private String failCode;

        /**
         * Текст причины ошибки.
         */
        private String failReason;

        /**
         * Связанный algo-ордер (например, в OCO).
         */
        private LinkedAlgoOrd linkedAlgoOrd;

        /**
         * Algo ID (может заполниться, когда algo-ордер сработал).
         */
        private String algoId;

        /**
         * Client algo ID (если задавал).
         */
        private String algoClOrdId;

        /**
         * true/false: это TP-limit ордер или нет.
         */
        private String isTpLimit;
    }
}
