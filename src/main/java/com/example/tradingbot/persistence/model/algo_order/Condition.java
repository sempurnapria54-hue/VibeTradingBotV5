package com.example.tradingbot.persistence.model.algo_order;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Condition {

    /**
     * Тип условия (строка вместо enum).
     * Примеры: STOP_LOSS, TAKE_PROFIT, OCO_FULL, TRAILING_PERCENTS, TRAILING_VALUE,
     * PARTIAL_TAKE_PROFIT, PARTIAL_STOP_LOSS.
     */
    private String type;

    /**
     * Доля позиции, которую закрываем при срабатывании условия.
     * 1 = 100%, 0.25 = 25%.
     */
    private BigDecimal closeFraction;

    /**
     * Триггеры SL/TP (STOP_LOSS/TAKE_PROFIT/OCO и PARTIAL_*).
     * Для trailing условий должен быть null.
     */
    private Trigger trigger;

    /**
     * Параметры trailing (TRAILING_*).
     * Для trigger-based условий должен быть null.
     */
    private Trailing trailing;
}