package com.example.tradingbot.integration.model.okx.response;

import lombok.Getter;
import lombok.Setter;

/**
 * Сырой элемент attached protection из ответа OKX по order
 * (attachAlgoOrds[*]). За adapter не выходит; нормализуется маппером в
 * AttachedAlgoOrderExternalSnapshot. См. docs/models/mapping/Order.md
 * (§AttachedAlgoOrder).
 */
@Getter
@Setter
public class AttachAlgoOrdOkxResponse {

    /** attached algo id из embedded block. */
    private String attachAlgoId;

    /** client id вложенного TP/SL (ключ матчинга). */
    private String attachAlgoClOrdId;

    /** algo id после trigger/создания. */
    private String algoId;

    /** Тип attached protection (tpOrdKind / future). */
    private String tpOrdKind;

    /** Размер. */
    private String sz;

    /** Trigger SL. */
    private String slTriggerPx;

    /** Код ошибки биржи (если заполнен — attached ERROR). */
    private String failCode;

    /** Диагностика ошибки. */
    private String failReason;
}
