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
public class Trailing {

    /**
     * Trailing по проценту (callbackRatio).
     * Пример: 0.01 = 1% отката от экстремума.
     */
    private BigDecimal trailingPercents;

    /**
     * Trailing по абсолютному шагу (callbackSpread).
     */
    private BigDecimal trailingStepValue;

    /**
     * Цена активации trailing (optional).
     * Если null — trailing активен сразу.
     */
    private TriggerPrice activationPrice;

    /**
     * Биржевое текущее значение trailing (обычно moveTriggerPx), как вернула OKX.
     */
    private BigDecimal externalPrice;
}