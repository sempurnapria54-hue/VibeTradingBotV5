package com.example.tradingbot.domain.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class AlgoOrder {

    /** Идентификатор algo-заявки на бирже. */
    private String externalId;
    /** Клиентский идентификатор заявки. */
    private String internalOrderId;
    /** Идентификатор инструмента (instId). */
    private String externalInstrumentId;
    /** Тип algo-заявки. */
    private String type;
    /** Текущее состояние algo-заявки. */
    private String status;
    /** Объём заявки. */
    private String size;
    /** Триггерная цена активации заявки. */
    private String triggerPrice;
    /** Цена выставления ордера после срабатывания триггера. */
    private String orderPrice;
    /** Триггерная цена для take-profit. */
    private String takeProfitTriggerPrice;
    /** Цена ордера для take-profit. */
    private String takeProfitOrderPrice;
    /** Триггерная цена для stop-loss. */
    private String stopLossTriggerPrice;
    /** Цена ордера для stop-loss. */
    private String stopLossOrderPrice;
    /** Коэффициент callback для trailing-механики. */
    private String callbackRatio;
    /** Шаг callback в абсолютном выражении. */
    private String callbackStep;
    /** Время создания заявки на бирже. */
    private String createTime;
    /** Время последнего обновления заявки на бирже. */
    private String updateTime;
    /** Код статуса ответа биржи. */
    private String externalStatusCode;
    /** Текст статуса/ошибки ответа биржи. */
    private String externalStatusMessage;
}
