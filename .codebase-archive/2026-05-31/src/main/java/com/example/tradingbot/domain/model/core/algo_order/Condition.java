package com.example.tradingbot.domain.model.core.algo_order;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

import static com.example.tradingbot.util.validator.AlgoOrderConditionValidator.validateByType;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;

@Getter
@NoArgsConstructor
public class Condition {

    /**
     * Тип условия. Определяет, какие поля должны быть заполнены.
     */
    private ConditionType type;

    /**
     * Доля позиции, которую закрываем при срабатывании условия.
     * 1 = 100%, 0.25 = 25%.
     */
    private BigDecimal closeFraction;

    /**
     * Триггеры SL/TP (используется для STOP_LOSS/TAKE_PROFIT/OCO и PARTIAL_*).
     * Для trailing условий должен быть null.
     */
    private Trigger trigger;

    /**
     * Параметры trailing (используется для TRAILING_*).
     * Для trigger-based условий должен быть null.
     */
    private Trailing trailing;

    protected Condition(ConditionType type, BigDecimal closeFraction, Trigger trigger, Trailing trailing) {
        if (type == null) {
            throw new IllegalArgumentException("ConditionType is null");
        }

        if (closeFraction == null) {
            throw new IllegalArgumentException("closeFraction is null");
        }

        if (closeFraction.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("closeFraction must be > 0");
        }

        if (closeFraction.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("closeFraction must be <= 1");
        }

        // Ровно один механизм: trigger XOR trailing (без xor-оператора)
        boolean hasTrigger = nonNull(trigger);
        boolean hasTrailing = nonNull(trailing);

        if (isFalse(hasTrigger) && isFalse(hasTrailing)) {
            throw new IllegalArgumentException("Either trigger or trailing must be set");
        }

        if (hasTrigger && hasTrailing) {
            throw new IllegalArgumentException("Only one of trigger or trailing must be set");
        }

        validateByType(type, closeFraction, trigger, trailing);

        this.type = type;
        this.closeFraction = closeFraction;
        this.trigger = trigger;
        this.trailing = trailing;
    }

    public void validate() {
        validateByType(type, closeFraction, trigger, trailing);
    }

    public void setType(ConditionType type) {
        if (nonNull(this.type)) {
            throw new IllegalArgumentException("type is already set");
        }
        this.type = type;
    }

    public void setCloseFraction(BigDecimal closeFraction) {
        if (nonNull(this.closeFraction)) {
            throw new IllegalArgumentException("closeFraction is already set");
        }
        this.closeFraction = closeFraction;
    }

    public void setTrigger(Trigger trigger) {
        if (nonNull(this.trigger)) {
            throw new IllegalArgumentException("trigger is already set");
        }
        this.trigger = trigger;
    }

    public void setTrailing(Trailing trailing) {
        if (nonNull(this.trailing)) {
            throw new IllegalArgumentException("trailing is already set");
        }
        this.trailing = trailing;
    }
}
