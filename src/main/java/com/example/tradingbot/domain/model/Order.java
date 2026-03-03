package com.example.tradingbot.domain.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class Order extends Auditable {

    /**
     * Внутренний идентификатор ордера.
     */
    private Long id;

    /**
     * Идентификатор сделки.
     */
    private Long dealId;

    /**
     * Межсервисный идентификатор ордера.
     */
    private String internalId;

    /**
     * Идентификатор ордера на бирже.
     */
    private String externalId;

    /**
     * Текущий внутренний статус ордера.
     */
    private Status status;

    /**
     * Тип ордера в бизнес-терминах.
     */
    private String type;

    /**
     * Сторона ордера (buy/sell).
     */
    private String side;

    /**
     * Состояние ордера на стороне биржи.
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
     * Накопленный исполненный объём.
     */
    private BigDecimal accumulatedFillSize;

    /**
     * Средняя цена исполнения.
     */
    private BigDecimal averagePrice;

    /**
     * Комиссия по ордеру.
     */
    private BigDecimal fee;

    public enum Status {
        /**
         * Запись создана локально, ещё не отправляли
         */
        CREATED,
        /**
         * Отправили, но ещё не активен
         */
        PENDING,
        /**
         * Реально активен на бирже (после fill)
         */
        ACTIVE,
        /**
         * Полностью выполнен на бирже
         */
        COMPLETED,
        /**
         * Частично выполнен на бирже
         */
        PARTIALLY_COMPLETED,
        /**
         * Отменён
         */
        CLOSED,
        /**
         * Не удалось создать/обновить
         */
        FAILED
    }
}
