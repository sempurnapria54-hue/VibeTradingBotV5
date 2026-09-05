package com.example.connector.okx.integration.model.okx.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

/**
 * Тело OKX place-algo (POST /trade/order-algo). ordType выводится из
 * conditionType (conditional/oco/move_order_stop); SL/TP исполняются
 * market после trigger (slOrdPx/tpOrdPx = -1). null-поля не
 * сериализуются. См. docs/models/mapping/AlgoOrder.md.
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PlaceAlgoOrderOkxRequest {

    /** Инструмент. */
    private String instId;

    /** Режим маржи (adapter-константа isolated). */
    private String tdMode;

    /** Сторона позиции (adapter-константа net). */
    private String posSide;

    /** stable client algo id. */
    private String algoClOrdId;

    /** Сторона (buy/sell). */
    private String side;

    /** Тип algo-order OKX (conditional/oco/move_order_stop). */
    private String ordType;

    /** Размер. */
    private String sz;

    /** Только уменьшать позицию. */
    private Boolean reduceOnly;

    /** SL trigger price. */
    private String slTriggerPx;

    /** Тип цены SL trigger (last/index/mark). */
    private String slTriggerPxType;

    /** Цена исполнения SL (-1 = market). */
    private String slOrdPx;

    /** TP trigger price. */
    private String tpTriggerPx;

    /** Тип цены TP trigger. */
    private String tpTriggerPxType;

    /** Цена исполнения TP (-1 = market). */
    private String tpOrdPx;

    /** Trailing callback в процентах. */
    private String callbackRatio;

    /** Trailing callback в абсолютном значении. */
    private String callbackSpread;

    /** Цена активации trailing. */
    private String activePx;
}
