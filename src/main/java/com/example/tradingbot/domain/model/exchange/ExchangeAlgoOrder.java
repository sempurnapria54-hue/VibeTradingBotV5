package com.example.tradingbot.domain.model.exchange;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ExchangeAlgoOrder {

    /** Идентификатор algo-заявки на бирже. */
    private String algoOrderId;
    /** Клиентский идентификатор заявки. */
    private String clientOrderId;
    /** Идентификатор инструмента (instId). */
    private String instrumentId;
    /** Тип algo-заявки. */
    private String orderType;
    /** Текущее состояние algo-заявки. */
    private String state;
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
    private String callbackSpread;
    /** Время создания заявки на бирже. */
    private String createTime;
    /** Время последнего обновления заявки на бирже. */
    private String updateTime;
    /** Код статуса ответа биржи. */
    private String statusCode;
    /** Текст статуса/ошибки ответа биржи. */
    private String statusMessage;
}
