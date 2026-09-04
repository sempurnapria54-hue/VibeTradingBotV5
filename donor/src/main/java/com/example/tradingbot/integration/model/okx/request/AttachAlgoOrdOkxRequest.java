package com.example.tradingbot.integration.model.okx.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

/**
 * Элемент attachAlgoOrds[*] тела OKX place-order — встроенная защита,
 * уходящая на биржу вместе с родительской заявкой. SL исполняется market
 * после trigger (slOrdPx = -1); slTriggerPxType заполняется всегда,
 * биржевой default не используется; sz не отправляется — источник ведёт
 * защиту на налитый объём родителя. null-поля не сериализуются. См.
 * docs/models/mapping/Order.md (§Domain Order → OKX request).
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AttachAlgoOrdOkxRequest {

    /** stable client id встроенной защиты (эхо — attachAlgoClOrdId, ключ матчинга). */
    private String attachAlgoClOrdId;

    /** Триггерная цена SL. */
    private String slTriggerPx;

    /** Ценовая база SL trigger (last/index/mark). */
    private String slTriggerPxType;

    /** Цена исполнения SL (-1 = market). */
    private String slOrdPx;
}
