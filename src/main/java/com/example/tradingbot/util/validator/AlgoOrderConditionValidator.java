package com.example.tradingbot.util.validator;

import com.example.tradingbot.domain.model.algo_order.ConditionType;
import com.example.tradingbot.domain.model.algo_order.Trailing;
import com.example.tradingbot.domain.model.algo_order.Trigger;
import com.example.tradingbot.domain.model.algo_order.TriggerPrice;

import java.math.BigDecimal;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;

public class AlgoOrderConditionValidator {

    public static void validateByType(ConditionType type,
                                      BigDecimal closeFraction,
                                      Trigger trigger,
                                      Trailing trailing) {
        if (isNull(type)) {
            throw new IllegalArgumentException("ConditionType is null");
        }

        requireCloseFractionCommon(closeFraction);

        if (type == ConditionType.STOP_LOSS) {
            validateStopLossOnly(trigger, type.name());
            requireCloseFractionFull(closeFraction, type.name());
            return;
        }

        if (type == ConditionType.TAKE_PROFIT) {
            validateTakeProfitOnly(trigger, type.name());
            requireCloseFractionFull(closeFraction, type.name());
            return;
        }

        if (type == ConditionType.PARTIAL_STOP_LOSS) {
            validateStopLossOnly(trigger, type.name());
            requireCloseFractionPartial(closeFraction, type.name());
            return;
        }

        if (type == ConditionType.PARTIAL_TAKE_PROFIT) {
            validateTakeProfitOnly(trigger, type.name());
            requireCloseFractionPartial(closeFraction, type.name());
            return;
        }

        if (type == ConditionType.OCO_FULL) {
            validateOco(trigger, type.name());
            requireCloseFractionFull(closeFraction, type.name());
            return;
        }

        if (type == ConditionType.TRAILING_PERCENTS) {
            validateTrailingPercents(trailing, type.name());
            requireCloseFractionFull(closeFraction, type.name());
            return;
        }

        if (type == ConditionType.TRAILING_VALUE) {
            validateTrailingValue(trailing, type.name());
            requireCloseFractionFull(closeFraction, type.name());
            return;
        }

        throw new IllegalStateException("Unsupported condition type: " + type);
    }

    private static void validateStopLossOnly(Trigger trigger, String typeName) {
        validateStopLoss(trigger, typeName);
        requireNull(trigger.getTakeProfit(), "trigger.takeProfit", typeName);
    }

    private static void validateTakeProfitOnly(Trigger trigger, String typeName) {
        validateTakeProfit(trigger, typeName);
        requireNull(trigger.getStopLoss(), "trigger.stopLoss", typeName);
    }

    private static void validateStopLoss(Trigger trigger, String typeName) {
        requireNotNull(trigger, "trigger", typeName);

        TriggerPrice stopLoss = trigger.getStopLoss();
        requireNotNull(stopLoss, "trigger.stopLoss", typeName);

        if (isNull(stopLoss.getType())) {
            throw new IllegalArgumentException("trigger.stopLoss.type is required for " + typeName);
        }

        requirePositive(stopLoss.getValue(), "trigger.stopLoss.value");
    }

    private static void validateTakeProfit(Trigger trigger, String typeName) {
        requireNotNull(trigger, "trigger", typeName);

        TriggerPrice takeProfit = trigger.getTakeProfit();
        requireNotNull(takeProfit, "trigger.takeProfit", typeName);

        if (isNull(takeProfit.getType())) {
            throw new IllegalArgumentException("trigger.takeProfit.type is required for " + typeName);
        }

        requirePositive(takeProfit.getValue(), "trigger.takeProfit.value");
    }

    private static void validateOco(Trigger trigger, String typeName) {
        requireNotNull(trigger, "trigger", typeName);

        if (isNull(trigger.getStopLoss())) {
            throw new IllegalArgumentException("trigger.stopLoss is required for " + typeName);
        }

        if (isNull(trigger.getTakeProfit())) {
            throw new IllegalArgumentException("trigger.takeProfit is required for " + typeName);
        }

        validateStopLoss(trigger, typeName);
        validateTakeProfit(trigger, typeName);
    }

    private static void validateTrailingPercents(Trailing trailing, String typeName) {
        requireNotNull(trailing, "trailing", typeName);

        requirePositive(trailing.getTrailingPercents(), "trailing.trailingPercents");

        if (trailing.getTrailingPercents()
                    .compareTo(BigDecimal.ONE) >= 0) {
            throw new IllegalArgumentException("trailing.trailingPercents must be < 1");
        }

        requireNull(trailing.getTrailingStepValue(), "trailing.trailingStepValue", typeName);

        validateActivationPriceIfPresent(trailing.getActivationPrice(), typeName);
    }

    private static void validateTrailingValue(Trailing trailing, String typeName) {
        requireNotNull(trailing, "trailing", typeName);

        requirePositive(trailing.getTrailingStepValue(), "trailing.trailingStepValue");

        requireNull(trailing.getTrailingPercents(), "trailing.trailingPercents", typeName);

        validateActivationPriceIfPresent(trailing.getActivationPrice(), typeName);
    }

    private static void validateActivationPriceIfPresent(TriggerPrice activationPrice, String typeName) {
        if (isNull(activationPrice)) {
            return;
        }

        requireNotNull(activationPrice.getType(), "trailing.activationPrice.type", typeName);
        requirePositive(activationPrice.getValue(), "trailing.activationPrice.value");
    }

    private static void requireNotNull(Object value, String name, String typeName) {
        if (isNull(value)) {
            throw new IllegalArgumentException(name + " is required for " + typeName);
        }
    }

    private static void requireNull(Object value, String name, String typeName) {
        if (isFalse(isNull(value))) {
            throw new IllegalArgumentException(name + " must be null for " + typeName);
        }
    }

    private static void requirePositive(BigDecimal value, String name) {
        if (isNull(value)) {
            throw new IllegalArgumentException(name + " is null");
        }

        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(name + " must be > 0");
        }
    }

    private static void requireCloseFractionCommon(BigDecimal closeFraction) {
        if (isNull(closeFraction)) {
            throw new IllegalArgumentException("closeFraction is null");
        }

        if (closeFraction.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("closeFraction must be > 0");
        }

        if (closeFraction.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("closeFraction must be <= 1");
        }
    }

    private static void requireCloseFractionFull(BigDecimal closeFraction, String typeName) {
        if (closeFraction.compareTo(BigDecimal.ONE) != 0) {
            throw new IllegalArgumentException(typeName + " requires closeFraction = 1");
        }
    }

    private static void requireCloseFractionPartial(BigDecimal closeFraction, String typeName) {
        if (closeFraction.compareTo(BigDecimal.ONE) >= 0) {
            throw new IllegalArgumentException(typeName + " requires closeFraction < 1");
        }
    }
}
