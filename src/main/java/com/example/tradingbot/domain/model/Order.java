package com.example.tradingbot.domain.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

import static org.apache.commons.lang3.BooleanUtils.isFalse;

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
     * Причина закрытия ордера.
     */
    private CloseReason closeReason;

    /**
     * Тип ордера в бизнес-терминах.
     */
    private Type type;

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

    /**
     * Прикреплённыe SL при создании.
     */
    private List<AttachedAlgoOrder> attachedAlgoOrders;

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

    public enum Type {

        /**
         * Ордер на вход в сделку: открыть позицию.
         */
        ENTRY,

        /**
         * Ордер на вход в сделку: открыть позицию вместе с привязанным SL.
         */
        ENTRY_ATTACHED_STOP_LOSS,

    }

    public enum CloseReason {
        /**
         * Ордер отменён аварийным kill-switch.
         */
        KILL_SWITCH
    }

    public boolean isLive() {
        return status == Status.CREATED
                || status == Status.PENDING
                || status == Status.ACTIVE
                || status == Status.PARTIALLY_COMPLETED;
    }

    public boolean isNotLive() {
        return isFalse(isLive());
    }

    public void toClose(CloseReason reason) {
        setStatus(Status.CLOSED);
        setCloseReason(reason);
    }
}
