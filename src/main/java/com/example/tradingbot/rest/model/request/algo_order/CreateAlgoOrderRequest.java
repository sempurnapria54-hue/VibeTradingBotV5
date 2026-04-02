package com.example.tradingbot.rest.model.request.algo_order;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateAlgoOrderRequest {

    /**
     * * Внутренний идентификатор сделки.
     */
    private String dealInternalId;

    /**
     * Внутренний тип algo-ордера.
     */
    private String type;

    /**
     * Сторона алго-ордера (buy/sell).
     */
    private String internalSide;

    /**
     * Объём algo-ордера.
     */
    private BigDecimal size;

    /**
     * Для Type.CONDITIONAL_MARKET_TP, CONDITIONAL_MARKET_FULL
     * Триггерная цена take-profit.
     */
    private BigDecimal takeProfitTriggerPrice;

    /**
     * Для Type.CONDITIONAL_MARKET_SL, CONDITIONAL_MARKET_FULL
     * Триггерная цена stop-loss.
     */
    private BigDecimal stopLossTriggerPrice;

    /**
     * Для Type.TRAILING_MARKET_PERCENTS,
     * Коэффициент callback для trailing-механики.
     * Процент “отката” от экстремума. (Пример: 0.01 = 1%)
     */
    private BigDecimal trailingFallenPercents;

    /**
     * Для Type.TRAILING_MARKET_VALUE
     * Абсолютный шаг callback для trailing-механики.
     */
    private BigDecimal trailingFallenAbsoluteValue;
}
