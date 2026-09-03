package com.example.tradingbot.domain.command.risk;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isNotTrue;
import static org.apache.commons.lang3.BooleanUtils.isTrue;
import static org.apache.commons.lang3.StringUtils.isBlank;

import com.example.tradingbot.config.RiskAppetiteProperties;
import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.command.calc.CalculatedPrice;
import com.example.tradingbot.domain.command.calc.CalculatedSize;
import com.example.tradingbot.domain.command.calc.CalculatedStrategyAction;
import com.example.tradingbot.domain.command.calc.PriceMode;
import com.example.tradingbot.domain.command.calc.ResolvedStopLossPrice;
import com.example.tradingbot.domain.command.calc.ResolvedTakeProfitPrice;
import com.example.tradingbot.domain.command.risk.RiskCheckResult.RiskCheckCode;
import com.example.tradingbot.domain.command.risk.RiskCheckResult.RiskCheckStatus;
import com.example.tradingbot.domain.command.risk.RiskValidationResult.RiskDecision;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyDetail;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyLevelSource;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyOrderAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyPlacementRole;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyTradeDirection;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.core.instrument.InstrumentExternalRules;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingbot.persistence.service.InstrumentExternalRulesDataService;
import com.example.tradingbot.util.Constants;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Проверяет уже рассчитанное risk-creating/increasing/weakening действие
 * по risk-policy и отвечает «разрешено ли действие?», возвращая
 * {@link RiskValidationResult} (docs/components/RiskValidator.md). Сам
 * считает нужные risk-метрики. Читает persisted InstrumentExternalRules —
 * в биржу не ходит; статус сделки не меняет и команд не создаёт.
 *
 * <p><b>Делитель ВСЕХ ЧЕТЫРЁХ потолков один</b> — база риска: снимок
 * сделки, если он есть, иначе живая база счёта. Развилка не
 * стилистическая: снимок пишет создатель ноги той же транзакцией, что
 * заводит ногу, а преконтроль идёт ДО неё — на ПЕРВОМ действии сделки
 * делителя-снимка не существует (docs/spec/risk-limits.json §base).
 *
 * <p><b>Входной гейт — полнота графа, и он fail-fast.</b> На неполном
 * графе операнды потолков занижены, то есть преконтроль разрешал бы
 * действие, которое потолок обязан отвергнуть; ответ по загруженному
 * подмножеству был бы ошибкой в разрешающую сторону, а пустота нулём не
 * подменяется (docs/components/RiskValidator.md).
 */
@Component
@RequiredArgsConstructor
public class RiskValidator {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final InstrumentExternalRulesDataService rulesDataService;
    private final RiskAppetiteProperties riskAppetite;

    public RiskValidationResult validate(CalculatedStrategyAction calculatedAction, DealContext dealContext) {
        List<RiskCheckResult> checks = new ArrayList<>();
        CalculatedSize size = calculatedAction.getCalculatedSize();
        CalculatedPrice price = calculatedAction.getCalculatedPrice();

        // Входной гейт полноты графа стои́т ПЕРВЫМ и отказывает fail-fast:
        // на неполном графе операнды потолков занижены, и любая проверка
        // после него отвечала бы по загруженному подмножеству.
        if (isFalse(dealContext.getGraphComplete())) {
            return blockedResult(checks, RiskCheckCode.DEAL_GRAPH_INCOMPLETE,
                    "Deal graph is not fully presented by the pass context");
        }
        if (isNull(size) || isNull(size.getSizeContracts()) || size.getSizeContracts().signum() <= 0) {
            return blockedResult(checks, RiskCheckCode.CALCULATED_ACTION_INVALID,
                    "Calculated size missing or non-positive");
        }
        InstrumentExternalRules rules = rulesDataService
                .findByInstrumentId(dealContext.getInstrument().getId())
                .orElse(null);
        if (isNull(rules)) {
            return blockedResult(checks, RiskCheckCode.INSTRUMENT_RULES_MISSING,
                    "Instrument external rules not materialized");
        }
        // Расчётная валюта — та, в которой меряются все числа риска:
        // инструмент без неё к торговле не допускается.
        if (isBlank(dealContext.getInstrument().getExternalSettlementCurrency())) {
            return blockedResult(checks, RiskCheckCode.INSTRUMENT_SETTLE_CURRENCY_MISSING,
                    "Instrument settlement currency is not resolved");
        }
        BigDecimal base = riskBase(dealContext);
        if (isNull(base) || base.signum() <= 0) {
            return blockedResult(checks, RiskCheckCode.BALANCE_INVALID,
                    "Risk base is missing or non-positive");
        }
        // Энфорсера остановки по серии убытков не существует, пока порог не
        // задан: провизорное число выглядит контролем, не будучи им
        // (docs/rules/loss-streak-halt.md §«Пустое место — отказ»).
        if (isNull(riskAppetite.getGlobalConsecutiveLossLimit())) {
            return blockedResult(checks, RiskCheckCode.LOSS_LIMIT_NOT_CONFIGURED,
                    "globalConsecutiveLossLimit is not configured");
        }
        if (isNull(riskAppetite.getGlobalSimultaneousRiskPerDealPercent())) {
            return blockedResult(checks, RiskCheckCode.RISK_APPETITE_NOT_CONFIGURED,
                    "globalSimultaneousRiskPerDealPercent is not configured");
        }

        BigDecimal sizeContracts = size.getSizeContracts();
        Position position = dealContext.getDeal().livePosition();
        StrategyTradeDirection direction = dealContext.getDeal().getDirection();
        BigDecimal entryReference = entryReference(position, price);

        checkInstrumentLive(rules, checks);
        checkMarginMode(dealContext.getInstrument(), checks);
        checkSizeBounds(rules, sizeContracts, price, checks);
        checkExchangeMaxLeverage(dealContext.getInstrument(), rules, checks);
        checkFeeRate(price, rules, checks);
        checkRiskCreatingEntryProtection(calculatedAction, checks);
        checkStopLossSide(calculatedAction.getSourceAction(), price.getStopLossPrice(), entryReference,
                direction, checks);
        checkStopDistanceFloor(price.getStopLossPrice(), entryReference, direction, rules, checks);
        checkTakeProfitSide(price.getTakeProfitPrice(), entryReference, direction, checks);
        checkLiquidationGuard(price.getStopLossPrice(), position, direction, checks);
        checkCollapseWindow(calculatedAction, dealContext, checks);
        checkSafetyRung(calculatedAction, dealContext, checks);
        checkCeilings(calculatedAction, dealContext, rules, base, entryReference, checks);

        return aggregate(checks);
    }

    /**
     * Делитель всех четырёх потолков: снимок базы, если он есть, иначе
     * живая база счёта. Снимок пишет создатель ноги ПОСЛЕ преконтроля,
     * поэтому на первом действии сделки его ещё не существует, и делит
     * живая база (docs/spec/risk-limits.json §base).
     */
    private BigDecimal riskBase(DealContext dealContext) {
        BigDecimal frozen = dealContext.getDeal().getPlannedRiskEquityBase();
        if (nonNull(frozen)) {
            return frozen;
        }
        return nonNull(dealContext.getExchange()) ? dealContext.getExchange().getRiskBase() : null;
    }

    /**
     * Четыре потолка риска и катастрофический потолок нотинала. Все пять
     * неравенств считаются от ОДНОЙ базы и от операндов, взятых по графу
     * в точке проверки (docs/spec/risk-limits.json).
     *
     * <p><b>Слагаемое проверяемого акта обязательно</b>: без него первый
     * вход сравнивал бы с потолком ноль и проходил любым размером —
     * потолок, заведённый против шокового хода, был бы инертен ровно
     * там, где решается размер.
     */
    private void checkCeilings(CalculatedStrategyAction calculatedAction, DealContext dealContext,
                               InstrumentExternalRules rules, BigDecimal base, BigDecimal entryReference,
                               List<RiskCheckResult> checks) {
        StrategyDetail detail = dealContext.getStrategyDetail();
        if (isNull(detail) || isNull(detail.getRiskPerActionPercent())) {
            return;
        }
        BigDecimal actRisk = actRisk(calculatedAction, dealContext, rules, entryReference);
        BigDecimal actNotional = actNotional(calculatedAction, rules, entryReference);

        checkAgainst(actRisk, percentOf(detail.getRiskPerActionPercent(), base),
                RiskCheckCode.RISK_PER_ACTION_EXCEEDED, "per-action risk ceiling", checks);
        if (nonNull(detail.getCumulativeRiskPerDealMultiplier())) {
            BigDecimal cumulative = percentOf(detail.getRiskPerActionPercent(), base)
                    .multiply(detail.getCumulativeRiskPerDealMultiplier());
            checkAgainst(zeroIfNull(dealContext.getDeal().getPlannedRiskAmount()).add(actRisk), cumulative,
                    RiskCheckCode.RISK_PER_DEAL_CUMULATIVE_EXCEEDED, "cumulative risk ceiling", checks);
        }
        BigDecimal liveRiskNow = liveRiskNow(dealContext, rules, entryReference);
        if (nonNull(detail.getStrategySimultaneousRiskPerDealPercent())) {
            checkAgainst(liveRiskNow.add(actRisk),
                    percentOf(detail.getStrategySimultaneousRiskPerDealPercent(), base),
                    RiskCheckCode.RISK_PER_DEAL_SIMULTANEOUS_EXCEEDED, "strategy simultaneous ceiling", checks);
        }
        checkAgainst(liveRiskNow.add(actRisk),
                percentOf(riskAppetite.getGlobalSimultaneousRiskPerDealPercent(), base),
                RiskCheckCode.RISK_PER_DEAL_SIMULTANEOUS_GLOBAL_EXCEEDED, "global simultaneous ceiling", checks);
        checkCatastrophicNotional(dealContext, detail, rules, base, entryReference, actNotional, checks);
    }

    /**
     * Катастрофический потолок: максимальный риск на сделку, растянутый
     * множителем стратегии; он же кэп суммарного нотинала — худший
     * мыслимый ход принят равным 100 %. Незаданный множитель ОТКАЗЫВАЕТ
     * вычислением, а не пропускает действие.
     */
    private void checkCatastrophicNotional(DealContext dealContext, StrategyDetail detail,
                                           InstrumentExternalRules rules, BigDecimal base,
                                           BigDecimal entryReference, BigDecimal actNotional,
                                           List<RiskCheckResult> checks) {
        if (isNull(detail.getStrategyCatastrophicRiskPerDealMultiplier())) {
            checks.add(RiskCheckResult.blocked(RiskCheckCode.RISK_APPETITE_NOT_CONFIGURED,
                    "strategyCatastrophicRiskPerDealMultiplier is not declared", null));
            return;
        }
        BigDecimal ceiling = percentOf(riskAppetite.getGlobalSimultaneousRiskPerDealPercent(), base)
                .multiply(detail.getStrategyCatastrophicRiskPerDealMultiplier());
        checkAgainst(dealNotional(dealContext, rules, entryReference).add(actNotional), ceiling,
                RiskCheckCode.DEAL_NOTIONAL_EXCEEDED, "catastrophic notional ceiling", checks);
    }

    /**
     * Что стои́т под ударом сейчас: неисполненная доля живых входных ног
     * плюс живой эпизод до действующей защиты. Оба слагаемых считаются
     * по графу в точке проверки.
     */
    private BigDecimal liveRiskNow(DealContext dealContext, InstrumentExternalRules rules,
                                   BigDecimal entryReference) {
        BigDecimal unfilled = liveEntryLegs(dealContext).stream()
                .map(this::legUnfilledRisk)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return unfilled.add(livePositionRiskAtStop(dealContext, rules, entryReference));
    }

    /** Заявленный риск ноги в её НЕИСПОЛНЕННОЙ доле. */
    private BigDecimal legUnfilledRisk(Order leg) {
        BigDecimal planned = zeroIfNull(leg.getPlannedSizeContracts());
        if (planned.signum() == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal unfilled = planned.subtract(zeroIfNull(leg.getAccumulatedFillSize()));
        return zeroIfNull(leg.getPlannedRiskAmount())
                .multiply(unfilled)
                .divide(planned, Constants.Calc.MATH_CONTEXT);
    }

    /**
     * Живой эпизод до действующей защиты, с round-trip комиссией. Клэмп
     * нулём здесь, а не на сумме: стоп за безубытком гасит СВОЁ
     * слагаемое, а не чужие.
     */
    private BigDecimal livePositionRiskAtStop(DealContext dealContext, InstrumentExternalRules rules,
                                              BigDecimal entryReference) {
        Position live = dealContext.getDeal().livePosition();
        if (isNull(live) || isNull(live.getExternalSize()) || isNull(entryReference)
                || isNull(rules.contractValue())) {
            return BigDecimal.ZERO;
        }
        BigDecimal stop = dealContext.getDeal().getTranches().stream()
                .map(tranche -> tranche.worstActiveStopLevel(dealContext.getDeal().getDirection()))
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
        BigDecimal risk = DealRiskNumbers.plannedRisk(dealContext.getDeal().getDirection(), entryReference,
                stop, rules.takerFeeRate(), live.getExternalSize(), rules.contractValue());
        if (isNull(risk)) {
            return BigDecimal.ZERO;
        }
        return risk.signum() > 0 ? risk : BigDecimal.ZERO;
    }

    /** Экспозиция сделки ДО акта: неисполненная доля живых ног плюс живой эпизод. */
    private BigDecimal dealNotional(DealContext dealContext, InstrumentExternalRules rules,
                                    BigDecimal entryReference) {
        if (isNull(rules.contractValue())) {
            return BigDecimal.ZERO;
        }
        BigDecimal legs = liveEntryLegs(dealContext).stream()
                .map(leg -> zeroIfNull(leg.getPlannedSizeContracts())
                        .subtract(zeroIfNull(leg.getAccumulatedFillSize()))
                        .multiply(rules.contractValue())
                        .multiply(zeroIfNull(leg.getPlannedEntryPrice())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Position live = dealContext.getDeal().livePosition();
        if (isNull(live) || isNull(live.getExternalSize()) || isNull(entryReference)) {
            return legs;
        }
        return legs.add(live.getExternalSize().multiply(rules.contractValue()).multiply(entryReference));
    }

    /** Живые ВХОДНЫЕ ноги сделки — по всем траншам: потолки агрегатные. */
    private List<Order> liveEntryLegs(DealContext dealContext) {
        return emptyIfNull(dealContext.getDeal().getOrders()).stream()
                .filter(order -> isTrue(order.isLive()))
                .filter(order -> isNotTrue(order.getPositionReducingOnly()))
                .collect(Collectors.toList());
    }

    /**
     * Риск проверяемого акта: плановый риск ноги для risk-creating, ноль
     * для risk-weakening — новых контрактов такое действие не создаёт.
     */
    private BigDecimal actRisk(CalculatedStrategyAction calculatedAction, DealContext dealContext,
                               InstrumentExternalRules rules, BigDecimal entryReference) {
        if (isFalse(isRiskCreatingEntry(calculatedAction.getSourceAction()))) {
            return BigDecimal.ZERO;
        }
        ResolvedStopLossPrice stop = calculatedAction.getCalculatedPrice().getStopLossPrice();
        BigDecimal risk = DealRiskNumbers.plannedRisk(dealContext.getDeal().getDirection(), entryReference,
                isNull(stop) ? null : stop.getTriggerPrice(), rules.takerFeeRate(),
                calculatedAction.getCalculatedSize().getSizeContracts(), rules.contractValue());
        return isNull(risk) ? BigDecimal.ZERO : risk;
    }

    /** Нотинал проверяемого акта; risk-weakening контрактов не создаёт. */
    private BigDecimal actNotional(CalculatedStrategyAction calculatedAction, InstrumentExternalRules rules,
                                   BigDecimal entryReference) {
        if (isFalse(isRiskCreatingEntry(calculatedAction.getSourceAction()))
                || isNull(rules.contractValue()) || isNull(entryReference)) {
            return BigDecimal.ZERO;
        }
        return calculatedAction.getCalculatedSize().getSizeContracts()
                .multiply(rules.contractValue()).multiply(entryReference);
    }

    /**
     * Набор риска в окне сворачивания отвергается ПРЕКОНТРОЛЕМ, а не
     * только статусным ребром: действие, статус не двигающее — добор
     * объёма, замещение с увеличением, — до ребра не доходит вовсе
     * (docs/rules/exit-teardown-order.md). Признак приходит контекстом
     * прохода; преконтроль его не выводит.
     */
    private void checkCollapseWindow(CalculatedStrategyAction calculatedAction, DealContext dealContext,
                                     List<RiskCheckResult> checks) {
        if (isFalse(isRiskCreatingEntry(calculatedAction.getSourceAction()))) {
            return;
        }
        if (Deal.Status.EXIT_PENDING.equals(dealContext.getDeal().getStatus())) {
            checks.add(RiskCheckResult.blocked(RiskCheckCode.RISK_CREATING_UNDER_COLLAPSE,
                    "Risk-creating act while the deal is collapsing", null));
        }
    }

    /**
     * Блок-сет стоящей safety-ступени инструмента: акт блокируемого
     * класса отвергается, реакция — карв-аут, возобновление — снятием
     * ступени (docs/rules/instrument-hold.md §Enforcement). Снятие риска
     * и reduce-only-выход в блок-сет не входят: сделки доживают под
     * своей защитой.
     */
    private void checkSafetyRung(CalculatedStrategyAction calculatedAction, DealContext dealContext,
                                 List<RiskCheckResult> checks) {
        if (isFalse(dealContext.getInstrument().hasStandingSafetyRung())) {
            return;
        }
        checks.add(RiskCheckResult.blocked(RiskCheckCode.INSTRUMENT_SAFETY_HOLD,
                "Instrument stands in a safety rung whose block set covers this act class", null));
    }

    /**
     * Пол дистанции стопа: уровень на убыточной стороне не ближе якоря,
     * чем round-trip комиссия — иначе стоп срабатывает в убыток даже без
     * движения цены (docs/spec/stop-distance.json §stopDistanceAboveFloor).
     * Проверяется на ЛЮБОЙ постановке и переносе уровня; стоп на
     * прибыльной стороне под пол не подпадает — там дистанция знаково
     * отрицательна, и пол к ней неприменим.
     */
    private void checkStopDistanceFloor(ResolvedStopLossPrice stopLoss, BigDecimal entryReference,
                                        StrategyTradeDirection direction, InstrumentExternalRules rules,
                                        List<RiskCheckResult> checks) {
        if (isNull(stopLoss) || isNull(stopLoss.getTriggerPrice()) || isNull(entryReference)
                || isNull(rules.takerFeeRate())) {
            return;
        }
        BigDecimal trigger = stopLoss.getTriggerPrice();
        BigDecimal signedDistance = StrategyTradeDirection.LONG.equals(direction)
                ? entryReference.subtract(trigger)
                : trigger.subtract(entryReference);
        if (signedDistance.signum() <= 0) {
            return;
        }
        BigDecimal floor = rules.takerFeeRate().multiply(entryReference.add(trigger));
        if (signedDistance.compareTo(floor) < 0) {
            checks.add(RiskCheckResult.blocked(RiskCheckCode.STOP_DISTANCE_BELOW_FLOOR,
                    "Stop distance below round-trip fee floor " + floor, signedDistance));
        }
    }

    /** Неравенство потолка: превышение — отказ своим кодом. */
    private void checkAgainst(BigDecimal actual, BigDecimal ceiling, RiskCheckCode code, String label,
                              List<RiskCheckResult> checks) {
        if (isNull(ceiling)) {
            return;
        }
        if (actual.compareTo(ceiling) > 0) {
            checks.add(RiskCheckResult.blocked(code, label + " exceeded: " + actual + " > " + ceiling, actual));
        }
    }

    private BigDecimal percentOf(BigDecimal percent, BigDecimal base) {
        return isNull(percent) ? null : percent.divide(HUNDRED, Constants.Calc.MATH_CONTEXT).multiply(base);
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return nonNull(value) ? value : BigDecimal.ZERO;
    }

    /**
     * Ветка ослабления защиты для СНЯТИЯ отдельной защиты при живой
     * экспозиции (docs/rules/risk-validator-scope.md): снятие риск не
     * снимает, а увеличивает. Операнд — покрытие транша после того, как
     * снимаемая защита исчезнет (docs/spec/protection-coverage.json,
     * величина {@code removalAllowed}); ниже экспозиции этого транша —
     * отказ {@code PROTECTION_COVERAGE_REDUCED}. Защиты соседних траншей
     * в операнд не входят.
     *
     * <p>Предикат, отказавший вычислением, даёт
     * {@code DEAL_GRAPH_INCOMPLETE}: пустота нулём не подменяется, иначе
     * сравнение с нулём разрешило бы снятие последней защиты над живой
     * экспозицией.
     */
    public RiskValidationResult validateProtectionRemoval(DealTranche tranche, Long algoOrderId) {
        Boolean allowed = tranche.removalAllowed(algoOrderId);
        if (isNull(allowed)) {
            return blockedResult(new ArrayList<>(), RiskCheckCode.DEAL_GRAPH_INCOMPLETE,
                    "Tranche graph incomplete: live protection with no orders presented");
        }
        if (isFalse(allowed)) {
            return blockedResult(new ArrayList<>(), RiskCheckCode.PROTECTION_COVERAGE_REDUCED,
                    "Coverage after removal " + tranche.coverageWithoutAlgoOrder(algoOrderId)
                            + " below tranche exposure " + tranche.exposure());
        }
        return aggregate(new ArrayList<>());
    }

    /**
     * Ставка комиссии нужна всякому действию, которое СТАВИТ либо
     * ПЕРЕНОСИТ уровень остановки убытка: комиссия входит в убыток на
     * стопе, и без неё величина риска неполна
     * (docs/components/RiskValidator.md). Подставленное число выглядит
     * фактом, не будучи им, и ошибается асимметрично: заниженная ставка
     * освобождает бюджет риска и даёт позицию больше положенной.
     *
     * <p>Действию, уровня не касающемуся, уровневые потребители ставки не
     * нужны; живое слагаемое одновременного потолка её потребует своим
     * заходом, и проверка расширится вместе с ним.
     */
    private void checkFeeRate(CalculatedPrice price, InstrumentExternalRules rules,
                              List<RiskCheckResult> checks) {
        if (isNull(price) || isNull(price.getStopLossPrice())
                || isNull(price.getStopLossPrice().getTriggerPrice())) {
            return;
        }
        if (isNull(rules.takerFeeRate())) {
            checks.add(RiskCheckResult.blocked(RiskCheckCode.FEE_RATE_UNAVAILABLE,
                    "Taker fee rate is not resolved for the instrument fee group", null));
        }
    }

    private void checkInstrumentLive(InstrumentExternalRules rules, List<RiskCheckResult> checks) {
        if (isFalse(rules.isLive())) {
            checks.add(RiskCheckResult.blocked(RiskCheckCode.INSTRUMENT_NOT_LIVE,
                    "Instrument not tradeable: " + rules.getStatus(), null));
        }
    }

    private void checkMarginMode(Instrument instrument, List<RiskCheckResult> checks) {
        if (isFalse(Instrument.MarginMode.ISOLATED.equals(instrument.getMarginMode()))) {
            checks.add(RiskCheckResult.blocked(RiskCheckCode.MARGIN_MODE_NOT_ISOLATED,
                    "Margin mode not isolated: " + instrument.getMarginMode(), null));
        }
    }

    private void checkSizeBounds(InstrumentExternalRules rules, BigDecimal sizeContracts, CalculatedPrice price,
                                 List<RiskCheckResult> checks) {
        BigDecimal minSize = rules.minSize();
        if (nonNull(minSize) && sizeContracts.compareTo(minSize) < 0) {
            checks.add(RiskCheckResult.blocked(RiskCheckCode.SIZE_BELOW_MIN,
                    "Size below instrument minimum " + minSize, sizeContracts));
        }
        BigDecimal lotSize = rules.lotSize();
        if (nonNull(lotSize) && lotSize.signum() > 0 && sizeContracts.remainder(lotSize).signum() != 0) {
            checks.add(RiskCheckResult.blocked(RiskCheckCode.SIZE_LOT_STEP_INVALID,
                    "Size not a multiple of lot step " + lotSize, sizeContracts));
        }
        BigDecimal maxSize = applicableMaxSize(rules, price);
        if (nonNull(maxSize) && sizeContracts.compareTo(maxSize) > 0) {
            checks.add(RiskCheckResult.blocked(RiskCheckCode.SIZE_ABOVE_LIMIT,
                    "Size above per-order limit " + maxSize, sizeContracts));
        }
    }

    private void checkExchangeMaxLeverage(Instrument instrument, InstrumentExternalRules rules,
                                          List<RiskCheckResult> checks) {
        BigDecimal maxLeverage = rules.maxLeverage();
        if (isNull(instrument.getLeverage()) || isNull(maxLeverage)) {
            return;
        }
        BigDecimal leverage = new BigDecimal(instrument.getLeverage());
        if (leverage.compareTo(maxLeverage) > 0) {
            checks.add(RiskCheckResult.blocked(RiskCheckCode.EXCHANGE_MAX_LEVERAGE_EXCEEDED,
                    "Leverage above exchange max " + maxLeverage, leverage));
        }
    }

    /**
     * Инвариант docs/rules/risk-creating-entry-protection.md: risk-creating вход
     * (открытие/наращивание позиции, не reduce-only) без резолвимого стопа
     * блокируется — без fail-open allocation-сайзинга в обход RISK_PER_TRADE.
     * Резолвимый стоп = attached SL / иной механизм, давший цену стопа в
     * CalculatedPrice. Reduce-only/закрывающие действия правило не трогает.
     */
    private void checkRiskCreatingEntryProtection(CalculatedStrategyAction calculatedAction,
                                                  List<RiskCheckResult> checks) {
        if (isFalse(isRiskCreatingEntry(calculatedAction.getSourceAction()))) {
            return;
        }
        ResolvedStopLossPrice stopLoss = calculatedAction.getCalculatedPrice().getStopLossPrice();
        if (isNull(stopLoss) || isNull(stopLoss.getTriggerPrice())) {
            checks.add(RiskCheckResult.blocked(RiskCheckCode.RISK_CREATING_ENTRY_WITHOUT_STOP,
                    "Risk-creating entry without resolvable stop-loss", null));
        }
    }

    /** Risk-creating вход — order-action, открывающий/наращивающий позицию (не reduce-only). */
    private Boolean isRiskCreatingEntry(StrategyAction action) {
        if (action instanceof StrategyOrderAction orderAction) {
            return isNotTrue(orderAction.getPositionReducingOnly());
        }
        return false;
    }

    /**
     * Первичный уровень остановки убытка обязан лечь на УБЫТОЧНУЮ сторону
     * от якоря — иначе worst-case выхода у позиции нет
     * (docs/spec/stop-distance.json, величина
     * {@code primaryStopOnLossSide}).
     *
     * <p><b>Область ограничена двумя осями.</b> Перенос уже стоящего
     * уровня (`TRANSFER`) под него не подпадает: перевод в безубыток и
     * трейлинг за безубыток — прямое назначение переноса. Защитное
     * создание с НАБЛЮДАЕМЫМ уровнем (трейлинг) — тоже: уровня в момент
     * постановки у него нет вовсе, и требовать стороны значило бы
     * отвергать ступень, ради которой держится прибыль. Дыры это не
     * открывает — такое создание охраняет инвариант покрытия: снять
     * прежнюю защиту, пока трейлинг уровня не показал, не даёт
     * {@code removalAllowed}.
     */
    private void checkStopLossSide(StrategyAction action, ResolvedStopLossPrice stopLoss, BigDecimal entryReference,
                                   StrategyTradeDirection direction, List<RiskCheckResult> checks) {
        if (isNull(stopLoss) || isNull(stopLoss.getTriggerPrice()) || isNull(entryReference)) {
            return;
        }
        if (isNull(action) || isFalse(StrategyPlacementRole.PRIMARY.equals(action.placementRole()))
                || isFalse(StrategyLevelSource.DECLARED.equals(action.levelSource()))) {
            return;
        }
        BigDecimal trigger = stopLoss.getTriggerPrice();
        boolean invalid = StrategyTradeDirection.LONG.equals(direction)
                ? trigger.compareTo(entryReference) >= 0
                : trigger.compareTo(entryReference) <= 0;
        if (invalid) {
            checks.add(RiskCheckResult.blocked(RiskCheckCode.STOP_LOSS_INVALID_SIDE,
                    "Stop-loss on wrong side of entry " + entryReference, trigger));
        }
    }

    private void checkTakeProfitSide(ResolvedTakeProfitPrice takeProfit, BigDecimal entryReference,
                                     StrategyTradeDirection direction, List<RiskCheckResult> checks) {
        if (isNull(takeProfit) || isNull(takeProfit.getTriggerPrice()) || isNull(entryReference)) {
            return;
        }
        BigDecimal trigger = takeProfit.getTriggerPrice();
        boolean invalid = StrategyTradeDirection.LONG.equals(direction)
                ? trigger.compareTo(entryReference) <= 0
                : trigger.compareTo(entryReference) >= 0;
        if (invalid) {
            checks.add(RiskCheckResult.blocked(RiskCheckCode.TAKE_PROFIT_INVALID_SIDE,
                    "Take-profit on wrong side of entry " + entryReference, trigger));
        }
    }

    private void checkLiquidationGuard(ResolvedStopLossPrice stopLoss, Position position,
                                       StrategyTradeDirection direction, List<RiskCheckResult> checks) {
        if (isNull(stopLoss) || isNull(stopLoss.getTriggerPrice())
                || isNull(position) || isNull(position.getExternalLiquidationPrice())) {
            return;
        }
        BigDecimal trigger = stopLoss.getTriggerPrice();
        BigDecimal liquidation = position.getExternalLiquidationPrice();
        boolean tooClose = StrategyTradeDirection.LONG.equals(direction)
                ? trigger.compareTo(liquidation) <= 0
                : trigger.compareTo(liquidation) >= 0;
        if (tooClose) {
            checks.add(RiskCheckResult.blocked(RiskCheckCode.STOP_LOSS_TOO_CLOSE_TO_LIQUIDATION,
                    "Stop-loss beyond liquidation price " + liquidation, trigger));
        }
    }

    /** Цена-ориентир входа: средняя цена открытой позиции либо рассчитанная цена входа. */
    private BigDecimal entryReference(Position position, CalculatedPrice price) {
        if (nonNull(position) && nonNull(position.getExternalAverageEntryPrice())) {
            return position.getExternalAverageEntryPrice();
        }
        return isNull(price) ? null : price.getRoundedPrice();
    }

    /** Per-order лимит размера по режиму цены: EXPLICIT → limit-лимит, иначе → market-лимит. */
    private BigDecimal applicableMaxSize(InstrumentExternalRules rules, CalculatedPrice price) {
        if (nonNull(price) && PriceMode.EXPLICIT.equals(price.getPriceMode())) {
            return rules.maxLimitSize();
        }
        return rules.maxMarketSize();
    }

    private RiskValidationResult blockedResult(List<RiskCheckResult> checks, RiskCheckCode code, String comment) {
        checks.add(RiskCheckResult.blocked(code, comment, null));
        return RiskValidationResult.builder()
                .decision(RiskDecision.BLOCKED)
                .checks(checks)
                .comment(comment)
                .build();
    }

    private RiskValidationResult aggregate(List<RiskCheckResult> checks) {
        RiskDecision decision = RiskDecision.ALLOWED;
        if (checks.stream().anyMatch(check -> RiskCheckStatus.BLOCKED.equals(check.getStatus()))) {
            decision = RiskDecision.BLOCKED;
        } else if (checks.stream().anyMatch(check -> RiskCheckStatus.WARNING.equals(check.getStatus()))) {
            decision = RiskDecision.WARNING;
        }
        return RiskValidationResult.builder()
                .decision(decision)
                .checks(checks)
                .comment("risk validation " + decision)
                .build();
    }
}
