package com.example.tradingcore.domain.command.risk;

import static java.math.BigDecimal.ZERO;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isNotTrue;
import static org.apache.commons.lang3.BooleanUtils.isTrue;
import static org.apache.commons.lang3.StringUtils.isBlank;

import com.example.strategy.engine.calc.CalculatedPrice;
import com.example.strategy.engine.calc.CalculatedSize;
import com.example.strategy.engine.calc.CalculatedStrategyAction;
import com.example.strategy.engine.calc.PriceMode;
import com.example.strategy.engine.calc.ResolvedStopLossPrice;
import com.example.strategy.engine.calc.ResolvedTakeProfitPrice;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyDetail;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAlgoOrderAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyLevelSource;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyOrderAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyPlacementRole;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyTradeDirection;
import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import com.example.tradingbot.domain.model.core.instrument.InstrumentExternalRules;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingbot.domain.model.core.tenant.Tenant;
import com.example.tradingbot.domain.util.DomainMath;
import com.example.tradingbot.domain.util.RiskMath;
import com.example.tradingcore.domain.account.AccountInstrumentState;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.risk.RiskCheckResult.RiskCheckCode;
import com.example.tradingcore.domain.command.risk.RiskCheckResult.RiskCheckStatus;
import com.example.tradingcore.domain.command.risk.RiskValidationResult.RiskDecision;
import com.example.tradingcore.persistence.service.AccountInstrumentStateDataService;
import com.example.tradingcore.persistence.service.InstrumentExternalRulesDataService;
import com.example.tradingcore.persistence.service.TenantRiskAppetiteDataService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Отвечает на вопрос «разрешено ли это рассчитанное действие» по
 * риск-политике, возвращая {@link RiskValidationResult}
 * (docs/components/RiskValidator.md). Нужные метрики считает сам; статус
 * сделки не меняет и команд не создаёт.
 *
 * <p><b>Три операнда валидатор читает СВОЕЙ тропой, а не аргументом:</b>
 * справочные правила инструмента (со ставкой, налитой границей навеса),
 * состояние пары «счёт, инструмент» и числа риск-аппетита тенанта.
 * Гидрация ставки в фабрике контекста расчёта накрыла бы только тропу
 * калькуляторов, и преконтроль блокировал бы каждый вход отсутствием
 * ставки.
 *
 * <p><b>Делитель ВСЕХ ЧЕТЫРЁХ потолков один</b> — база риска: снимок
 * сделки, если он есть, иначе живая база счёта. Развилка не
 * стилистическая: снимок пишет создатель ноги той же транзакцией, что
 * заводит ногу, а преконтроль идёт ДО неё — на ПЕРВОМ действии сделки
 * делителя-снимка не существует (docs/spec/risk-limits.json, величина
 * {@code base}).
 *
 * <p><b>Входной гейт — полнота графа, и он fail-fast.</b> На неполном
 * графе операнды потолков занижены, то есть преконтроль разрешал бы
 * действие, которое потолок обязан отвергнуть; ответ по загруженному
 * подмножеству был бы ошибкой в разрешающую сторону, а пустота нулём не
 * подменяется.
 *
 * <p><b>Незаданное число ОТКАЗЫВАЕТ вычислением, а не пропускает
 * действие.</b> Правило общее и на числа риск-аппетита тенанта, и на
 * числа, объявленные деталью стратегии: неравенство, которое не на чем
 * посчитать, не проверено, а непроверенное благоприятным умолчанием не
 * читается (docs/concept.md П1).
 */
@Component
@RequiredArgsConstructor
public class RiskValidator {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final InstrumentExternalRulesDataService rulesDataService;
    private final AccountInstrumentStateDataService accountInstrumentStateDataService;
    private final TenantRiskAppetiteDataService tenantRiskAppetiteDataService;

    /**
     * Преконтроль рассчитанного действия: вход, добор, замещение с
     * увеличением, создание и перенос защиты
     * (docs/rules/risk-validator-scope.md).
     */
    public RiskValidationResult validate(CalculatedStrategyAction calculatedAction, DealContext dealContext) {
        List<RiskCheckResult> checks = new ArrayList<>();
        CalculatedSize size = calculatedAction.getCalculatedSize();
        CalculatedPrice price = calculatedAction.getCalculatedPrice();
        ExchangeAccount account = dealContext.getExchangeAccount();

        if (isFalse(dealContext.getGraphComplete())) {
            return blockedResult(checks, RiskCheckCode.DEAL_GRAPH_INCOMPLETE,
                    "Deal graph is not fully presented by the pass context");
        }
        if (isNull(size) || isNull(size.getSizeContracts()) || size.getSizeContracts().signum() <= 0) {
            return blockedResult(checks, RiskCheckCode.CALCULATED_ACTION_INVALID,
                    "Calculated size missing or non-positive");
        }
        InstrumentExternalRules rules = rulesDataService
                .findByInstrumentId(dealContext.getInstrument().getId(), account.getId())
                .orElse(null);
        if (isNull(rules)) {
            return blockedResult(checks, RiskCheckCode.INSTRUMENT_RULES_MISSING,
                    "Instrument external rules not materialized");
        }
        if (isBlank(dealContext.getInstrument().getExternalSettlementCurrency())) {
            return blockedResult(checks, RiskCheckCode.INSTRUMENT_SETTLE_CURRENCY_MISSING,
                    "Instrument settlement currency is not resolved");
        }
        BigDecimal base = dealContext.riskBase();
        if (isNull(base) || base.signum() <= 0) {
            return blockedResult(checks, RiskCheckCode.BALANCE_INVALID,
                    "Risk base is missing or non-positive");
        }
        Tenant appetite = tenantRiskAppetiteDataService
                .findByTenantInternalId(account.getTenantId())
                .orElse(new Tenant());
        if (isNull(appetite.getGlobalConsecutiveLossLimit())) {
            return blockedResult(checks, RiskCheckCode.LOSS_LIMIT_NOT_CONFIGURED,
                    "globalConsecutiveLossLimit is not assigned for tenant " + account.getTenantId());
        }
        if (isNull(appetite.getGlobalSimultaneousRiskPerDealPercent())) {
            return blockedResult(checks, RiskCheckCode.RISK_APPETITE_NOT_CONFIGURED,
                    "globalSimultaneousRiskPerDealPercent is not assigned for tenant " + account.getTenantId());
        }

        AccountInstrumentState pairState = accountInstrumentStateDataService
                .getRequiredByPair(account.getId(), dealContext.getInstrument().getId());
        BigDecimal sizeContracts = size.getSizeContracts();
        Position position = dealContext.getDeal().livePosition();
        StrategyTradeDirection direction = dealContext.getDeal().getDirection();
        BigDecimal entryAnchor = entryAnchor(position, price);

        checkInstrumentLive(rules, checks);
        checkMarginMode(pairState, checks);
        checkSizeBounds(rules, sizeContracts, price, checks);
        checkExchangeMaxLeverage(pairState, rules, checks);
        checkFeeRate(price, rules, dealContext, checks);
        checkRiskCreatingEntryProtection(calculatedAction, checks);
        checkStopLossSide(calculatedAction.getSourceAction(), price.getStopLossPrice(), entryAnchor,
                direction, checks);
        checkStopDistanceFloor(price.getStopLossPrice(), entryAnchor, direction, rules, checks);
        checkTakeProfitSide(price.getTakeProfitPrice(), entryAnchor, direction, checks);
        checkLiquidationGuard(price.getStopLossPrice(), position, direction, checks);
        checkCollapseWindow(calculatedAction, dealContext, checks);
        checkSafetyRung(calculatedAction.getSourceAction(), pairState, checks);
        checkCeilings(calculatedAction, dealContext, rules, appetite, base, entryAnchor, checks);

        return aggregate(checks);
    }

    /**
     * Ветка ослабления защиты для СНЯТИЯ отдельной защиты при живой
     * экспозиции (docs/rules/risk-validator-scope.md): снятие риск не
     * снимает, а увеличивает. Операнд — покрытие транша после того, как
     * снимаемая защита исчезнет (docs/spec/protection-coverage.json,
     * величина {@code removalAllowed}); ниже экспозиции этого транша —
     * отказ. Защиты соседних траншей в операнд не входят.
     *
     * <p><b>Блок-сет ступени действует и здесь:</b> снятие защиты при живой
     * экспозиции есть risk-weakening, а ступень блокирует его наравне с
     * созданием нового риска (docs/rules/instrument-hold.md §Enforcement).
     * Аварийная дочистка и kill-switch сюда не приходят вовсе — они вне
     * scope преконтроля.
     *
     * <p>Предикат, отказавший вычислением, даёт
     * {@code DEAL_GRAPH_INCOMPLETE}: пустота нулём не подменяется, иначе
     * сравнение с нулём разрешило бы снятие последней защиты над живой
     * экспозицией.
     */
    public RiskValidationResult validateProtectionRemoval(DealContext dealContext, DealTranche tranche,
                                                          Long algoOrderId) {
        List<RiskCheckResult> checks = new ArrayList<>();
        AccountInstrumentState pairState = accountInstrumentStateDataService.getRequiredByPair(
                dealContext.getExchangeAccount().getId(), dealContext.getInstrument().getId());
        if (isTrue(pairState.hasStandingSafetyRung())) {
            checks.add(RiskCheckResult.blocked(RiskCheckCode.INSTRUMENT_SAFETY_HOLD,
                    "Instrument stands in safety rung " + pairState.getSafetyRung()
                            + " whose block set covers protection removal", null));
        }
        Boolean allowed = tranche.removalAllowed(algoOrderId);
        if (isNull(allowed)) {
            return blockedResult(checks, RiskCheckCode.DEAL_GRAPH_INCOMPLETE,
                    "Tranche graph incomplete: live protection with no orders presented");
        }
        if (isFalse(allowed)) {
            return blockedResult(checks, RiskCheckCode.PROTECTION_COVERAGE_REDUCED,
                    "Coverage after removal " + tranche.coverageWithoutAlgoOrder(algoOrderId)
                            + " below tranche exposure " + tranche.exposure());
        }
        return aggregate(checks);
    }

    /**
     * <b>Вторая точка входа: те же неравенства при НУЛЕВОМ акте.</b>
     * Отвечает на вопрос «уложилась бы живая сделка в потолки, не делая
     * ничего» — предмет детектора нарушения риск-политики при живой
     * защите (docs/components/RiskValidator.md §«Что делает»,
     * docs/rules/instrument-hold.md §«Форма реакции на нарушение
     * риск-политики при живой защите»).
     *
     * <p><b>Собственных величин здесь не заводится:</b> считаются
     * {@code withinStrategySimultaneous}, {@code withinGlobalSimultaneous}
     * и {@code withinDealNotional} (docs/spec/risk-limits.json) при
     * {@code actRisk = 0} и {@code actNotional = 0}. Второй дом у любой из
     * форм был бы копией, расходящейся первой же правкой.
     *
     * <p><b>Поактный и накопленный потолки в перечень НЕ входят</b> — оба
     * меряют акт, которого здесь нет: при нулевом акте они истинны
     * тождественно, и включение их в набор давало бы детектору две
     * заведомо молчащие проверки.
     *
     * <p><b>Отсутствие операнда — молчание, а не находка.</b> Неполный
     * граф, нерезолвенные правила инструмента, пустая база риска,
     * незаявленное число — всё это означает «не проверено», а
     * непроверенное нарушением не читается: ложный триггер остановил бы
     * входы по инструменту без основания.
     *
     * @return нарушенные неравенства; пусто — нарушений нет либо проверка
     *         не проводилась
     */
    public List<RiskCheckResult> ceilingsBreachedWithoutAct(DealContext dealContext) {
        StrategyDetail detail = dealContext.getStrategyDetail();
        ExchangeAccount account = dealContext.getExchangeAccount();
        BigDecimal base = dealContext.riskBase();
        if (isFalse(dealContext.getGraphComplete()) || isNull(detail) || isNull(base) || base.signum() <= 0) {
            return List.of();
        }
        InstrumentExternalRules rules = rulesDataService
                .findByInstrumentId(dealContext.getInstrument().getId(), account.getId())
                .orElse(null);
        Tenant appetite = tenantRiskAppetiteDataService
                .findByTenantInternalId(account.getTenantId())
                .orElse(new Tenant());
        if (isNull(rules) || isNull(appetite.getGlobalSimultaneousRiskPerDealPercent())
                || isNull(detail.getStrategySimultaneousRiskPerDealPercent())
                || isNull(detail.getStrategyCatastrophicRiskPerDealMultiplier())) {
            return List.of();
        }
        BigDecimal entryAnchor = entryAnchor(dealContext.getDeal().livePosition(), null);
        BigDecimal livePositionRisk = livePositionRiskAtStop(dealContext, rules, entryAnchor,
                dealContext.getDeal().currentStopLevel());
        if (isNull(livePositionRisk)) {
            // Уровня защиты нет вовсе — это ПОТЕРЯ ПОКРЫТИЯ, и её реакцию
            // поднимает свой триггер, а не этот: одно состояние не получает
            // двух ответов (docs/rules/instrument-hold.md).
            return List.of();
        }
        List<RiskCheckResult> checks = new ArrayList<>();
        BigDecimal liveRiskNow = unfilledPlannedRisk(dealContext).add(livePositionRisk);
        checkAgainst(liveRiskNow, percentOf(detail.getStrategySimultaneousRiskPerDealPercent(), base),
                RiskCheckCode.RISK_PER_DEAL_SIMULTANEOUS_EXCEEDED, "strategy simultaneous ceiling", checks);
        checkAgainst(liveRiskNow, percentOf(appetite.getGlobalSimultaneousRiskPerDealPercent(), base),
                RiskCheckCode.RISK_PER_DEAL_SIMULTANEOUS_GLOBAL_EXCEEDED, "global simultaneous ceiling", checks);
        checkAgainst(dealNotional(dealContext, rules, entryAnchor),
                percentOf(appetite.getGlobalSimultaneousRiskPerDealPercent(), base)
                        .multiply(detail.getStrategyCatastrophicRiskPerDealMultiplier()),
                RiskCheckCode.DEAL_NOTIONAL_EXCEEDED, "catastrophic notional ceiling", checks);
        return checks;
    }

    /**
     * Четыре потолка риска и катастрофический потолок нотинала. Все пять
     * неравенств считаются от ОДНОЙ базы и от операндов, взятых по графу в
     * точке проверки (docs/spec/risk-limits.json).
     *
     * <p><b>Слагаемое проверяемого акта обязательно</b>: без него первый
     * вход сравнивал бы с потолком ноль и проходил любым размером —
     * потолок, заведённый против шокового хода, был бы инертен ровно там,
     * где решается размер.
     */
    private void checkCeilings(CalculatedStrategyAction calculatedAction, DealContext dealContext,
                               InstrumentExternalRules rules, Tenant appetite, BigDecimal base,
                               BigDecimal entryAnchor, List<RiskCheckResult> checks) {
        StrategyDetail detail = dealContext.getStrategyDetail();
        if (isNull(detail) || isNull(detail.getRiskPerActionPercent())) {
            checks.add(RiskCheckResult.blocked(RiskCheckCode.RISK_APPETITE_NOT_CONFIGURED,
                    "riskPerActionPercent is not declared by the pinned strategy detail", null));
            return;
        }
        BigDecimal actRisk = actRisk(calculatedAction, dealContext, rules, entryAnchor);
        BigDecimal actNotional = actNotional(calculatedAction, rules, entryAnchor);
        BigDecimal perAction = percentOf(detail.getRiskPerActionPercent(), base);

        checkPerAction(actRisk, perAction, calculatedAction, rules, checks);
        checkCumulative(dealContext, detail, actRisk, perAction, checks);
        checkSimultaneous(dealContext, detail, appetite, rules, base, entryAnchor, actRisk, calculatedAction, checks);
        checkCatastrophicNotional(dealContext, detail, appetite, rules, base, entryAnchor, actNotional, checks);
    }

    /**
     * Поактный потолок, и превышение разведено НА ДВА КОДА.
     *
     * <p>Размер, стоящий на минимальном торговом, поделить нечем — ветвь
     * подъёма до минимума потолком не ограничена вовсе
     * (docs/spec/order-sizing.json, величина {@code entryWithinRiskBudget}),
     * и такой отказ есть НЕДЕЛИМЫЙ ЛОТ, а не расхождение расчёта. Один код
     * на оба исхода делает карв-аут неразрешимым: у эталона при малой базе
     * минимальный лот превышает бюджет КАЖДЫМ входом, и сделка без живого
     * риска уходила бы в аварийный контур по ожидаемому отказу
     * (docs/components/models/RiskCheckResult.md).
     */
    private void checkPerAction(BigDecimal actRisk, BigDecimal perAction,
                                CalculatedStrategyAction calculatedAction, InstrumentExternalRules rules,
                                List<RiskCheckResult> checks) {
        if (actRisk.compareTo(perAction) <= 0) {
            return;
        }
        BigDecimal minSize = rules.minSize();
        BigDecimal sizeContracts = calculatedAction.getCalculatedSize().getSizeContracts();
        RiskCheckCode code = nonNull(minSize) && sizeContracts.compareTo(minSize) <= 0
                ? RiskCheckCode.SIZE_MIN_LOT_EXCEEDS_RISK_BUDGET
                : RiskCheckCode.RISK_PER_ACTION_EXCEEDED;
        checks.add(RiskCheckResult.blocked(code,
                "per-action risk ceiling exceeded: " + actRisk + " > " + perAction, actRisk));
    }

    /** Кумулятивный потолок: взятое сделкой за жизнь плюс риск акта. */
    private void checkCumulative(DealContext dealContext, StrategyDetail detail, BigDecimal actRisk,
                                 BigDecimal perAction, List<RiskCheckResult> checks) {
        if (isNull(detail.getCumulativeRiskPerDealMultiplier())) {
            checks.add(RiskCheckResult.blocked(RiskCheckCode.RISK_APPETITE_NOT_CONFIGURED,
                    "cumulativeRiskPerDealMultiplier is not declared by the pinned strategy detail", null));
            return;
        }
        BigDecimal ceiling = perAction.multiply(detail.getCumulativeRiskPerDealMultiplier());
        checkAgainst(zeroIfNull(dealContext.getDeal().getPlannedRiskAmount()).add(actRisk), ceiling,
                RiskCheckCode.RISK_PER_DEAL_CUMULATIVE_EXCEEDED, "cumulative risk ceiling", checks);
    }

    /**
     * Одновременный потолок в двух вложенных редакциях — стратегии и
     * риск-аппетита.
     *
     * <p><b>Живое слагаемое считается по уровню, ДЕЙСТВУЮЩЕМУ ПОСЛЕ
     * АКТА.</b> Преконтроль зовётся до создания команды, поэтому среди
     * живых защит проверяемой нет по построению: потолок, посчитанный по
     * действующему уровню, мерил бы защиту, которую действие как раз
     * заменяет, — и после рядового адверсного проскока входа постановка
     * основной защиты блокировалась бы бессрочно
     * (docs/spec/risk-limits.json, величина {@code stopPriceAfterAct}).
     *
     * <p><b>Ветвей три, и третья ОТКАЗЫВАЕТ вычислением:</b> акт снимает
     * защиту и своей не ставит — уровня после акта нет, риск живого
     * эпизода ничем не ограничен, и считать его по снятому уровню значило
     * бы мерить то, чего после акта не будет.
     */
    private void checkSimultaneous(DealContext dealContext, StrategyDetail detail, Tenant appetite,
                                   InstrumentExternalRules rules, BigDecimal base, BigDecimal entryAnchor,
                                   BigDecimal actRisk, CalculatedStrategyAction calculatedAction,
                                   List<RiskCheckResult> checks) {
        BigDecimal livePositionRisk = livePositionRiskAtStop(dealContext, rules, entryAnchor,
                stopPriceAfterAct(calculatedAction, dealContext));
        if (isNull(livePositionRisk)) {
            checks.add(RiskCheckResult.blocked(RiskCheckCode.PROTECTION_COVERAGE_REDUCED,
                    "No stop level remains after the act while the episode is live", null));
            return;
        }
        BigDecimal liveRiskNow = unfilledPlannedRisk(dealContext).add(livePositionRisk);
        if (isNull(detail.getStrategySimultaneousRiskPerDealPercent())) {
            checks.add(RiskCheckResult.blocked(RiskCheckCode.RISK_APPETITE_NOT_CONFIGURED,
                    "strategySimultaneousRiskPerDealPercent is not declared by the pinned strategy detail", null));
        } else {
            checkAgainst(liveRiskNow.add(actRisk),
                    percentOf(detail.getStrategySimultaneousRiskPerDealPercent(), base),
                    RiskCheckCode.RISK_PER_DEAL_SIMULTANEOUS_EXCEEDED, "strategy simultaneous ceiling", checks);
        }
        checkAgainst(liveRiskNow.add(actRisk),
                percentOf(appetite.getGlobalSimultaneousRiskPerDealPercent(), base),
                RiskCheckCode.RISK_PER_DEAL_SIMULTANEOUS_GLOBAL_EXCEEDED, "global simultaneous ceiling", checks);
    }

    /**
     * Катастрофический потолок: максимальный риск на сделку, растянутый
     * множителем стратегии; он же кэп суммарного номинала — худший
     * мыслимый ход принят равным 100 %. Незаявленный множитель ОТКАЗЫВАЕТ
     * вычислением, а не пропускает действие.
     */
    private void checkCatastrophicNotional(DealContext dealContext, StrategyDetail detail, Tenant appetite,
                                           InstrumentExternalRules rules, BigDecimal base,
                                           BigDecimal entryAnchor, BigDecimal actNotional,
                                           List<RiskCheckResult> checks) {
        if (isNull(detail.getStrategyCatastrophicRiskPerDealMultiplier())) {
            checks.add(RiskCheckResult.blocked(RiskCheckCode.RISK_APPETITE_NOT_CONFIGURED,
                    "strategyCatastrophicRiskPerDealMultiplier is not declared by the pinned strategy detail", null));
            return;
        }
        BigDecimal ceiling = percentOf(appetite.getGlobalSimultaneousRiskPerDealPercent(), base)
                .multiply(detail.getStrategyCatastrophicRiskPerDealMultiplier());
        checkAgainst(dealNotional(dealContext, rules, entryAnchor).add(actNotional), ceiling,
                RiskCheckCode.DEAL_NOTIONAL_EXCEEDED, "catastrophic notional ceiling", checks);
    }

    /**
     * Уровень защиты, действующий ПОСЛЕ применения проверяемого акта:
     * устанавливаемый актом, а если акт уровня не касается — действующий
     * на всю позицию. Пусто — защиты после акта не остаётся вовсе.
     */
    private BigDecimal stopPriceAfterAct(CalculatedStrategyAction calculatedAction, DealContext dealContext) {
        ResolvedStopLossPrice actStop = calculatedAction.getCalculatedPrice().getStopLossPrice();
        if (nonNull(actStop) && nonNull(actStop.getTriggerPrice())) {
            return actStop.getTriggerPrice();
        }
        return dealContext.getDeal().currentStopLevel();
    }

    /** Что ещё может встать под удар: неисполненная доля живых входных ног. */
    private BigDecimal unfilledPlannedRisk(DealContext dealContext) {
        return liveEntryLegs(dealContext).stream()
                .map(RiskValidator::legUnfilledRisk)
                .reduce(ZERO, BigDecimal::add);
    }

    /** Заявленный риск ноги в её НЕИСПОЛНЕННОЙ доле. */
    private static BigDecimal legUnfilledRisk(Order leg) {
        BigDecimal planned = zeroIfNull(leg.getPlannedSizeContracts());
        if (planned.signum() == 0) {
            return ZERO;
        }
        BigDecimal unfilled = planned.subtract(zeroIfNull(leg.getAccumulatedFillSize()));
        return zeroIfNull(leg.getPlannedRiskAmount())
                .multiply(unfilled)
                .divide(planned, DomainMath.CONTEXT);
    }

    /**
     * Живой эпизод до уровня, действующего после акта, с round-trip
     * комиссией. Клэмп нулём здесь, а не на сумме: стоп за безубытком
     * гасит СВОЁ слагаемое, а не чужие. Пусто — уровня после акта нет, и
     * величина отказывает вычислением.
     */
    private BigDecimal livePositionRiskAtStop(DealContext dealContext, InstrumentExternalRules rules,
                                              BigDecimal entryAnchor, BigDecimal stopAfterAct) {
        Position live = dealContext.getDeal().livePosition();
        if (isNull(live) || isNull(live.getExternalSize()) || live.getExternalSize().signum() == 0) {
            return ZERO;
        }
        if (isNull(stopAfterAct) || isNull(entryAnchor) || isNull(rules.contractValue())
                || isNull(rules.takerFeeRate())) {
            return null;
        }
        BigDecimal risk = RiskMath
                .lossAtStopPerUnit(dealContext.getDeal().getDirection(), entryAnchor, stopAfterAct,
                        rules.takerFeeRate())
                .multiply(live.getExternalSize())
                .multiply(rules.contractValue());
        return risk.signum() > 0 ? risk : ZERO;
    }

    /** Экспозиция сделки ДО акта: неисполненная доля живых ног плюс живой эпизод. */
    private BigDecimal dealNotional(DealContext dealContext, InstrumentExternalRules rules,
                                    BigDecimal entryAnchor) {
        if (isNull(rules.contractValue())) {
            return ZERO;
        }
        BigDecimal legs = liveEntryLegs(dealContext).stream()
                .map(leg -> zeroIfNull(leg.getPlannedSizeContracts())
                        .subtract(zeroIfNull(leg.getAccumulatedFillSize()))
                        .multiply(rules.contractValue())
                        .multiply(zeroIfNull(leg.getPlannedEntryPrice())))
                .reduce(ZERO, BigDecimal::add);
        Position live = dealContext.getDeal().livePosition();
        if (isNull(live) || isNull(live.getExternalSize()) || isNull(entryAnchor)) {
            return legs;
        }
        return legs.add(live.getExternalSize().multiply(rules.contractValue()).multiply(entryAnchor));
    }

    /**
     * Живые ВХОДНЫЕ ноги сделки — по всем траншам: потолки агрегатные.
     *
     * <p><b>Ноги собираются обходом траншей, а не полем агрегата:</b> поля
     * {@code Deal.orders} в целевой модели нет — нога висит на транше, а
     * одноимённое поле общей библиотеки донорское и ядром не читается
     * (docs/models/domain/aggregate/Deal.md §Структура).
     */
    private List<Order> liveEntryLegs(DealContext dealContext) {
        return emptyIfNull(dealContext.getDeal().getTranches()).stream()
                .flatMap(tranche -> emptyIfNull(tranche.getOrders()).stream())
                .filter(order -> isTrue(order.isLive()))
                .filter(order -> isNotTrue(order.getPositionReducingOnly()))
                .collect(Collectors.toList());
    }

    /**
     * Риск проверяемого акта: плановый риск ноги для risk-creating, ноль
     * для risk-weakening — новых контрактов такое действие не создаёт.
     */
    private BigDecimal actRisk(CalculatedStrategyAction calculatedAction, DealContext dealContext,
                               InstrumentExternalRules rules, BigDecimal entryAnchor) {
        if (isFalse(isRiskCreatingEntry(calculatedAction.getSourceAction()))) {
            return ZERO;
        }
        ResolvedStopLossPrice stop = calculatedAction.getCalculatedPrice().getStopLossPrice();
        if (isNull(stop) || isNull(stop.getTriggerPrice()) || isNull(entryAnchor)
                || isNull(rules.contractValue()) || isNull(rules.takerFeeRate())) {
            return ZERO;
        }
        return RiskMath.lossAtStopPerUnit(dealContext.getDeal().getDirection(), entryAnchor,
                        stop.getTriggerPrice(), rules.takerFeeRate())
                .multiply(calculatedAction.getCalculatedSize().getSizeContracts())
                .multiply(rules.contractValue());
    }

    /** Нотинал проверяемого акта; risk-weakening контрактов не создаёт. */
    private BigDecimal actNotional(CalculatedStrategyAction calculatedAction, InstrumentExternalRules rules,
                                   BigDecimal entryAnchor) {
        if (isFalse(isRiskCreatingEntry(calculatedAction.getSourceAction()))
                || isNull(rules.contractValue()) || isNull(entryAnchor)) {
            return ZERO;
        }
        return calculatedAction.getCalculatedSize().getSizeContracts()
                .multiply(rules.contractValue()).multiply(entryAnchor);
    }

    /**
     * Набор риска в окне сворачивания отвергается ПРЕКОНТРОЛЕМ, а не
     * только статусным ребром: действие, статус не двигающее — добор
     * объёма, замещение с увеличением, — до ребра не доходит вовсе
     * (docs/rules/exit-teardown-order.md). Признак приходит готовым с
     * модели сделки; преконтроль его не выводит.
     */
    private void checkCollapseWindow(CalculatedStrategyAction calculatedAction, DealContext dealContext,
                                     List<RiskCheckResult> checks) {
        if (isFalse(isRiskCreatingEntry(calculatedAction.getSourceAction()))) {
            return;
        }
        if (isTrue(dealContext.getDeal().isCollapsing())) {
            checks.add(RiskCheckResult.blocked(RiskCheckCode.RISK_CREATING_UNDER_COLLAPSE,
                    "Risk-creating act while the deal is collapsing", null));
        }
    }

    /**
     * Блок-сет стоящей safety-ступени пары «счёт, инструмент»: акт
     * блокируемого класса отвергается, реакция — карв-аут, возобновление —
     * снятием ступени (docs/rules/instrument-hold.md §Enforcement).
     *
     * <p><b>Ступень стои́т на ПАРЕ, а не на инструменте:</b> инструмент
     * принадлежит площадке, и ступень, поднятая отказами одного счёта, не
     * описывает состояние другого.
     *
     * <p><b>Блок-сет — не «всё, что видит преконтроль».</b> Постановка
     * уровня фиксации прибыли тоже валидируется (это создание защитной
     * заявки), но риска не создаёт и защиты не ослабляет — под ступенью
     * она проходит: сделки доживают под своей защитой.
     */
    private void checkSafetyRung(StrategyAction action, AccountInstrumentState pairState,
                                 List<RiskCheckResult> checks) {
        if (isFalse(pairState.hasStandingSafetyRung()) || isFalse(inSafetyBlockSet(action))) {
            return;
        }
        checks.add(RiskCheckResult.blocked(RiskCheckCode.INSTRUMENT_SAFETY_HOLD,
                "Instrument stands in safety rung " + pairState.getSafetyRung()
                        + " whose block set covers this act class", null));
    }

    /**
     * Акт входит в блок-сет ступени: он создаёт риск либо ослабляет его
     * контроль. Защитное действие ослабляет контроль тогда, когда касается
     * УРОВНЯ остановки убытка; уровень фиксации прибыли контроля не
     * ослабляет (docs/spec/strategy-reference.json, величина
     * {@code isProtectiveAction}).
     */
    private Boolean inSafetyBlockSet(StrategyAction action) {
        if (isTrue(isRiskCreatingEntry(action))) {
            return true;
        }
        return action instanceof StrategyAlgoOrderAction algoAction && isTrue(algoAction.isProtective());
    }

    /**
     * Пол дистанции стопа: уровень на убыточной стороне не ближе якоря,
     * чем round-trip комиссия — иначе стоп срабатывает в убыток даже без
     * движения цены (docs/spec/stop-distance.json). Проверяется на ЛЮБОЙ
     * постановке и переносе уровня; стоп на прибыльной стороне под пол не
     * подпадает — там дистанция знаково отрицательна.
     */
    private void checkStopDistanceFloor(ResolvedStopLossPrice stopLoss, BigDecimal entryAnchor,
                                        StrategyTradeDirection direction, InstrumentExternalRules rules,
                                        List<RiskCheckResult> checks) {
        if (isNull(stopLoss) || isNull(stopLoss.getTriggerPrice()) || isNull(entryAnchor)
                || isNull(rules.takerFeeRate())) {
            return;
        }
        BigDecimal trigger = stopLoss.getTriggerPrice();
        BigDecimal signedDistance = RiskMath.signedStopDistance(direction, entryAnchor, trigger);
        if (signedDistance.signum() <= 0) {
            return;
        }
        BigDecimal floor = RiskMath.stopDistanceFloor(entryAnchor, trigger, rules.takerFeeRate());
        if (signedDistance.compareTo(floor) < 0) {
            checks.add(RiskCheckResult.blocked(RiskCheckCode.STOP_DISTANCE_BELOW_FLOOR,
                    "Stop distance below round-trip fee floor " + floor, signedDistance));
        }
    }

    /** Неравенство потолка: превышение — отказ своим кодом. */
    private void checkAgainst(BigDecimal actual, BigDecimal ceiling, RiskCheckCode code, String label,
                              List<RiskCheckResult> checks) {
        if (actual.compareTo(ceiling) > 0) {
            checks.add(RiskCheckResult.blocked(code, label + " exceeded: " + actual + " > " + ceiling, actual));
        }
    }

    private BigDecimal percentOf(BigDecimal percent, BigDecimal base) {
        return percent.divide(HUNDRED, DomainMath.CONTEXT).multiply(base);
    }

    private static BigDecimal zeroIfNull(BigDecimal value) {
        return nonNull(value) ? value : ZERO;
    }

    /**
     * Ставка нужна всякому действию, которое СТАВИТ либо ПЕРЕНОСИТ уровень
     * остановки убытка: комиссия входит в убыток на стопе, и без неё
     * величина риска неполна. У действия, уровня не касающегося, уровневые
     * потребители ставки отпадают, но ставка остаётся нужна, ПОКА ЖИВ
     * ЭПИЗОД: она стои́т операндом живого слагаемого одновременного
     * потолка (docs/components/RiskValidator.md).
     *
     * <p>Подставленное число выглядит фактом, не будучи им, и ошибается
     * асимметрично: заниженная ставка освобождает бюджет риска и даёт
     * позицию больше положенной.
     */
    private void checkFeeRate(CalculatedPrice price, InstrumentExternalRules rules, DealContext dealContext,
                              List<RiskCheckResult> checks) {
        boolean touchesStopLevel = nonNull(price) && nonNull(price.getStopLossPrice())
                && nonNull(price.getStopLossPrice().getTriggerPrice());
        boolean episodeLive = nonNull(dealContext.getDeal().livePosition());
        if (isFalse(touchesStopLevel || episodeLive)) {
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

    /**
     * Режим маржи читается со строки ПАРЫ, а не с проекции каталога: её
     * перезаписывает синк, и запись ядра он бы затирал каждым тиком
     * (docs/models/domain/core/Instrument.md §«Ступень и настройки счёта на
     * инструменте — своя таблица ядра»).
     */
    private void checkMarginMode(AccountInstrumentState pairState, List<RiskCheckResult> checks) {
        if (isFalse(pairState.isMarginIsolated())) {
            checks.add(RiskCheckResult.blocked(RiskCheckCode.MARGIN_MODE_NOT_ISOLATED,
                    "Margin mode not isolated: " + pairState.getMarginMode(), null));
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

    /**
     * Плечо читается со строки пары — там же, где режим маржи: оно
     * объявлено ручной статичной настройкой СЧЁТА НА ИНСТРУМЕНТЕ
     * (docs/rules/trading-constraints.md). Пустое сверять не с чем, и
     * отказа это не даёт: настройка не назначена, а биржевой максимум
     * охраняет площадка.
     */
    private void checkExchangeMaxLeverage(AccountInstrumentState pairState, InstrumentExternalRules rules,
                                          List<RiskCheckResult> checks) {
        BigDecimal maxLeverage = rules.maxLeverage();
        if (isNull(pairState.getLeverage()) || isNull(maxLeverage)) {
            return;
        }
        BigDecimal leverage = new BigDecimal(pairState.getLeverage());
        if (leverage.compareTo(maxLeverage) > 0) {
            checks.add(RiskCheckResult.blocked(RiskCheckCode.EXCHANGE_MAX_LEVERAGE_EXCEEDED,
                    "Leverage above exchange max " + maxLeverage, leverage));
        }
    }

    /**
     * Risk-creating вход без резолвимого стопа блокируется: без уровня
     * остановки риск нечем посчитать, и сайзинг по доле аллокации в обход
     * поактного потолка тут не разрешение, а fail-open.
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

    /** Risk-creating вход — order-action, открывающий либо наращивающий позицию. */
    private Boolean isRiskCreatingEntry(StrategyAction action) {
        if (action instanceof StrategyOrderAction orderAction) {
            return isNotTrue(orderAction.getPositionReducingOnly());
        }
        return false;
    }

    /**
     * Первичный уровень остановки убытка обязан лечь на УБЫТОЧНУЮ сторону
     * от якоря — иначе worst-case выхода у позиции нет
     * (docs/spec/stop-distance.json, величина {@code primaryStopOnLossSide}).
     *
     * <p><b>Область ограничена двумя осями.</b> Перенос уже стоящего уровня
     * под неё не подпадает: перевод в безубыток и трейлинг за безубыток —
     * прямое назначение переноса. Защитное создание с НАБЛЮДАЕМЫМ уровнем
     * (трейлинг) — тоже: уровня в момент постановки у него нет вовсе, и
     * требовать стороны значило бы отвергать ступень, ради которой
     * держится прибыль.
     */
    private void checkStopLossSide(StrategyAction action, ResolvedStopLossPrice stopLoss, BigDecimal entryAnchor,
                                   StrategyTradeDirection direction, List<RiskCheckResult> checks) {
        if (isNull(stopLoss) || isNull(stopLoss.getTriggerPrice()) || isNull(entryAnchor)) {
            return;
        }
        if (isNull(action) || isFalse(StrategyPlacementRole.PRIMARY.equals(action.placementRole()))
                || isFalse(StrategyLevelSource.DECLARED.equals(action.levelSource()))) {
            return;
        }
        BigDecimal trigger = stopLoss.getTriggerPrice();
        boolean invalid = StrategyTradeDirection.LONG.equals(direction)
                ? trigger.compareTo(entryAnchor) >= 0
                : trigger.compareTo(entryAnchor) <= 0;
        if (invalid) {
            checks.add(RiskCheckResult.blocked(RiskCheckCode.STOP_LOSS_INVALID_SIDE,
                    "Stop-loss on wrong side of entry " + entryAnchor, trigger));
        }
    }

    private void checkTakeProfitSide(ResolvedTakeProfitPrice takeProfit, BigDecimal entryAnchor,
                                     StrategyTradeDirection direction, List<RiskCheckResult> checks) {
        if (isNull(takeProfit) || isNull(takeProfit.getTriggerPrice()) || isNull(entryAnchor)) {
            return;
        }
        BigDecimal trigger = takeProfit.getTriggerPrice();
        boolean invalid = StrategyTradeDirection.LONG.equals(direction)
                ? trigger.compareTo(entryAnchor) <= 0
                : trigger.compareTo(entryAnchor) >= 0;
        if (invalid) {
            checks.add(RiskCheckResult.blocked(RiskCheckCode.TAKE_PROFIT_INVALID_SIDE,
                    "Take-profit on wrong side of entry " + entryAnchor, trigger));
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

    /** Себестоимость, от которой меряется дистанция: средняя цена живого эпизода либо плановая цена действия. */
    private BigDecimal entryAnchor(Position position, CalculatedPrice price) {
        if (nonNull(position) && nonNull(position.getExternalAverageEntryPrice())) {
            return position.getExternalAverageEntryPrice();
        }
        return isNull(price) ? null : price.getRoundedPrice();
    }

    /** Per-order лимит размера по режиму цены: EXPLICIT — limit-лимит, иначе market-лимит. */
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
