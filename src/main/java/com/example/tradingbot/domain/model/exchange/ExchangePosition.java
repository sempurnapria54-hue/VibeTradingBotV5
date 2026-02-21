package com.example.tradingbot.domain.model.exchange;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ExchangePosition {

    /** Идентификатор инструмента (instId). */
    private String instrumentId;
    /** Тип инструмента позиции. */
    private String instrumentType;
    /** Сторона позиции (long/short/net). */
    private String positionSide;
    /** Размер открытой позиции. */
    private String positionSize;
    /** Средняя цена входа в позицию. */
    private String averagePrice;
    /** Текущая mark price позиции. */
    private String markPrice;
    /** Оценочная цена ликвидации. */
    private String liquidationPrice;
    /** Нереализованный PnL позиции. */
    private String unrealizedProfit;
    /** Плечо, применённое к позиции. */
    private String leverage;
    /** Режим маржи (cross/isolated). */
    private String marginMode;
    /** Время последнего обновления позиции. */
    private String updateTime;
}
