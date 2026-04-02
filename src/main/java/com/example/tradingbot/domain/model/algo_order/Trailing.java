package com.example.tradingbot.domain.model.algo_order;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class Trailing {

    /**
     * Trailing по проценту (callbackRatio).
     * Пример: 0.01 = 1% отката от экстремума.
     * <p>
     * Если задано — trailingStepValue должно быть null.
     */
    private BigDecimal trailingPercents;

    /**
     * Trailing по абсолютному шагу (callbackSpread).
     * Пример: 25 = откат на 25 USDT от экстремума.
     * <p>
     * Если задано — trailingPercents должно быть null.
     */
    private BigDecimal trailingStepValue;

    /**
     * Цена активации trailing (optional).
     * Пока цена не достигла этого уровня — trailing не активен.
     * Если null — trailing активен сразу.
     */
    private TriggerPrice activationPrice;

    /**
     * Биржевое “текущее значение” trailing (обычно moveTriggerPx), которое возвращает OKX.
     */
    private BigDecimal externalPrice;

    public Trailing(BigDecimal trailingPercents, BigDecimal trailingStepValue, TriggerPrice activationPrice) {
        this.trailingPercents = trailingPercents;
        this.trailingStepValue = trailingStepValue;
        this.activationPrice = activationPrice;
    }
}
