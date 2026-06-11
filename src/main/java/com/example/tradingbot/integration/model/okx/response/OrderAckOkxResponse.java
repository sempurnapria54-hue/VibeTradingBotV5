package com.example.tradingbot.integration.model.okx.response;

import lombok.Getter;
import lombok.Setter;

/**
 * Сырой ACK OKX на place/cancel order (элемент data). За adapter не
 * выходит; нормализуется в ExchangeAck. sCode="0" — запрос принят. См.
 * docs/models/mapping/Order.md.
 */
@Getter
@Setter
public class OrderAckOkxResponse {

    /** Биржевой id ордера (на place). */
    private String ordId;

    /** Эхо stable client id. */
    private String clOrdId;

    /** Метка запроса. */
    private String tag;

    /** Код результата по ордеру ("0" — принят). */
    private String sCode;

    /** Сообщение результата по ордеру. */
    private String sMsg;
}
