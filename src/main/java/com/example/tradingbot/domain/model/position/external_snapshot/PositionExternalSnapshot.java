package com.example.tradingbot.domain.model.position.external_snapshot;

import com.example.tradingbot.domain.model.Auditable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PositionExternalSnapshot extends Auditable {

    /**
     * Идентификатор инструмента на бирже.
     */
    private String instrumentExternalId;

    /**
     * Идентификатор позиции на бирже.
     */
    private String externalId;

    /**
     * Сторона позиции на бирже (long/short/net).
     */
    private String externalSide;

    /**
     * Размер позиции.
     */
    private BigDecimal size;

    /**
     * Средняя цена входа.
     */
    private BigDecimal averagePrice;

    /**
     * Текущая рыночная цена позиции.
     */
    private BigDecimal markPrice;

    /**
     * Оценочная цена ликвидации позиции.
     */
    private BigDecimal liquidationPrice;

    /**
     * Плечо позиции.
     */
    private Integer leverage;

    /**
     * Биржевой режим маржи (cross/isolated).
     */
    private String marginMode;

    /**
     * Нереализованный PnL по позиции.
     */
    private BigDecimal unrealizedProfit;
}
