package com.example.tradingbot.domain.model.core.algo_order;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class TriggerPrice {

    /**
     * Внутренний тип цены для сравнения триггера: LAST/INDEX/MARK.
     */
    private TriggerPriceType type;

    /**
     * Внутреннее значение триггерной цены.
     */
    private BigDecimal value;

    /**
     * Биржевой тип цены триггера (как вернула OKX): last/index/mark.
     * Храним строкой, чтобы не потерять точное значение и не зависеть от enum-ов.
     */
    private String externalType;

    /**
     * Биржевое значение триггерной цены (как вернула OKX).
     * Может отличаться от value из-за округления/формата.
     */
    private BigDecimal externalValue;

    public TriggerPrice(TriggerPriceType type, BigDecimal value) {
        this.type = type;
        this.value = value;
    }
}
