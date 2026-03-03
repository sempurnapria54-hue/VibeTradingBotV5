package com.example.tradingbot.domain.model;

import com.example.tradingbot.rest.model.response.Auditable;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class Position extends Auditable {

    /**
     * Внутренний идентификатор.
     */
    private Long id;

    /**
     * Идентификатор сделки.
     */
    private Long dealId;

    /**
     * Межсервисный идентификатор.
     */
    private String internalId;

    /**
     * Идентификатор позиции на бирже.
     */
    private String externalId;

    /**
     * Текущий внутренний статус позиции.
     */
    private Status status;

    /**
     * Сторона позиции (long/short/net).
     */
    private String side;

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

    public enum Status {
        ACTIVE,
        CLOSED,
        ERROR
    }
}
