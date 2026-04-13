package com.example.tradingbot.persistence.model.deal.algo_order;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TriggerPrice {

    /**
     * Тип цены для сравнения триггера (строка вместо enum).
     * Примеры: MARK/LAST/INDEX.
     */
    private String type;

    /**
     * Внутреннее значение триггерной цены.
     */
    private BigDecimal value;

    /**
     * Биржевой тип цены триггера (как вернула OKX): last/index/mark.
     */
    private String externalType;

    /**
     * Биржевое значение триггерной цены (как вернула OKX).
     */
    private BigDecimal externalValue;
}