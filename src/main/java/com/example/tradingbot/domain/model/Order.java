package com.example.tradingbot.domain.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class Order {

    /** Идентификатор ордера на бирже. */
    private String externalId;
    /** Клиентский идентификатор ордера. */
    private String internalId;
    /** Идентификатор инструмента (instId). */
    private String externalInstrumentId;
    /** Тип инструмента ордера. */
    private String instrumentType;
    /** Сторона ордера: buy/sell. */
    private String side;
    /** Направление позиции для ордера. */
    private String positionSide;
    /** Тип ордера (limit/market и т.д.). */
    private String type;
    /** Цена ордера. */
    private String price;
    /** Объём ордера. */
    private String size;
    /** Текущее состояние ордера. */
    private String status;
    /** Средняя цена исполнения ордера. */
    private String averagePrice;
    /** Накопленный исполненный объём. */
    private String accumulatedFillSize;
    /** Комиссия по ордеру. */
    private String fee;
    /** Время создания ордера на бирже. */
    private String createTime;
    /** Время последнего обновления ордера. */
    private String updateTime;
    /** Код статуса ответа биржи. */
    private String externalStatusCode;
    /** Текст статуса/ошибки ответа биржи. */
    private String externalStatusMessage;
}
