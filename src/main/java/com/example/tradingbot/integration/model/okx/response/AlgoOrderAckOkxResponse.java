package com.example.tradingbot.integration.model.okx.response;

import lombok.Getter;
import lombok.Setter;

/**
 * Сырой ACK OKX на place/cancel algo-order (элемент data). За adapter
 * не выходит; нормализуется в ExchangeAck. sCode="0" — принят. См.
 * docs/models/mapping/AlgoOrder.md.
 */
@Getter
@Setter
public class AlgoOrderAckOkxResponse {

    /** Биржевой algo id (на place). */
    private String algoId;

    /** Эхо stable client algo id. */
    private String algoClOrdId;

    /** Код результата ("0" — принят). */
    private String sCode;

    /** Сообщение результата. */
    private String sMsg;
}
