package com.example.tradingbot.domain.model.algo_order;

import com.example.tradingbot.domain.model.Auditable;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class AlgoOrder extends Auditable {

    /**
     * Внутренний идентификатор algo-ордера.
     */
    private Long id;

    /**
     * Идентификатор сделки.
     */
    private Long dealId;

    /**
     * Межсервисный идентификатор algo-ордера (идемпотентность).
     */
    private String internalId;

    /**
     * Текущий внутренний статус.
     */
    private Status status;

    /**
     * Доменный тип условия.
     * Дублируется с condition.getType() для удобной фильтрации/логирования.
     */
    private ConditionType conditionType;

    /**
     * Объём algo-ордера.
     * Для close-algo может быть null, если закрываем через closeFraction=1.
     */
    private BigDecimal size;

    /**
     * Сторона алго-ордера (buy/sell) в домене.
     * Для close-algo обычно вычисляется из позиции.
     */
    private Direction direction;

    /**
     * Идентификатор algo-ордера на бирже (algoId).
     */
    private String externalId;

    /**
     * Биржевой тип algo-ордера (ordType): conditional | oco | move_order_stop | trigger ...
     */
    private String externalType;

    /**
     * Состояние algo-ордера на стороне биржи (state), например live/pause.
     */
    private String externalStatus;

    /**
     * Сторона алго-ордера на бирже (buy/sell).
     */
    private String externalDirection;

    /**
     * Сторона позиции на бирже (posSide): net | long | short.
     */
    private String externalPositionSide;

    /**
     * Условие (StopLoss/TakeProfit/OCO/Trailing/Partial...).
     * Именно оно определяет, какие параметры должны быть заполнены.
     */
    private Condition condition;

    public enum Status {
        /**
         * Запись создана локально, ещё не отправляли.
         */
        CREATED,
        /**
         * Отправили, но ещё не активен.
         */
        PENDING,
        /**
         * Реально активен на бирже.
         */
        ACTIVE,
        /**
         * Отменён/сработал.
         */
        CLOSED,
        /**
         * Не удалось создать/обновить.
         */
        FAILED
    }

    public enum Direction {
        /**
         * Покупка (buy).
         */
        BUY,
        /**
         * Продажа (sell).
         */
        SELL
    }
}