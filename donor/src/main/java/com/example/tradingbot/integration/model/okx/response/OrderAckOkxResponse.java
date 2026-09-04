package com.example.tradingbot.integration.model.okx.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * Сырой ACK OKX на place/cancel order (элемент data). За adapter не
 * выходит; нормализуется в ExchangeAck. sCode="0" — запрос принят. См.
 * docs/models/mapping/Order.md.
 *
 * <p>{@code sCode}/{@code sMsg} несут явный {@link JsonProperty}: Lombok
 * (beanspec) даёт аксессоры {@code getsCode()}/{@code setsCode()}, чьё
 * выводимое имя свойства Jackson 3 (дефолт RestClient в SB4) НЕ матчит с
 * ключом {@code sCode} → поле биндилось в null (находка F3a). Явное имя
 * фиксирует бинд под обоими Jackson на classpath.
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
    @JsonProperty("sCode")
    private String sCode;

    /** Сообщение результата по ордеру. */
    @JsonProperty("sMsg")
    private String sMsg;

    /** Биржевое время приёма запроса (Unix ms). */
    private String ts;
}
