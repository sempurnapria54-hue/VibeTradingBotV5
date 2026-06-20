package com.example.tradingbot.domain.command.calc;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.model.aggregate.strategy.StrategyDetail;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAlgoOrderAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyOrderAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyPositionAction;
import com.example.tradingbot.domain.model.core.instrument.InstrumentExternalRules;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingbot.util.Constants;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

/**
 * Рассчитывает размер order/algo/position action и возвращает
 * {@link CalculatedSize} (docs/components/SizeCalculator.md). Для
 * risk-creating входа со стопом размер ограничен лимитом риска на сделку:
 * убыток на стопе ≤ riskPerTradePercent × externalAvailableEquity; размер
 * округляется по lotSz и снизу ограничен minSz (если даже на minSz убыток
 * превышает лимит — возвращается minSz-пол, блокирует RiskValidator).
 * Direct partial close через StrategyPositionAction не считается (только
 * CLOSE_FULL); частичное уменьшение — через reduce-only Order/AlgoOrder
 * ({@code closeFraction}). Нельзя безопасно посчитать (нет lotSz/minSz/
 * ctVal/позиции) → controlled {@link CalculationException}.
 */
@Component
public class SizeCalculator {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final String MISSING_SIZE_SPECS = "MISSING_SIZE_SPECS";
    private static final String MISSING_BALANCE = "MISSING_BALANCE";
    private static final String MISSING_ALLOCATION = "MISSING_ALLOCATION";
    private static final String MISSING_ENTRY_PRICE = "MISSING_ENTRY_PRICE";
    private static final String MISSING_CLOSE_FRACTION = "MISSING_CLOSE_FRACTION";
    private static final String NO_POSITION = "NO_POSITION";

    public CalculatedSize calculate(CalculationContext context, CalculatedPrice calculatedPrice) {
        return switch (context.getAction()) {
            case StrategyPositionAction ignored -> fullClose(context);
            case StrategyAlgoOrderAction algoAction -> algoSize(algoAction, context);
            case StrategyOrderAction orderAction -> orderSize(orderAction, context, calculatedPrice);
            default -> CalculatedSize.builder()
                    .sizeMode(SizeMode.NOT_REQUIRED)
                    .description("size not required for action")
                    .build();
        };
    }

    private CalculatedSize orderSize(StrategyOrderAction action, CalculationContext context,
                                     CalculatedPrice calculatedPrice) {
        if (isTrue(action.getPositionReducingOnly())) {
            return reduceOnlyOrderSize(action, context);
        }
        return entrySize(action, context, calculatedPrice);
    }

    private CalculatedSize entrySize(StrategyOrderAction action, CalculationContext context,
                                     CalculatedPrice calculatedPrice) {
        InstrumentExternalRules rules = requireSpecs(context);
        BigDecimal equity = requireAvailableEquity(context);
        BigDecimal entryPrice = requireEntryPrice(calculatedPrice);
        BigDecimal ctVal = rules.contractValue();
        BigDecimal allocationFraction = requireFraction(action.getAllocationPercents(), MISSING_ALLOCATION);

        BigDecimal desiredNotional = equity.multiply(allocationFraction);
        BigDecimal contracts = desiredNotional.divide(entryPrice.multiply(ctVal), Constants.Calc.MATH_CONTEXT);
        contracts = capByRiskLimit(contracts, entryPrice, ctVal, equity, context, calculatedPrice);

        BigDecimal rounded = floorAtMin(roundDownToLot(contracts, rules.lotSize()), rules.minSize());
        BigDecimal notional = rounded.multiply(entryPrice).multiply(ctVal);
        return CalculatedSize.builder()
                .sizeContracts(rounded)
                .notionalUsdt(notional)
                .sizeMode(SizeMode.OPEN_OR_INCREASE)
                .description("risk-bounded entry size")
                .build();
    }

    /** Кэп размера лимитом риска на сделку, если у входа есть стоп; иначе размер по allocation. */
    private BigDecimal capByRiskLimit(BigDecimal contracts, BigDecimal entryPrice, BigDecimal ctVal,
                                      BigDecimal equity, CalculationContext context, CalculatedPrice calculatedPrice) {
        if (isNull(calculatedPrice.getStopLossPrice())
                || isNull(calculatedPrice.getStopLossPrice().getTriggerPrice())) {
            return contracts;
        }
        StrategyDetail detail = context.getStrategyDetail();
        if (isNull(detail) || isNull(detail.getRiskPerTradePercent())) {
            return contracts;
        }
        BigDecimal stopDistance = entryPrice.subtract(calculatedPrice.getStopLossPrice().getTriggerPrice()).abs();
        if (stopDistance.signum() <= 0) {
            return contracts;
        }
        BigDecimal riskBudget = equity.multiply(fraction(detail.getRiskPerTradePercent()));
        BigDecimal maxByRisk = riskBudget.divide(stopDistance.multiply(ctVal), Constants.Calc.MATH_CONTEXT);
        return contracts.min(maxByRisk);
    }

    private CalculatedSize reduceOnlyOrderSize(StrategyOrderAction action, CalculationContext context) {
        InstrumentExternalRules rules = requireSpecs(context);
        Position position = requirePosition(context);
        BigDecimal fraction = clampFraction(requireFraction(action.getAllocationPercents(), MISSING_CLOSE_FRACTION));
        BigDecimal rounded = floorAtMin(
                roundDownToLot(position.getExternalSize().multiply(fraction), rules.lotSize()), rules.minSize());
        return CalculatedSize.builder()
                .sizeContracts(rounded)
                .closeFraction(fraction)
                .sizeMode(SizeMode.REDUCE_ONLY)
                .description("reduce-only order size")
                .build();
    }

    private CalculatedSize algoSize(StrategyAlgoOrderAction action, CalculationContext context) {
        InstrumentExternalRules rules = requireSpecs(context);
        Position position = requirePosition(context);
        BigDecimal fraction = clampFraction(switch (action.getConditionType()) {
            case PARTIAL_TAKE_PROFIT, PARTIAL_STOP_LOSS ->
                    requireFraction(action.getCloseFractionPercents(), MISSING_CLOSE_FRACTION);
            default -> BigDecimal.ONE;
        });
        BigDecimal rounded = floorAtMin(
                roundDownToLot(position.getExternalSize().multiply(fraction), rules.lotSize()), rules.minSize());
        return CalculatedSize.builder()
                .sizeContracts(rounded)
                .closeFraction(fraction)
                .sizeMode(SizeMode.REDUCE_ONLY)
                .description("reduce-only algo size for " + action.getConditionType())
                .build();
    }

    private CalculatedSize fullClose(CalculationContext context) {
        Position position = requirePosition(context);
        return CalculatedSize.builder()
                .sizeContracts(position.getExternalSize())
                .closeFraction(BigDecimal.ONE)
                .sizeMode(SizeMode.FULL_CLOSE)
                .description("full position close size")
                .build();
    }

    private InstrumentExternalRules requireSpecs(CalculationContext context) {
        InstrumentExternalRules rules = context.getInstrumentExternalRules();
        if (isNull(rules) || isFalse(rules.hasSizingSpecs())) {
            throw error(MISSING_SIZE_SPECS, "Instrument sizing specs (ctVal/lotSz/minSz) missing or invalid");
        }
        return rules;
    }

    private BigDecimal clampFraction(BigDecimal fraction) {
        if (fraction.signum() < 0) {
            return BigDecimal.ZERO;
        }
        return fraction.compareTo(BigDecimal.ONE) > 0 ? BigDecimal.ONE : fraction;
    }

    private BigDecimal requireAvailableEquity(CalculationContext context) {
        if (isNull(context.getBalanceContainer())
                || isNull(context.getBalanceContainer().getExternalAvailableEquity())) {
            throw error(MISSING_BALANCE, "Available equity missing for sizing");
        }
        return context.getBalanceContainer().getExternalAvailableEquity();
    }

    private Position requirePosition(CalculationContext context) {
        Position position = context.getActivePosition();
        if (isNull(position) || isNull(position.getExternalSize())) {
            throw error(NO_POSITION, "No active position for reduce/close sizing");
        }
        return position;
    }

    private BigDecimal requireEntryPrice(CalculatedPrice calculatedPrice) {
        if (isNull(calculatedPrice) || isNull(calculatedPrice.getRoundedPrice())
                || calculatedPrice.getRoundedPrice().signum() <= 0) {
            throw error(MISSING_ENTRY_PRICE, "Entry price missing for sizing");
        }
        return calculatedPrice.getRoundedPrice();
    }

    private BigDecimal requireFraction(BigDecimal percents, String missingCode) {
        if (isNull(percents)) {
            throw error(missingCode, "Required percents missing for sizing");
        }
        return fraction(percents);
    }

    private BigDecimal fraction(BigDecimal percents) {
        return percents.divide(HUNDRED, Constants.Calc.MATH_CONTEXT);
    }

    private BigDecimal roundDownToLot(BigDecimal contracts, BigDecimal lotSize) {
        BigDecimal units = contracts.divide(lotSize, 0, RoundingMode.DOWN);
        return units.multiply(lotSize);
    }

    private BigDecimal floorAtMin(BigDecimal contracts, BigDecimal minSize) {
        if (nonNull(minSize) && contracts.compareTo(minSize) < 0) {
            return minSize;
        }
        return contracts;
    }

    private CalculationException error(String code, String message) {
        return new CalculationException(CalculationError.permanent(code, message));
    }
}
