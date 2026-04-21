package com.example.tradingbot.domain.model.order.external_snapshot;

import com.example.tradingbot.domain.model.Auditable;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 * Внешний снапшот ордера, полученный из OKX REST GET-методов.
 */
@Getter
@Setter
public class OrderExternalSnapshot extends Auditable {

    /**
     * Клиентский (внутренний) идентификатор ордера на стороне бота/биржи.
     */
    private String internalId;

    /**
     * Внешний идентификатор ордера на стороне OKX.
     */
    private String externalId;

    /**
     * Тип ордера с биржи (строка из ordType).
     */
    private String type;

    /**
     * Сторона ордера (buy/sell).
     */
    private String side;

    /**
     * Биржевой статус ордера (state).
     */
    private String externalStatus;

    /**
     * Цена ордера.
     */
    private BigDecimal price;

    /**
     * Объём ордера.
     */
    private BigDecimal size;

    /**
     * Накопленный исполненный объём ордера.
     */
    private BigDecimal accumulatedFillSize;

    /**
     * Средняя цена исполнения ордера.
     */
    private BigDecimal averagePrice;

    /**
     * Накопленная комиссия по ордеру.
     */
    private BigDecimal fee;

    /**
     * Снапшоты прикреплённых algo-ордеров (attachAlgoOrds).
     */
    private List<AttachedAlgoOrderExternalSnapshot> attachedAlgoOrders;

    /**
     * Клиентский идентификатор прикреплённой защиты из top-level order snapshot.
     */
    private String attachedAlgoInternalId;

    /**
     * Top-level триггер take-profit из order snapshot.
     */
    private BigDecimal takeProfitTriggerPrice;

    /**
     * Top-level триггер stop-loss из order snapshot.
     */
    private BigDecimal stopLossTriggerPrice;
}
