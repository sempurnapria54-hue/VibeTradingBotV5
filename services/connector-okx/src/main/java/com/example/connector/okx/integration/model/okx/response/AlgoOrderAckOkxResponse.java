package com.example.connector.okx.integration.model.okx.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * Сырой ACK OKX на place/cancel algo-order (элемент data). За adapter
 * не выходит; нормализуется в ExchangeAck. sCode="0" — принят. См.
 * docs/models/mapping/AlgoOrder.md.
 *
 * <p>{@code sCode}/{@code sMsg} несут явный {@link JsonProperty}: Lombok
 * (beanspec) даёт аксессоры {@code getsCode()}/{@code setsCode()}, чьё
 * выводимое имя свойства Jackson 3 (дефолт RestClient в SB4) НЕ матчит с
 * ключом {@code sCode} → поле биндилось в null (находка F3a). Явное имя
 * фиксирует бинд под обоими Jackson на classpath.
 */
@Getter
@Setter
public class AlgoOrderAckOkxResponse {

    /** Биржевой algo id (на place). */
    private String algoId;

    /** Эхо stable client algo id. */
    private String algoClOrdId;

    /** Код результата ("0" — принят). */
    @JsonProperty("sCode")
    private String sCode;

    /** Сообщение результата. */
    @JsonProperty("sMsg")
    private String sMsg;
}
