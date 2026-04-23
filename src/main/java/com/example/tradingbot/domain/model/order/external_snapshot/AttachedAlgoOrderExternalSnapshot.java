package com.example.tradingbot.domain.model.order.external_snapshot;

import lombok.Getter;
import lombok.Setter;

/**
 * Снапшот прикреплённого algo-ордера из блока attachAlgoOrds в ответе OKX trade/order.
 */
@Getter
@Setter
public class AttachedAlgoOrderExternalSnapshot {

    /**
     * Идентификатор прикреплённого algo-ордера на стороне OKX.
     */
    private String externalAttachedId;

    /**
     * Клиентский идентификатор прикреплённого algo-ордера.
     */
    private String internalId;

    /**
     * Внешний идентификатор algo-ордера (если уже создан/привязан).
     */
    private String externalId;

    /**
     * Тип прикреплённого algo-ордера на стороне биржи (строковое значение).
     */
    private String externalType;

    /**
     * Размер прикреплённого algo-ордера.
     */
    private String size;

    /**
     * Триггер-цена stop-loss у прикреплённого algo-ордера.
     */
    private String stopLossTriggerPrice;

    /**
     * Код ошибки создания или привязки attached protection на стороне OKX.
     */
    private String failCode;

    /**
     * Текст причины ошибки создания или привязки attached protection на стороне OKX.
     */
    private String failReason;
}
