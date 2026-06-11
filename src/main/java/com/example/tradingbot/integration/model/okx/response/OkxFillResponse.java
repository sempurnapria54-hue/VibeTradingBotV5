package com.example.tradingbot.integration.model.okx.response;

import lombok.Getter;
import lombok.Setter;

/**
 * Сырой элемент OKX trade fills (GET /trade/fills, /trade/fills-history).
 * За adapter не выходит; нормализуется в FillExternalSnapshot. См.
 * docs/models/integrations/okx/OkxFillResponse.md.
 */
@Getter
@Setter
public class OkxFillResponse {

    /** Инструмент. */
    private String instId;

    /** Id сделки на бирже. */
    private String tradeId;

    /** Id ордера, породившего fill. */
    private String ordId;

    /** Client order id ордера. */
    private String clOrdId;

    /** Внутренний id записи — якорь пагинации. */
    private String billId;

    /** Цена сделки. */
    private String fillPx;

    /** Объём сделки. */
    private String fillSz;

    /** Сторона (buy/sell). */
    private String side;

    /** Сторона позиции. */
    private String posSide;

    /** Валюта комиссии. */
    private String feeCcy;

    /** Комиссия. */
    private String fee;

    /** Время сделки (Unix ms). */
    private String ts;
}
