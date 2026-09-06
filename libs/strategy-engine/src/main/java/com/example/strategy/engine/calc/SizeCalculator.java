package com.example.strategy.engine.calc;

import static com.example.strategy.engine.calc.util.CalculationErrorCodes.FEE_RATE_UNAVAILABLE;
import static com.example.strategy.engine.calc.util.CalculationErrorCodes.PROTECTION_LADDER_STEP_BELOW_MIN_SIZE;
import static com.example.strategy.engine.calc.util.CalculationErrorCodes.STOP_LEVEL_NOT_ON_LOSS_SIDE_FOR_SIZING;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyDetail;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStep;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAlgoOrderAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyOrderAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyTradeDirection;
import com.example.tradingbot.domain.model.core.instrument.InstrumentExternalRules;
import com.example.tradingbot.domain.util.DomainMath;
import com.example.tradingbot.domain.util.RiskMath;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Service;

/**
 * Считает размер действия и возвращает {@link CalculatedSize}
 * (docs/components/SizeCalculator.md). Цену не считает, но принимает
 * рассчитанную входом. Исполнимая форма — docs/spec/order-sizing.json;
 * закрытая форма убытка на стопе подключается оттуда же
 * (docs/spec/risk-at-stop.json, величина {@code lossAtStopPerUnit}).
 *
 * <p><b>Три класса размера, и они разведены не оттенком, а последствием
 * ошибки:</b>
 *
 * <ul>
 *   <li><b>вход</b> — размер под поактный потолок риска, решается ЗАКРЫТОЙ
 *       ФОРМОЙ, а не подбором: комиссия пропорциональна размеру, поэтому
 *       размер выводится из неравенства, а каждая нога комиссии идёт по
 *       своей цене;</li>
 *   <li><b>reduce-only выход</b> — пола минимального размера нет: подъём
 *       менял бы объявленное действие, и вместо пола объявляются четыре
 *       исхода {@link ExitOutcome};</li>
 *   <li><b>защитная ступень</b> — наоборот, ступень ниже минимума есть
 *       ЯВНЫЙ ОТКАЗ шага: у выхода «не исполнили» значит «ничего не
 *       произошло», у защиты то же — «стоп не поставлен», то есть
 *       благоприятное умолчание (docs/rules/absent-value-semantics.md).</li>
 * </ul>
 *
 * <p><b>Сделочные потолки калькулятор не считает</b> — их проверяет
 * преконтроль над готовым размером; подбирать размер под остаток бюджета
 * значило бы выпускать ногу заведомо меньше объявленного, не сообщая об
 * этом.
 */
@Service
public class SizeCalculator {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final String MISSING_SIZE_SPECS = "MISSING_SIZE_SPECS";
    private static final String MISSING_RISK_BASE = "MISSING_RISK_BASE";
    private static final String MISSING_RISK_LIMIT = "MISSING_RISK_LIMIT";
    private static final String MISSING_ALLOCATION = "MISSING_ALLOCATION";
    private static final String MISSING_ENTRY_PRICE = "MISSING_ENTRY_PRICE";
    private static final String MISSING_CLOSE_FRACTION = "MISSING_CLOSE_FRACTION";
    private static final String MISSING_TRANCHE_EXPOSURE = "MISSING_TRANCHE_EXPOSURE";
    private static final String MISSING_STOP_PRICE_FOR_SIZING = "MISSING_STOP_PRICE_FOR_SIZING";
    private static final String LADDER_PREVIOUS_STEPS_UNKNOWN = "LADDER_PREVIOUS_STEPS_UNKNOWN";

    public CalculatedSize calculate(CalculationContext context, CalculatedPrice price) {
        return switch (context.getAction()) {
            case StrategyAlgoOrderAction algoAction -> algoSize(algoAction, context);
            case StrategyOrderAction orderAction -> orderSize(orderAction, context, price);
            default -> CalculatedSize.builder()
                    .sizeMode(SizeMode.NOT_REQUIRED)
                    .description("size not required for action")
                    .build();
        };
    }

    private CalculatedSize orderSize(StrategyOrderAction action, CalculationContext context, CalculatedPrice price) {
        if (isTrue(action.getPositionReducingOnly())) {
            return exitSize(fraction(requireDeclared(action.getAllocationPercents(), MISSING_CLOSE_FRACTION)),
                    context, "reduce-only order exit");
        }
        return entrySize(action, context, price);
    }

    /**
     * Защитное действие — ступень лестницы, тейк — выход: классы разведены
     * закрытым селектором docs/spec/strategy-reference.json
     * ({@code isProtectiveAction}), а не суждением на месте.
     */
    private CalculatedSize algoSize(StrategyAlgoOrderAction action, CalculationContext context) {
        BigDecimal fraction = fraction(algoPercents(action));
        if (isTrue(action.isProtective())) {
            return ladderStepSize(action, fraction, context);
        }
        return exitSize(fraction, context, "reduce-only algo exit for " + action.getConditionType());
    }

    /**
     * Доля, которой объявлено действие над условной заявкой. У полного
     * типа доля есть сто процентов по самому его имени, у частичного она
     * ОБЪЯВЛЯЕТСЯ и умолчания не имеет: пустая доля, прочитанная нулём,
     * дала бы действие нулевого размера вместо отказа
     * (docs/rules/strategy-validation.md).
     */
    private BigDecimal algoPercents(StrategyAlgoOrderAction action) {
        return switch (action.getConditionType()) {
            case PARTIAL_STOP_LOSS, PARTIAL_TAKE_PROFIT ->
                    requireDeclared(action.getCloseFractionPercents(), MISSING_CLOSE_FRACTION);
            default -> HUNDRED;
        };
    }

    /**
     * Размер входа под поактный потолок риска — закрытая форма
     * docs/spec/order-sizing.json: считаются оба кандидата (по бюджету
     * риска и по доле аллокации), берётся меньший, округляется вниз по
     * шагу лота, снизу — минимальный торговый размер.
     *
     * <p><b>Потолок — предел, а не цель:</b> когда меньшим оказывается
     * кандидат по аллокации, фактический риск ноги выходит ниже потолка, и
     * это штатное следствие (docs/rules/risk-policy.md).
     */
    private CalculatedSize entrySize(StrategyOrderAction action, CalculationContext context, CalculatedPrice price) {
        InstrumentExternalRules rules = requireSpecs(context);
        BigDecimal base = requireRiskBase(context);
        BigDecimal entryAnchor = requireEntryAnchor(price);
        BigDecimal contractValue = rules.contractValue();

        BigDecimal perContractRisk = perContractRisk(context, price, rules, entryAnchor, contractValue);
        BigDecimal riskBudget = requireRiskPerActionPercent(context).multiply(base).divide(HUNDRED, DomainMath.CONTEXT);
        BigDecimal contractsFromRisk = riskBudget.divide(perContractRisk, DomainMath.CONTEXT);
        BigDecimal contractsFromAllocation = base
                .multiply(requireDeclared(action.getAllocationPercents(), MISSING_ALLOCATION))
                .divide(HUNDRED, DomainMath.CONTEXT)
                .divide(entryAnchor.multiply(contractValue), DomainMath.CONTEXT);

        BigDecimal desired = contractsFromRisk.min(contractsFromAllocation);
        BigDecimal entryContracts = floorTo(desired, rules.lotSize()).max(rules.minSize());
        return CalculatedSize.builder()
                .sizeContracts(entryContracts)
                .notionalUsdt(entryContracts.multiply(entryAnchor).multiply(contractValue))
                .sizeMode(SizeMode.OPEN_OR_INCREASE)
                .description("entry size bound by " + (contractsFromRisk.compareTo(contractsFromAllocation) < 0
                        ? "risk ceiling" : "allocation share"))
                .build();
    }

    /**
     * Убыток на стопе одного контракта — знаковая ценовая дистанция от
     * якоря плюс комиссии ОБЕИХ ног, каждая по своей цене, домноженная на
     * стоимость контракта.
     *
     * <p><b>Знаменатель охранён.</b> Величина знаковая, и неположительная
     * означает, что уровень остановки лежит не на убыточной стороне —
     * worst-case выхода у позиции нет. Без охраны деление давало бы
     * отрицательный размер, {@code min} пропускал бы его как меньший, а
     * пол минимального лота поднимал бы до минимума: вход состоялся бы с
     * отрицательным заявленным риском, пройдя и преконтроль дистанции, и
     * бюджет риска; на точном безубытке — деление на ноль. Отказ по этой
     * ветви есть сработавший контроль, а не авария: шаг не исполняется,
     * сделка в ошибочное состояние не уходит
     * (docs/processes/risk-evaluation.md).
     */
    private BigDecimal perContractRisk(CalculationContext context, CalculatedPrice price,
                                       InstrumentExternalRules rules, BigDecimal entryAnchor,
                                       BigDecimal contractValue) {
        BigDecimal stopPrice = requireStopPrice(price);
        BigDecimal feeRate = requireFeeRate(rules);
        StrategyTradeDirection direction = context.getStrategyDirection();
        BigDecimal perContract = RiskMath.lossAtStopPerUnit(direction, entryAnchor, stopPrice, feeRate)
                .multiply(contractValue);
        if (perContract.signum() <= 0) {
            throw error(STOP_LEVEL_NOT_ON_LOSS_SIDE_FOR_SIZING,
                    "Stop level is not on the loss side of the anchor: per-contract risk is " + perContract);
        }
        return perContract;
    }

    /**
     * Размер reduce-only выхода — доля экспозиции ТРАНША, округлённая
     * вниз, плюс исход округления. Пола минимального размера нет: подъём
     * размера менял бы объявленное действие — частичный выход стал бы
     * бо́льшим, вплоть до полного, и ни одна минимальная проверка «закрыли
     * больше объявленного» этого не выражает.
     *
     * <p><b>Ветвление идёт по ОСТАТКУ, а не по размеру выхода:</b> признак
     * полного выхода по доле — нулевой остаток, и он не зависит от того,
     * выразим ли сам размер минимальным торговым.
     */
    private CalculatedSize exitSize(BigDecimal fraction, CalculationContext context, String description) {
        InstrumentExternalRules rules = requireSpecs(context);
        BigDecimal exposure = requireExposure(context);
        BigDecimal exitSize = floorTo(exposure.multiply(fraction), rules.lotSize());
        BigDecimal remainder = exposure.subtract(exitSize);
        BigDecimal minSize = rules.minSize();

        ExitOutcome outcome;
        if (remainder.signum() == 0) {
            outcome = ExitOutcome.FULL_BY_FRACTION;
        } else if (remainder.compareTo(minSize) < 0) {
            outcome = ExitOutcome.FULL;
        } else if (exitSize.compareTo(minSize) >= 0) {
            outcome = ExitOutcome.PARTIAL;
        } else {
            outcome = ExitOutcome.SKIPPED;
        }
        return CalculatedSize.builder()
                .sizeContracts(exitSizeFinal(outcome, exitSize, exposure))
                .closeFraction(fraction)
                .sizeMode(SizeMode.REDUCE_ONLY)
                .exitOutcome(outcome)
                .description(description)
                .build();
    }

    /** На обоих полных исходах в заявку уезжает экспозиция транша целиком, а не округлённая доля. */
    private BigDecimal exitSizeFinal(ExitOutcome outcome, BigDecimal exitSize, BigDecimal exposure) {
        return switch (outcome) {
            case SKIPPED -> BigDecimal.ZERO;
            case PARTIAL -> exitSize;
            case FULL, FULL_BY_FRACTION -> exposure;
        };
    }

    /**
     * Размер ступени защитной лестницы. Последняя ступень набора получает
     * ОСТАТОК экспозиции транша: доли режутся вниз, и без остатка сумма
     * покрытия вышла бы меньше экспозиции — то есть транш остался бы
     * недопокрыт (docs/rules/live-risk-protection.md).
     *
     * <p><b>Ступень ниже минимального размера — явный отказ шага.</b>
     * Проверяются ОБЕ величины — объявленная доля и итоговый размер: набор,
     * чья объявленная доля неразмещаема, невыразим целиком, сколько бы ни
     * досталось его последней ступени.
     */
    private CalculatedSize ladderStepSize(StrategyAlgoOrderAction action, BigDecimal fraction,
                                          CalculationContext context) {
        InstrumentExternalRules rules = requireSpecs(context);
        BigDecimal exposure = requireExposure(context);
        BigDecimal declaredStepSize = floorTo(exposure.multiply(fraction), rules.lotSize());
        StrategyStep step = stepOf(action, context);
        BigDecimal stepSize = isLastLadderStep(action, step)
                ? exposure.subtract(requirePreviousStepsTotal(context, step))
                : declaredStepSize;
        if (declaredStepSize.min(stepSize).compareTo(rules.minSize()) < 0) {
            throw error(PROTECTION_LADDER_STEP_BELOW_MIN_SIZE,
                    "Protection ladder step is below the minimum tradable size: declared " + declaredStepSize
                            + ", step " + stepSize + ", minimum " + rules.minSize());
        }
        return CalculatedSize.builder()
                .sizeContracts(stepSize)
                .closeFraction(fraction)
                .sizeMode(SizeMode.REDUCE_ONLY)
                .description("protection ladder step")
                .build();
    }

    /** Шаг, объявивший действие; пусто — деталь не предъявлена (восстановленная сделка). */
    private StrategyStep stepOf(StrategyAlgoOrderAction action, CalculationContext context) {
        StrategyDetail detail = context.getStrategyDetail();
        return isNull(detail) ? null : detail.stepOf(action);
    }

    /** Последняя ступень набора — та, что получает остаток; шаг не предъявлен — остатка не берём. */
    private boolean isLastLadderStep(StrategyAlgoOrderAction action, StrategyStep step) {
        return nonNull(step) && action == step.lastProtectionLadderStep();
    }

    /**
     * Сумма уже поставленных ступеней набора. Набор из одной ступени даёт
     * ноль ПО ПОСТРОЕНИЮ — поставленных до неё нет; у набора из нескольких
     * пустой операнд отдал бы последней ступени всю экспозицию, то есть
     * покрыл бы чужие доли, поэтому пустота здесь отказывает.
     */
    private BigDecimal requirePreviousStepsTotal(CalculationContext context, StrategyStep step) {
        BigDecimal total = context.getLadderPreviousStepsTotal();
        if (nonNull(total)) {
            return total;
        }
        if (step.protectionLadderSteps().size() == 1) {
            return BigDecimal.ZERO;
        }
        throw error(LADDER_PREVIOUS_STEPS_UNKNOWN,
                "Ladder has several steps, but the total of the placed ones is not supplied");
    }

    private InstrumentExternalRules requireSpecs(CalculationContext context) {
        InstrumentExternalRules rules = context.getInstrumentExternalRules();
        if (isNull(rules) || isFalse(rules.hasSizingSpecs())) {
            throw error(MISSING_SIZE_SPECS, "Instrument sizing specs are missing or invalid");
        }
        return rules;
    }

    /**
     * База риска приезжает операндом: её резолв «снимок сделки, иначе
     * живая база счёта» живёт в доме-спеке docs/spec/risk-limits.json, и
     * второй его копии здесь не заводится.
     */
    private BigDecimal requireRiskBase(CalculationContext context) {
        BigDecimal base = context.getRiskBase();
        if (isNull(base) || base.signum() <= 0) {
            throw error(MISSING_RISK_BASE, "Risk base is not resolved: the per-act ceiling has no divisor");
        }
        return base;
    }

    private BigDecimal requireRiskPerActionPercent(CalculationContext context) {
        StrategyDetail detail = context.getStrategyDetail();
        BigDecimal percent = isNull(detail) ? null : detail.getRiskPerActionPercent();
        if (isNull(percent)) {
            throw error(MISSING_RISK_LIMIT, "Per-act risk ceiling is not declared by the pinned strategy detail");
        }
        return percent;
    }

    /** Себестоимость действия для сайзинга — его ПЛАНОВАЯ цена входа. */
    private BigDecimal requireEntryAnchor(CalculatedPrice price) {
        BigDecimal anchor = isNull(price) ? null : price.getRoundedPrice();
        if (isNull(anchor) || anchor.signum() <= 0) {
            throw error(MISSING_ENTRY_PRICE, "Entry price is missing for sizing");
        }
        return anchor;
    }

    /**
     * Уровень остановки убытка входа. Пусто — размера по риску не
     * существует, и вход НЕ сайзится долей аллокации: он состоялся бы без
     * известного worst-case выхода (docs/concept.md, П1 следствие 1).
     */
    private BigDecimal requireStopPrice(CalculatedPrice price) {
        BigDecimal stopPrice = isNull(price) || isNull(price.getStopLossPrice())
                ? null
                : price.getStopLossPrice().getTriggerPrice();
        if (isNull(stopPrice)) {
            throw error(MISSING_STOP_PRICE_FOR_SIZING,
                    "Entry action declares no stop level: the worst-case exit is unknown");
        }
        return stopPrice;
    }

    private BigDecimal requireFeeRate(InstrumentExternalRules rules) {
        BigDecimal feeRate = rules.takerFeeRate();
        if (isNull(feeRate)) {
            throw error(FEE_RATE_UNAVAILABLE, "Taker fee rate is not resolved: both legs of the cost are unknown");
        }
        return feeRate;
    }

    /** Экспозиция транша, которому принадлежит действие. */
    private BigDecimal requireExposure(CalculationContext context) {
        DealTranche tranche = context.getDealTranche();
        if (isNull(tranche)) {
            throw error(MISSING_TRANCHE_EXPOSURE, "Deal tranche is not supplied: the exposure has no owner");
        }
        return tranche.exposure();
    }

    private BigDecimal requireDeclared(BigDecimal percents, String missingCode) {
        if (isNull(percents)) {
            throw error(missingCode, "Required percents are not declared by the action");
        }
        return percents;
    }

    private BigDecimal fraction(BigDecimal percents) {
        return percents.divide(HUNDRED, DomainMath.CONTEXT);
    }

    private BigDecimal floorTo(BigDecimal value, BigDecimal step) {
        return value.divide(step, 0, RoundingMode.DOWN).multiply(step);
    }

    private CalculationException error(String code, String message) {
        return new CalculationException(CalculationError.permanent(code, message));
    }
}
