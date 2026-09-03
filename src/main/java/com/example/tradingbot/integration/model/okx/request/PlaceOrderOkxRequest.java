package com.example.tradingbot.integration.model.okx.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Тело OKX place-order (POST /trade/order). null-поля не сериализуются
 * (NON_NULL): для market-ордера px отсутствует. tdMode/posSide —
 * adapter-константы. См. docs/models/mapping/Order.md.
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PlaceOrderOkxRequest {

    /** Инструмент. */
    private String instId;

    /** Режим маржи (adapter-константа isolated). */
    private String tdMode;

    /** Сторона (buy/sell). */
    private String side;

    /** Сторона позиции (adapter-константа net). */
    private String posSide;

    /** Тип ордера OKX (limit/market). */
    private String ordType;

    /** Размер. */
    private String sz;

    /** Цена (для market отсутствует). */
    private String px;

    /** stable client id. */
    private String clOrdId;

    /** Только уменьшать позицию. */
    private Boolean reduceOnly;

    /** Метка запроса. */
    private String tag;

    /** Встроенная защита (attachAlgoOrds[*]); у заявки без защиты отсутствует. */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<AttachAlgoOrdOkxRequest> attachAlgoOrds;
}
