package com.example.tradingcore.domain.command.risk;

import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.ZERO;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
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
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyDetail;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAlgoOrderAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyLevelSource;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyOrderAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyPlacementRole;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyTradeDirection;
import com.example.tradingbot.domain.model.core.balance.Balance;
import com.example.tradingbot.domain.model.core.balance.BalanceContainer;
import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import com.example.tradingbot.domain.model.core.instrument.InstrumentExternalRules;
import com.example.tradingbot.domain.model.core.instrument.PositionTier;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingbot.domain.util.DomainMath;
import com.example.tradingbot.domain.util.RiskMath;
import com.example.tradingcore.config.DealContextProperties;
import com.example.tradingcore.domain.account.AccountInstrumentState;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.risk.RiskCheckResult.RiskCheckCode;
import com.example.tradingcore.domain.command.risk.RiskValidationResult.RiskDecision;
import com.example.tradingcore.domain.deal.DealContextService;
import com.example.tradingcore.domain.model.RiskAppetite;
import com.example.tradingcore.domain.service.RiskAppetiteService;
import com.example.tradingcore.persistence.service.AccountInstrumentStateDataService;
import com.example.tradingcore.persistence.service.DealDataService;
import com.example.tradingcore.persistence.service.ExchangeAccountDataService;
import com.example.tradingcore.persistence.service.InstrumentExternalRulesDataService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Отвечает на вопрос «разрешено ли это рассчитанное действие» по
 * риск-политике, возвращая {@link RiskValidationResult}
 * (docs/components/RiskValidator.md). Нужные метрики считает сам; статус
 * сделки не меняет и команд не создаёт.
 *
 * <p><b>Четыре операнда валидатор читает СВОЕЙ тропой, а не аргументом:</b>
 * справочные правила инструмента (со ставкой, налитой границей навеса),
 * состояние пары «счёт, инструмент», принятые ядром числа риск-аппетита и
 * живые сделки уровней «счёт» и «тенант» с базами счетов тенанта. Гидрация
 * ставки в фабрике контекста расчёта накрыла бы только тропу калькуляторов,
 * и преконтроль блокировал бы каждый вход отсутствием ставки.
 *
 * <p><b>Снимок средств приходит контекстом, а срок его годности —
 * конфигурацией прохода:</b> проверки средств счёта меряются тем же
 * предикатом свежести, по которому обработчик предвходовой проверки
 * заказывает добычу, и с той же толерантностью.
 *
 * <p><b>Делитель ВСЕХ потолков сделки один</b> — база риска: снимок
 * сделки, если он есть, иначе живая база счёта. Потолки счёта и тенанта
 * делят не его, а капитал своего уровня (docs/rules/risk-policy.md
 * §«Потолки живого риска счёта и тенанта»). Развилка не
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
 * действие.</b> Неравенство, которое не на чем посчитать, не проверено, а
 * непроверенное благоприятным умолчанием не читается (docs/concept.md П1).
 *
 * <p><b>Числа риск-аппетита окружения пустыми не бывают:</b> непринятый
 * набор роняет старт ядра (docs/rules/risk-policy.md §«Числа назначает
 * держатель; пустое место — отказ»), поэтому каждое неравенство на них
 * мерится у всякого проверяемого действия. Пустым бывает только число,
 * объявленное деталью стратегии, и его пустота отказывает всякому действию.
 */
@Component
@RequiredArgsConstructor
public class RiskValidator {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final InstrumentExternalRulesDataService rulesDataService;
    private final AccountInstrumentStateDataService accountInstrumentStateDataService;
    private final RiskAppetiteService riskAppetiteService;
    private final ExchangeAccountDataService exchangeAccountDataService;
    private final DealDataService dealDataService;
    private final DealContextService dealContextService;
    private final DealContextProperties properties;

    /**
     * Преконтроль рассчитанного действия: вход, добор, замещение с
     * увеличением, создание и перенос защиты
     * (docs/rules/risk-validator-scope.md).
     *
     * <p><b>Транш действия — операнд различителя блок-сета, и только его.</b>
     * Потолки считаются по всей сделке (docs/rules/risk-validator-scope.md
     * §«Действие транша, потолки сделки»); транш нужен одному вопросу — ослабляет ли защитное
     * действие уровень СВОЕГО транша. Спрашивают его две проверки —
     * стоящая ступень и торгуемость инструмента: область у них одна.
     * Пустой транш этот вопрос оставляет без ответа, и защитное действие
     * остаётся в блок-сете.
     */
    public RiskValidationResult validate(CalculatedStrategyAction calculatedAction, DealContext dealContext,
                                         DealTranche tranche) {
        List<RiskCheckResult> checks = new ArrayList<>();
        CalculatedSize size = calculatedAction.getCalculatedSize();
        CalculatedPrice price = calculatedAction.getCalculatedPrice();
        ExchangeAccount account = dealContext.getExchangeAccount();

        if (isNotTrue(dealContext.getGraphComplete())) {
            return blockedResult(checks, RiskCheckCode.DEAL_GRAPH_INCOMPLETE,
                    "Deal graph is not fully presented by the pass context");
        }
        if (isNull(size) || isNull(size.getSizeContracts()) || size.getSizeContracts().signum() <= 0) {
            return blockedResult(checks, RiskCheckCode.CALCULATED_ACTION_INVALID,
                    "Calculated size missing or non-positive");
        }
        if (isNull(price)) {
            return blockedResult(checks, RiskCheckCode.CALCULATED_ACTION_INVALID,
                    "Calculated price missing: level checks and act risk are unmeasured");
        }
        InstrumentExternalRules rules = rulesDataService
                .findByInstrumentId(dealContext.getInstrument().getId(), account.getId())
                .orElse(null);
        if (isNull(rules)) {
            return blockedResult(checks, RiskCheckCode.INSTRUMENT_RULES_MISSING,
                    "Instrument external rules not materialized");
        }
        if (isNull(rules.contractValue())) {
            return blockedResult(checks, RiskCheckCode.INSTRUMENT_RULES_MISSING,
                    "Instrument contract value not materialized: act risk, live risk and notional are unmeasured");
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
        Boolean riskCreating = isRiskCreatingEntry(calculatedAction.getSourceAction());
        RiskAppetite appetite = riskAppetiteService.getAccepted();

        AccountInstrumentState pairState = accountInstrumentStateDataService
                .getRequiredByPair(account.getId(), dealContext.getInstrument().getId());
        BigDecimal sizeContracts = size.getSizeContracts();
        Position position = dealContext.getDeal().livePosition();
        StrategyTradeDirection direction = dealContext.getDeal().getDirection();
        BigDecimal entryAnchor = entryAnchor(position, price);

        checkInstrumentLive(rules, calculatedAction, tranche, direction, checks);
        checkMarginMode(pairState, checks);
        checkSizeBounds(rules, sizeContracts, price, checks);
        checkLeverage(calculatedAction.getSourceAction(), pairState, rules, appetite, checks);
        checkFeeRate(touchesStopLevel(price), rules, dealContext, checks);
        Balance settlement = checkAccountFunds(dealContext, checks);
        if (nonNull(settlement)) {
            checkBalanceEnough(calculatedAction, dealContext, rules, pairState, settlement, checks);
        }
        checkRiskCreatingEntryProtection(calculatedAction, checks);
        checkStopLossSide(calculatedAction.getSourceAction(), price.getStopLossPrice(), entryAnchor,
                price.getRoundedPrice(), direction, checks);
        checkTransferStopBehindMark(calculatedAction.getSourceAction(), price.getStopLossPrice(), position,
                entryAnchor, direction, checks);
        checkStopDistanceFloor(price.getStopLossPrice(), entryAnchor, direction, rules, checks);
        checkTakeProfitSide(price.getTakeProfitPrice(), entryAnchor, direction, checks);
        checkLiquidation(calculatedAction, dealContext, rules, pairState, entryAnchor, checks);
        checkCollapseWindow(calculatedAction, dealContext, checks);
        checkSafetyRung(calculatedAction, tranche, direction, pairState, checks);
        checkCeilings(calculatedAction, riskCreating, dealContext, rules, appetite, base, entryAnchor, checks);

        return aggregate(checks);
    }

    /**
     * Ветка ослабления защиты для СНЯТИЯ отдельной защиты при живой
     * экспозиции (docs/rules/risk-validator-scope.md): снятие риск не
     * снимает, а увеличивает (docs/components/RiskValidator.md §«Ветка
     * ослабления защиты»).
     *
     * <p><b>Набор тот же, что у рассчитанного действия, и в том же
     * порядке</b> — за вычетом проверок, чей операнд собственный размер,
     * цена либо уровень акта (у снятия их нет), и проверок, чья область —
     * действие, создающее риск: входные гейты, торгуемость, режим маржи,
     * биржевой максимум плеча, ставка при живом эпизоде, проверки средств
     * на свежем снимке, стоящая ступень, потолки и последним — покрытие
     * транша после снятия. Класс действия ни одного неравенства не
     * выключает (docs/rules/risk-policy.md, риск акта по классу действия):
     * неравенство потолка нотинала, выключенное на снятии, пропускало бы
     * ослабление защиты сверх потолка.
     *
     * <p><b>Потолки считаются при нулевых слагаемых акта и ДОАКТНОМ
     * уровне</b> — снимаемая защита ещё действует, и уровень берётся
     * наименее благоприятный среди действующих, её включая. Поактный
     * тривиально истинен и не считается; кумулятивные, одновременные сделки
     * и потолок нотинала сравнивают с потолком уже взятое и уже живое;
     * потолки счёта и тенанта меряют прирост живого риска сделки, а он при
     * доактном уровне нулевой.
     * <b>Это не вторая точка входа:</b> там отсутствие операнда — молчание,
     * здесь — отказ, как на всяком проверяемом действии.
     *
     * <p><b>Последним — покрытие транша после снятия</b>
     * (docs/spec/protection-coverage.json, величина {@code removalAllowed});
     * ниже экспозиции этого транша — отказ, защиты соседних траншей в
     * операнд не входят. Предикат, отказавший вычислением, обрывает
     * перечень отказом {@code DEAL_GRAPH_INCOMPLETE}: пустота нулём не
     * подменяется, иначе сравнение с нулём разрешило бы снятие последней
     * защиты над живой экспозицией.
     */
    public RiskValidationResult validateProtectionRemoval(DealContext dealContext, DealTranche tranche,
                                                          Long algoOrderId) {
        List<RiskCheckResult> checks = new ArrayList<>();
        ExchangeAccount account = dealContext.getExchangeAccount();

        if (isNotTrue(dealContext.getGraphComplete())) {
            return blockedResult(checks, RiskCheckCode.DEAL_GRAPH_INCOMPLETE,
                    "Deal graph is not fully presented by the pass context");
        }
        InstrumentExternalRules rules = rulesDataService
                .findByInstrumentId(dealContext.getInstrument().getId(), account.getId())
                .orElse(null);
        if (isNull(rules)) {
            return blockedResult(checks, RiskCheckCode.INSTRUMENT_RULES_MISSING,
                    "Instrument external rules not materialized");
        }
        if (isNull(rules.contractValue())) {
            return blockedResult(checks, RiskCheckCode.INSTRUMENT_RULES_MISSING,
                    "Instrument contract value not materialized: live risk and notional are unmeasured");
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
        RiskAppetite appetite = riskAppetiteService.getAccepted();

        AccountInstrumentState pairState = accountInstrumentStateDataService
                .getRequiredByPair(account.getId(), dealContext.getInstrument().getId());
        BigDecimal entryAnchor = entryAnchor(dealContext.getDeal().livePosition(), null);

        checkInstrumentLiveForRemoval(rules, checks);
        checkMarginMode(pairState, checks);
        checkLeverage(null, pairState, rules, appetite, checks);
        checkFeeRate(false, rules, dealContext, checks);
        checkAccountFunds(dealContext, checks);
        if (isTrue(pairState.hasStandingSafetyRung())) {
            checks.add(RiskCheckResult.blocked(RiskCheckCode.INSTRUMENT_SAFETY_HOLD,
                    "Instrument stands in safety rung " + pairState.getSafetyRung()
                            + " whose block set covers protection removal", null));
        }
        checkCeilingsWithoutActOperands(dealContext, rules, appetite, base, entryAnchor, checks);

        Boolean allowed = tranche.removalAllowed(algoOrderId);
        if (isNull(allowed)) {
            return blockedResult(checks, RiskCheckCode.DEAL_GRAPH_INCOMPLETE,
                    "Tranche graph incomplete: live protection with no orders presented");
        }
        if (isFalse(allowed)) {
            checks.add(RiskCheckResult.blocked(RiskCheckCode.PROTECTION_COVERAGE_REDUCED,
                    "Coverage after removal " + tranche.coverageWithoutAlgoOrder(algoOrderId)
                            + " below tranche exposure " + tranche.exposure(), null));
        }
        return aggregate(checks);
    }

    /**
     * Торгуемость инструмента на снятии защиты: снятие при живой экспозиции
     * входит в блок-сет всегда — уровня, который защиту транша не
     * ослаблял бы, у него нет (docs/rules/risk-validator-scope.md
     * §«Торгуемость инструмента запирает набор риска, а не защиту»).
     */
    private void checkInstrumentLiveForRemoval(InstrumentExternalRules rules, List<RiskCheckResult> checks) {
        if (isTrue(rules.isLive())) {
            return;
        }
        checks.add(RiskCheckResult.blocked(RiskCheckCode.INSTRUMENT_NOT_LIVE,
                "Instrument not tradeable: " + rules.getStatus() + ", protection removal is in the block set",
                null));
    }

    /**
     * Потолки на снятии защиты: слагаемые акта нулевые, уровень — ДОАКТНЫЙ
     * (снимаемая защита ещё действует). Поактный потолок тривиально
     * истинен и не считается; незаявленное деталью число отказывает, как
     * у рассчитанного действия: незаявленный процент на действие снимает
     * все потолки, прочие числа — только свой.
     */
    private void checkCeilingsWithoutActOperands(DealContext dealContext, InstrumentExternalRules rules,
                                                 RiskAppetite appetite, BigDecimal base, BigDecimal entryAnchor,
                                                 List<RiskCheckResult> checks) {
        StrategyDetail detail = dealContext.getStrategyDetail();
        if (isNull(detail) || isNull(detail.getRiskPerActionPercent())) {
            checks.add(RiskCheckResult.blocked(RiskCheckCode.RISK_APPETITE_NOT_CONFIGURED,
                    "riskPerActionPercent is not declared by the pinned strategy detail", null));
            return;
        }
        Deal deal = dealContext.getDeal();
        BigDecimal perAction = percentOf(detail.getRiskPerActionPercent(), base);
        BigDecimal liveRisk = liveRiskNow(deal, rules, entryAnchor, deal.currentStopLevel());
        checkCumulative(dealContext, detail, ZERO, perAction, checks);
        checkGlobalCumulative(deal, appetite, base, ZERO, checks);
        checkSimultaneous(detail, appetite, base, liveRisk, ZERO, checks);
        checkDealNotional(dealContext, appetite, rules, base, entryAnchor, ZERO, checks);
        checkLevelCeilings(dealContext, appetite, liveRisk, liveRisk, ZERO, checks);
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
     * <p><b>Поактный и накопленные потолки в перечень НЕ входят</b> — все
     * меряют акт, которого здесь нет: при нулевом акте они истинны
     * тождественно. <b>Потолки счёта и тенанта не входят тоже:</b> при
     * нулевом акте прирост живого риска сделки нулевой
     * ({@code ownLiveRiskRaised}), и они истинны тождественно.
     *
     * <p><b>Отсутствие операнда — молчание, а не находка.</b> Неполный
     * граф, нерезолвенные правила инструмента, пустая база риска,
     * незаявленное число — всё это означает «не проверено», а
     * непроверенное нарушением не читается: ложный триггер остановил бы
     * входы по инструменту без основания. <b>Молчание поштучное:</b> операнд
     * всех трёх неравенств — граф, база, правила, живой риск — снимает весь
     * перечень, а число детали — только свою редакцию: незаявленный процент
     * одновременного риска стратегии глушит её, а глобальная редакция и
     * потолок нотинала мерятся — их числа есть риск-аппетит окружения,
     * заданный у работающего ядра всегда. Детали нет вовсе — молчит весь
     * перечень: сделка без закреплённой детали есть восстановленная, и её
     * главная проверка — сверка экспозиции траншей с эпизодом
     * (docs/components/DealActiveHandler.md).
     *
     * @return нарушенные неравенства; пусто — нарушений нет либо проверка
     *         не проводилась
     */
    public List<RiskCheckResult> ceilingsBreachedWithoutAct(DealContext dealContext) {
        StrategyDetail detail = dealContext.getStrategyDetail();
        ExchangeAccount account = dealContext.getExchangeAccount();
        BigDecimal base = dealContext.riskBase();
        if (isNotTrue(dealContext.getGraphComplete()) || isNull(detail) || isNull(base) || base.signum() <= 0) {
            return List.of();
        }
        InstrumentExternalRules rules = rulesDataService
                .findByInstrumentId(dealContext.getInstrument().getId(), account.getId())
                .orElse(null);
        if (isNull(rules)) {
            return List.of();
        }
        RiskAppetite appetite = riskAppetiteService.getAccepted();
        Deal deal = dealContext.getDeal();
        BigDecimal entryAnchor = entryAnchor(deal.livePosition(), null);
        BigDecimal liveRiskNow = liveRiskNow(deal, rules, entryAnchor, deal.currentStopLevel());
        if (isNull(liveRiskNow)) {
            // Уровня защиты нет вовсе — это ПОТЕРЯ ПОКРЫТИЯ, и её реакцию
            // поднимает свой триггер, а не этот: одно состояние не получает
            // двух ответов (docs/rules/instrument-hold.md).
            return List.of();
        }
        List<RiskCheckResult> checks = new ArrayList<>();
        if (nonNull(detail.getStrategySimultaneousRiskPerDealPercent())) {
            checkAgainst(liveRiskNow, percentOf(detail.getStrategySimultaneousRiskPerDealPercent(), base),
                    RiskCheckCode.RISK_PER_DEAL_SIMULTANEOUS_EXCEEDED, "strategy simultaneous ceiling", checks);
        }
        checkAgainst(liveRiskNow, percentOf(appetite.getGlobalSimultaneousRiskPerDealPercent(), base),
                RiskCheckCode.RISK_PER_DEAL_SIMULTANEOUS_GLOBAL_EXCEEDED, "global simultaneous ceiling", checks);
        checkAgainst(dealNotional(deal, rules, entryAnchor), dealNotionalCeiling(appetite, base),
                RiskCheckCode.DEAL_NOTIONAL_EXCEEDED, "deal notional ceiling", checks);
        return checks;
    }

    /**
     * Потолки риска сделки, потолок нотинала и потолки живого риска счёта и
     * тенанта. Потолки сделки считаются от ОДНОЙ базы и от операндов, взятых
     * по графу в точке проверки (docs/spec/risk-limits.json); потолки уровней
     * делят капитал своего уровня.
     *
     * <p><b>Слагаемое проверяемого акта обязательно</b>: без него первый
     * вход сравнивал бы с потолком ноль и проходил любым размером —
     * потолок, заведённый против шокового хода, был бы инертен ровно там,
     * где решается размер.
     *
     * @param riskCreating проверяемый акт создаёт риск — его операнды
     *                     (якорь, плановая цена) обязаны быть резолвлены
     */
    private void checkCeilings(CalculatedStrategyAction calculatedAction, Boolean riskCreating,
                               DealContext dealContext, InstrumentExternalRules rules, RiskAppetite appetite,
                               BigDecimal base, BigDecimal entryAnchor, List<RiskCheckResult> checks) {
        StrategyDetail detail = dealContext.getStrategyDetail();
        if (isNull(detail) || isNull(detail.getRiskPerActionPercent())) {
            checks.add(RiskCheckResult.blocked(RiskCheckCode.RISK_APPETITE_NOT_CONFIGURED,
                    "riskPerActionPercent is not declared by the pinned strategy detail", null));
            return;
        }
        if (isTrue(riskCreating) && isNull(entryAnchor)) {
            checks.add(RiskCheckResult.blocked(RiskCheckCode.CALCULATED_ACTION_INVALID,
                    "Entry anchor is not resolved: act risk is unmeasured", null));
            return;
        }
        if (isTrue(riskCreating) && isNull(calculatedAction.getCalculatedPrice().getRoundedPrice())) {
            checks.add(RiskCheckResult.blocked(RiskCheckCode.CALCULATED_ACTION_INVALID,
                    "Act price is not resolved: act notional is unmeasured", null));
            return;
        }
        Deal deal = dealContext.getDeal();
        BigDecimal actRisk = actRisk(calculatedAction, dealContext, rules, entryAnchor);
        BigDecimal actNotional = actNotional(calculatedAction, rules);
        BigDecimal perAction = percentOf(detail.getRiskPerActionPercent(), base);
        BigDecimal liveRiskAfterAct = liveRiskNow(deal, rules, entryAnchor,
                stopPriceAfterAct(calculatedAction, dealContext));

        checkPerAction(actRisk, perAction, calculatedAction, rules, checks);
        checkCumulative(dealContext, detail, actRisk, perAction, checks);
        checkGlobalCumulative(deal, appetite, base, actRisk, checks);
        checkSimultaneous(detail, appetite, base, liveRiskAfterAct, actRisk, checks);
        checkDealNotional(dealContext, appetite, rules, base, entryAnchor, actNotional, checks);
        checkLevelCeilings(dealContext, appetite,
                liveRiskNow(deal, rules, entryAnchor, deal.currentStopLevel()), liveRiskAfterAct, actRisk, checks);
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

    /** Кумулятивный потолок стратегии: взятое сделкой за жизнь плюс риск акта. */
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
     * Глобальная редакция кумулятивного потолка: взятое сделкой за жизнь
     * плюс риск акта — не выше предела множителя, помноженного на процент
     * сделки (docs/spec/risk-limits.json, величина
     * {@code withinGlobalCumulative}).
     *
     * <p><b>Рантайм-редакция нужна рядом со статической.</b> Множитель
     * стратегии сверяется с пределом на создании и активации, но смена чисел
     * активные определения не ревалидирует: без этой проверки пониженный
     * предел остался бы без энфорсера у уже активной стратегии
     * (.claude/decisions/global-cumulative-risk-ceiling.md).
     */
    private void checkGlobalCumulative(Deal deal, RiskAppetite appetite, BigDecimal base,
                                       BigDecimal actRisk, List<RiskCheckResult> checks) {
        BigDecimal ceiling = percentOf(appetite.getGlobalSimultaneousRiskPerDealPercent(), base)
                .multiply(appetite.getGlobalCumulativeRiskPerDealMultiplier());
        checkAgainst(zeroIfNull(deal.getPlannedRiskAmount()).add(actRisk), ceiling,
                RiskCheckCode.RISK_PER_DEAL_CUMULATIVE_GLOBAL_EXCEEDED, "global cumulative risk ceiling", checks);
    }

    /**
     * Одновременный потолок сделки в двух вложенных редакциях — стратегии
     * и риск-аппетита.
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
     *
     * @param liveRiskAfterAct живой риск сделки по уровню после акта; пусто —
     *                         уровня после акта нет
     */
    private void checkSimultaneous(StrategyDetail detail, RiskAppetite appetite, BigDecimal base,
                                   BigDecimal liveRiskAfterAct, BigDecimal actRisk,
                                   List<RiskCheckResult> checks) {
        if (isNull(liveRiskAfterAct)) {
            checks.add(RiskCheckResult.blocked(RiskCheckCode.PROTECTION_COVERAGE_REDUCED,
                    "No stop level remains after the act while the episode is live", null));
            return;
        }
        if (isNull(detail.getStrategySimultaneousRiskPerDealPercent())) {
            checks.add(RiskCheckResult.blocked(RiskCheckCode.RISK_APPETITE_NOT_CONFIGURED,
                    "strategySimultaneousRiskPerDealPercent is not declared by the pinned strategy detail", null));
        } else {
            checkAgainst(liveRiskAfterAct.add(actRisk),
                    percentOf(detail.getStrategySimultaneousRiskPerDealPercent(), base),
                    RiskCheckCode.RISK_PER_DEAL_SIMULTANEOUS_EXCEEDED, "strategy simultaneous ceiling", checks);
        }
        checkAgainst(liveRiskAfterAct.add(actRisk),
                percentOf(appetite.getGlobalSimultaneousRiskPerDealPercent(), base),
                RiskCheckCode.RISK_PER_DEAL_SIMULTANEOUS_GLOBAL_EXCEEDED, "global simultaneous ceiling", checks);
    }

    /**
     * Потолок нотинала сделки: нотинал неисполненной доли живых ног, живого
     * эпизода и акта — не выше предела плеча, помноженного на базу
     * (docs/spec/risk-limits.json, величины {@code dealNotionalCeiling} и
     * {@code withinDealNotional}). Брутто-плечо сделки к базе и есть
     * отношение её нотинала к базе, поэтому «плечо сделки не выше предела»
     * исполняется этим неравенством (.claude/decisions/deal-leverage-ceiling.md).
     * Худшего убытка оно не утверждает.
     */
    private void checkDealNotional(DealContext dealContext, RiskAppetite appetite,
                                   InstrumentExternalRules rules, BigDecimal base, BigDecimal entryAnchor,
                                   BigDecimal actNotional, List<RiskCheckResult> checks) {
        checkAgainst(dealNotional(dealContext.getDeal(), rules, entryAnchor).add(actNotional),
                dealNotionalCeiling(appetite, base),
                RiskCheckCode.DEAL_NOTIONAL_EXCEEDED, "deal notional ceiling", checks);
    }

    /** Правая часть потолка нотинала: предел плеча, помноженный на базу. */
    private BigDecimal dealNotionalCeiling(RiskAppetite appetite, BigDecimal base) {
        return appetite.getGlobalMaxLeverage().multiply(base);
    }

    /**
     * Потолки живого риска биржевого счёта и тенанта
     * (docs/rules/risk-policy.md §«Потолки живого риска счёта и тенанта»;
     * формы — docs/spec/risk-limits.json, величины
     * {@code withinAccountSimultaneous} и {@code withinTenantSimultaneous}).
     *
     * <p><b>Неравенство уровня применяется, когда акт ПОВЫШАЕТ живой риск
     * своей сделки</b> ({@code ownLiveRiskRaised}): операнд уровня — чужие
     * сделки, и без различителя неизмеренный сосед запирал бы постановку
     * защиты этой сделки. Гейт стои́т на измеренном приросте, а не на классе
     * действия: ремодел защиты дальше от цены прирост даёт и проверяется.
     * Соседей поэтому грузят только тогда, когда прирост есть.
     *
     * <p><b>Живой риск после акта пуст — уровни молчат:</b> его пустота уже
     * отказ своим кодом у одновременного потолка сделки, и второй код о том
     * же операнде диагностики не добавил бы.
     *
     * <p><b>Неизмеренный сосед — отказ кодом уровня</b>: неизмеренный уровень
     * выполненным не читается (docs/concept.md П1).
     *
     * @param liveRiskBeforeAct живой риск сделки до акта; пусто — не измерен,
     *                          и прирост не исключён
     * @param liveRiskAfterAct  живой риск сделки по уровню после акта
     */
    private void checkLevelCeilings(DealContext dealContext, RiskAppetite appetite,
                                    BigDecimal liveRiskBeforeAct, BigDecimal liveRiskAfterAct, BigDecimal actRisk,
                                    List<RiskCheckResult> checks) {
        if (isNull(liveRiskAfterAct)) {
            return;
        }
        BigDecimal ownAfterAct = liveRiskAfterAct.add(actRisk);
        if (isFalse(ownLiveRiskRaised(liveRiskBeforeAct, ownAfterAct))) {
            return;
        }
        ExchangeAccount account = dealContext.getExchangeAccount();
        List<ExchangeAccount> tenantAccounts = tenantBaseAccounts(account);
        Map<Long, List<BigDecimal>> peerRisks = peerLiveRisks(dealContext.getDeal(), account, tenantAccounts);
        checkAccountCeiling(account, appetite.getGlobalSimultaneousRiskPerAccountPercent(), peerRisks, ownAfterAct,
                checks);
        checkTenantCeiling(account, appetite.getGlobalSimultaneousRiskPerTenantPercent(), tenantAccounts, peerRisks,
                ownAfterAct, checks);
    }

    /**
     * Акт повышает живой риск своей сделки: живой риск после акта вместе с
     * риском акта больше доактного; доактный не измерен — повышение не
     * исключено (docs/spec/risk-limits.json, величина
     * {@code ownLiveRiskRaised}).
     */
    private Boolean ownLiveRiskRaised(BigDecimal liveRiskBeforeAct, BigDecimal ownAfterAct) {
        return isNull(liveRiskBeforeAct) || ownAfterAct.compareTo(liveRiskBeforeAct) > 0;
    }

    /**
     * Потолок счёта: прочие живые сделки счёта плюс эта после акта — не выше
     * процента счёта от ЖИВОЙ базы счёта, а не снимка сделки: уровень мерит
     * капитал счёта.
     */
    private void checkAccountCeiling(ExchangeAccount account, BigDecimal accountPercent,
                                     Map<Long, List<BigDecimal>> peerRisks, BigDecimal ownAfterAct,
                                     List<RiskCheckResult> checks) {
        if (isNull(account.getRiskBase())) {
            checks.add(RiskCheckResult.blocked(RiskCheckCode.BALANCE_INVALID,
                    "Account risk base is missing: account live risk ceiling is unmeasured", null));
            return;
        }
        BigDecimal peers = sumPeers(peerRisks, Set.of(account.getId()));
        if (isNull(peers)) {
            checks.add(RiskCheckResult.blocked(RiskCheckCode.RISK_PER_ACCOUNT_SIMULTANEOUS_EXCEEDED,
                    "Live risk of a peer deal on the account is not measured", null));
            return;
        }
        checkAgainst(peers.add(ownAfterAct), percentOf(accountPercent, account.getRiskBase()),
                RiskCheckCode.RISK_PER_ACCOUNT_SIMULTANEOUS_EXCEEDED, "account live risk ceiling", checks);
    }

    /**
     * Потолок тенанта: прочие живые сделки тенанта на счетах базы тенанта
     * плюс эта после акта — не выше процента тенанта от базы тенанта.
     * Неизмеренная база тенанта (валюта базы счёта сделки не известна) —
     * отказ кодом уровня, как неизмеренный сосед.
     */
    private void checkTenantCeiling(ExchangeAccount account, BigDecimal tenantPercent,
                                    List<ExchangeAccount> tenantAccounts, Map<Long, List<BigDecimal>> peerRisks,
                                    BigDecimal ownAfterAct, List<RiskCheckResult> checks) {
        if (isNull(account.getRiskBaseCurrency())) {
            checks.add(RiskCheckResult.blocked(RiskCheckCode.RISK_PER_TENANT_SIMULTANEOUS_EXCEEDED,
                    "Tenant risk base is not measured: risk base currency of the account is unknown", null));
            return;
        }
        Set<Long> accountIds = tenantAccounts.stream()
                .map(ExchangeAccount::getId)
                .collect(Collectors.toSet());
        BigDecimal peers = sumPeers(peerRisks, accountIds);
        if (isNull(peers)) {
            checks.add(RiskCheckResult.blocked(RiskCheckCode.RISK_PER_TENANT_SIMULTANEOUS_EXCEEDED,
                    "Live risk of a peer deal of the tenant is not measured", null));
            return;
        }
        BigDecimal tenantBase = tenantAccounts.stream()
                .map(ExchangeAccount::getRiskBase)
                .reduce(ZERO, BigDecimal::add);
        checkAgainst(peers.add(ownAfterAct), percentOf(tenantPercent, tenantBase),
                RiskCheckCode.RISK_PER_TENANT_SIMULTANEOUS_EXCEEDED, "tenant live risk ceiling", checks);
    }

    /**
     * Счета базы тенанта: в статусе {@code ACTIVE}, с наблюдённой базой и той
     * же валютой базы, что у счёта проверяемой сделки
     * (docs/spec/risk-limits.json, операнд {@code tenantRiskBase}). Разные
     * валюты без курса не складываются. Валюта счёта сделки не известна —
     * пусто.
     */
    private List<ExchangeAccount> tenantBaseAccounts(ExchangeAccount account) {
        if (isNull(account.getRiskBaseCurrency())) {
            return List.of();
        }
        return exchangeAccountDataService.findActiveByTenantInternalId(account.getTenantId()).stream()
                .filter(candidate -> nonNull(candidate.getRiskBase()))
                .filter(candidate -> Objects.equals(account.getRiskBaseCurrency(), candidate.getRiskBaseCurrency()))
                .collect(Collectors.toList());
    }

    /**
     * Живой риск ПРОЧИХ живых сделок счёта сделки и счетов базы тенанта —
     * одной выборкой на все счета: ключ счёта → {@code liveRiskNow} его
     * соседних сделок при нулевом акте и действующей защите; пустой член —
     * живой риск соседа не измерен. Проверяемая сделка в перечень не входит:
     * её слагаемое — живой риск после акта.
     */
    private Map<Long, List<BigDecimal>> peerLiveRisks(Deal deal, ExchangeAccount account,
                                                      List<ExchangeAccount> tenantAccounts) {
        Set<Long> accountIds = new HashSet<>();
        accountIds.add(account.getId());
        tenantAccounts.forEach(tenantAccount -> accountIds.add(tenantAccount.getId()));
        Map<Long, List<BigDecimal>> risks = new HashMap<>();
        dealDataService.findNonTerminalByExchangeAccountIds(accountIds).stream()
                .filter(peer -> isFalse(Objects.equals(deal.getId(), peer.getId())))
                .forEach(peer -> risks.computeIfAbsent(peer.getExchangeAccountId(), key -> new ArrayList<>())
                        .add(peerLiveRisk(peer)));
        return risks;
    }

    /**
     * {@code liveRiskNow} соседней сделки при нулевом акте и действующей
     * защите — та же форма, что у проверяемой; второй формы живого риска не
     * заводится. Граф соседа перечитывается тем же ходом, что у прохода;
     * неполный граф, нематериализованные правила инструмента либо живая
     * экспозиция без действующего уровня — не измерен.
     */
    private BigDecimal peerLiveRisk(Deal peer) {
        dealContextService.reloadRuntimeGraph(peer);
        if (isNotTrue(peer.graphComplete()) || isNull(peer.getInstrumentId())) {
            return null;
        }
        InstrumentExternalRules rules = rulesDataService
                .findByInstrumentId(peer.getInstrumentId(), peer.getExchangeAccountId())
                .orElse(null);
        if (isNull(rules)) {
            return null;
        }
        return liveRiskNow(peer, rules, entryAnchor(peer.livePosition(), null), peer.currentStopLevel());
    }

    /**
     * Сумма живого риска соседей на названных счетах; пусто — хоть один из
     * них не измерен, и нулём он не подменяется.
     */
    private BigDecimal sumPeers(Map<Long, List<BigDecimal>> peerRisks, Set<Long> accountIds) {
        BigDecimal sum = ZERO;
        for (Long accountId : accountIds) {
            for (BigDecimal risk : peerRisks.getOrDefault(accountId, List.of())) {
                if (isNull(risk)) {
                    return null;
                }
                sum = sum.add(risk);
            }
        }
        return sum;
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

    /**
     * Живой риск сделки по названному уровню: неисполненная доля живых ног
     * плюс живой эпизод до уровня (docs/spec/risk-limits.json, величина
     * {@code liveRiskNow}). Форма одна на проверяемую сделку — до акта и
     * после него — и на соседей уровня. Пусто — уровня нет при живом
     * эпизоде, и величина отказывает вычислением.
     */
    private BigDecimal liveRiskNow(Deal deal, InstrumentExternalRules rules, BigDecimal entryAnchor,
                                   BigDecimal stopLevel) {
        BigDecimal livePositionRisk = livePositionRiskAtStop(deal, rules, entryAnchor, stopLevel);
        if (isNull(livePositionRisk)) {
            return null;
        }
        return unfilledPlannedRisk(deal).add(livePositionRisk);
    }

    /** Что ещё может встать под удар: неисполненная доля живых входных ног. */
    private BigDecimal unfilledPlannedRisk(Deal deal) {
        return liveEntryLegs(deal).stream()
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
    private BigDecimal livePositionRiskAtStop(Deal deal, InstrumentExternalRules rules,
                                              BigDecimal entryAnchor, BigDecimal stopAfterAct) {
        Position live = deal.livePosition();
        if (isNull(live) || isNull(live.getExternalSize()) || live.getExternalSize().signum() == 0) {
            return ZERO;
        }
        if (isNull(stopAfterAct) || isNull(entryAnchor) || isNull(rules.contractValue())
                || isNull(rules.takerFeeRate())) {
            return null;
        }
        BigDecimal risk = RiskMath
                .lossAtStopPerUnit(deal.getDirection(), entryAnchor, stopAfterAct, rules.takerFeeRate())
                .multiply(live.getExternalSize())
                .multiply(rules.contractValue());
        return risk.signum() > 0 ? risk : ZERO;
    }

    /** Экспозиция сделки ДО акта: неисполненная доля живых ног плюс живой эпизод. */
    private BigDecimal dealNotional(Deal deal, InstrumentExternalRules rules, BigDecimal entryAnchor) {
        if (isNull(rules.contractValue())) {
            return ZERO;
        }
        BigDecimal legs = liveEntryLegs(deal).stream()
                .map(leg -> zeroIfNull(leg.getPlannedSizeContracts())
                        .subtract(zeroIfNull(leg.getAccumulatedFillSize()))
                        .multiply(rules.contractValue())
                        .multiply(zeroIfNull(leg.getPlannedEntryPrice())))
                .reduce(ZERO, BigDecimal::add);
        Position live = deal.livePosition();
        if (isNull(live) || isNull(live.getExternalSize()) || isNull(entryAnchor)) {
            return legs;
        }
        return legs.add(live.getExternalSize().multiply(rules.contractValue()).multiply(entryAnchor));
    }

    /**
     * Живые ВХОДНЫЕ ноги сделки — по всем траншам: потолки агрегатные.
     * Входную ногу отбирает её намерение ({@link Order#isEntryLeg()}) — тот
     * же предикат, что у писателя четвёрки чисел риска.
     *
     * <p><b>Ноги собираются обходом траншей, а не полем агрегата:</b> нога
     * висит на транше, а остаток неприписанных заявок агрегата — операнд
     * инварианта неприписанного живого риска, не потолков
     * (docs/models/domain/aggregate/Deal.md §Структура).
     */
    private List<Order> liveEntryLegs(Deal deal) {
        return emptyIfNull(deal.getTranches()).stream()
                .flatMap(tranche -> emptyIfNull(tranche.getOrders()).stream())
                .filter(order -> isTrue(order.isLive()))
                .filter(order -> isTrue(order.isEntryLeg()))
                .collect(Collectors.toList());
    }

    /**
     * Риск проверяемого акта: плановый риск ноги для risk-creating, ноль
     * для risk-weakening — новых контрактов такое действие не создаёт.
     *
     * <p><b>Отрицательный риск обрезается нулём</b>, как и живое слагаемое:
     * уровень за безубытком гасит СВОЁ слагаемое, а не чужие, — иначе он
     * вычитался бы из принятого и живого риска. На входной тропе ветвь
     * недостижима — сайзинг отказывает первым, а преконтроль отвергает
     * сторону уровня, — и клэмп есть охрана второго рубежа.
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
        BigDecimal risk = RiskMath.lossAtStopPerUnit(dealContext.getDeal().getDirection(), entryAnchor,
                        stop.getTriggerPrice(), rules.takerFeeRate())
                .multiply(calculatedAction.getCalculatedSize().getSizeContracts())
                .multiply(rules.contractValue());
        return risk.signum() > 0 ? risk : ZERO;
    }

    /**
     * Нотинал проверяемого акта; risk-weakening контрактов не создаёт.
     *
     * <p><b>Цена — плановая цена САМОГО акта, а не якорь живого эпизода</b>
     * (docs/rules/risk-policy.md, правило потолка нотинала сделки и
     * таблица «Риск акта зависит от класса действия»). Средняя цена эпизода
     * прайсит уже налитые контракты — своё слагаемое экспозиции сделки;
     * контракты акта налиться по ней не могут, и нотинал добора по средней
     * был бы занижен ровно при доборе выше неё — в разрешающую сторону.
     * Тот же нотинал читает проверка маржи (docs/spec/risk-limits.json,
     * операнд {@code actNotional}).
     */
    private BigDecimal actNotional(CalculatedStrategyAction calculatedAction, InstrumentExternalRules rules) {
        if (isFalse(isRiskCreatingEntry(calculatedAction.getSourceAction())) || isNull(rules.contractValue())) {
            return ZERO;
        }
        BigDecimal actPrice = calculatedAction.getCalculatedPrice().getRoundedPrice();
        if (isNull(actPrice)) {
            return ZERO;
        }
        return calculatedAction.getCalculatedSize().getSizeContracts()
                .multiply(rules.contractValue()).multiply(actPrice);
    }

    /**
     * Набор риска в окне сворачивания отвергается ПРЕКОНТРОЛЕМ, а не
     * только статусным ребром: строка исполнения, перенесённая через
     * проход сворачивания, доигрывается у транша, чей обработчик операнда
     * статуса сделки не имеет, и до ребра не доходит вовсе
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
     * она проходит: сделки доживают под своей защитой. Так же проходит и
     * защитное действие, чей уровень защиты транша не ослабляет, — подтяжка
     * стопа, перевод в безубыток, первая защита над непокрытым траншем.
     */
    private void checkSafetyRung(CalculatedStrategyAction calculatedAction, DealTranche tranche,
                                 StrategyTradeDirection direction, AccountInstrumentState pairState,
                                 List<RiskCheckResult> checks) {
        if (isFalse(pairState.hasStandingSafetyRung())
                || isFalse(inSafetyBlockSet(calculatedAction, tranche, direction))) {
            return;
        }
        checks.add(RiskCheckResult.blocked(RiskCheckCode.INSTRUMENT_SAFETY_HOLD,
                "Instrument stands in safety rung " + pairState.getSafetyRung()
                        + " whose block set covers this act class", null));
    }

    /**
     * Акт входит в блок-сет ступени: он создаёт риск либо ослабляет его
     * контроль. Защитное действие касается УРОВНЯ остановки убытка
     * (docs/spec/strategy-reference.json, величина
     * {@code isProtectiveAction}) и ослабляет контроль, когда его уровень
     * отступает от защиты транша (docs/spec/protection-coverage.json,
     * величина {@code actStopKeepsProtection}); уровень фиксации прибыли
     * контроля не ослабляет вовсе.
     *
     * <p><b>Уровень акта — только ОБЪЯВЛЕННЫЙ.</b> У трейлинга уровень
     * наблюдается после активации, и в момент постановки подтяжку нечем
     * доказать: он остаётся в блок-сете.
     *
     * <p><b>Читателей у предиката два</b> — стоящая ступень и торгуемость
     * инструмента (docs/rules/risk-validator-scope.md §«Граница общая с
     * блок-сетом холдов»); второй копии различителя не заводится.
     */
    private Boolean inSafetyBlockSet(CalculatedStrategyAction calculatedAction, DealTranche tranche,
                                     StrategyTradeDirection direction) {
        StrategyAction action = calculatedAction.getSourceAction();
        if (isTrue(isRiskCreatingEntry(action))) {
            return true;
        }
        boolean protective = action instanceof StrategyAlgoOrderAction algoAction
                && isTrue(algoAction.isProtective());
        if (isFalse(protective)) {
            return false;
        }
        if (isNull(tranche) || isFalse(StrategyLevelSource.DECLARED.equals(action.levelSource()))) {
            return true;
        }
        ResolvedStopLossPrice actStop = calculatedAction.getCalculatedPrice().getStopLossPrice();
        BigDecimal actLevel = isNull(actStop) ? null : actStop.getTriggerPrice();
        return isFalse(tranche.stopLevelKeepsProtection(actLevel, direction));
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
    private void checkFeeRate(Boolean touchesStopLevel, InstrumentExternalRules rules, DealContext dealContext,
                              List<RiskCheckResult> checks) {
        boolean episodeLive = nonNull(dealContext.getDeal().livePosition());
        if (isFalse(touchesStopLevel) && isFalse(episodeLive)) {
            return;
        }
        if (isNull(rules.takerFeeRate())) {
            checks.add(RiskCheckResult.blocked(RiskCheckCode.FEE_RATE_UNAVAILABLE,
                    "Taker fee rate is not resolved for the instrument fee group", null));
        }
    }

    /** Действие ставит либо переносит уровень остановки убытка. */
    private Boolean touchesStopLevel(CalculatedPrice price) {
        return nonNull(price.getStopLossPrice()) && nonNull(price.getStopLossPrice().getTriggerPrice());
    }

    /**
     * Проверки средств счёта — режим счёта и признаки обязательств — по
     * СВЕЖЕМУ снимку средств; достаточность свободной маржи вызывающий меряет по
     * возвращённой строке, и только у действия, создающего риск
     * (docs/components/RiskValidator.md §«Проверки средств счёта»).
     *
     * <p><b>Меряются только на свежем снимке — тем же предикатом и той же
     * толерантностью, по которым добычу заказывает обработчик предвходовой
     * проверки.</b> Акт, создающий риск, с несвежим снимком сюда не доходит
     * ни на одной стадии транша: на входе снимок добыт до работы, на
     * сопровождении исполнитель откладывает акт до расчёта, и проход
     * заказывает добычу (отсрочка до свежего снимка —
     * docs/components/models/ActionPlan.md). Несвежий снимок здесь бывает
     * только у действий, риска не создающих, — защитного и снятия защиты:
     * они свежести не ждут, и проверки средств у них остаются неизмеренными,
     * потому что отсрочка защиты ради проверки контура ослабляла бы то, что
     * защищает.
     *
     * <p><b>Кода несвежести у преконтроля нет.</b> Отказ по несвежести при
     * живом риске — код вне карв-аута, и карта реакции ведёт его в
     * {@code ERROR}; проверка по старому снимку мерила бы не то состояние
     * счёта и ошибалась бы в разрешающую сторону
     * (docs/components/models/RiskCheckResult.md).
     *
     * <p><b>Свежий снимок без полной строки расчётной валюты — отказ
     * {@code BALANCE_INVALID}:</b> снимок, обязанный её нести, негоден, а
     * пустой остаток нулём и благоприятным умолчанием не подменяется
     * (docs/rules/absent-value-semantics.md).
     *
     * @return полная строка расчётной валюты свежего снимка; пусто —
     *         проверки средств не меряются либо снимок негоден
     */
    private Balance checkAccountFunds(DealContext dealContext, List<RiskCheckResult> checks) {
        if (isFalse(dealContext.balanceFresh(properties.getBalanceFreshness()))) {
            return null;
        }
        checkAccountMode(dealContext.getBalanceContainer(), checks);
        Balance settlement = dealContext.settlementBalance();
        if (isNull(settlement) || isNull(settlement.getExternalCashBalance())
                || isNull(settlement.getExternalEquity()) || isNull(settlement.getExternalAvailableBalance())) {
            checks.add(RiskCheckResult.blocked(RiskCheckCode.BALANCE_INVALID,
                    "Fresh balance snapshot carries no complete settlement currency row", null));
            return null;
        }
        checkBorrowOrDebt(settlement, checks);
        return settlement;
    }

    /**
     * Торгуем только своими средствами (docs/rules/trading-constraints.md):
     * отрицательный денежный остаток либо отрицательный капитал строки
     * расчётной валюты — обязательство счёта в этой валюте
     * (docs/spec/risk-limits.json, величина {@code borrowOrDebtDetected}).
     *
     * <p><b>Область — всякое проверяемое действие</b>, как у режима маржи:
     * признак описывает состояние контура, а не класс акта.
     *
     * <p><b>Эта проверка — половина пары, и заём она не ловит.</b> Знак
     * остатка ловит долг без займа; заём площадка даёт лишь в режимах счёта
     * вне контура, и исключает его вторая половина — режим счёта. Поэтому
     * явных полей обязательств площадки снимок не несёт намеренно: в
     * контурном режиме они описывали бы то, чего нет по построению. Долг
     * иной валюты операндом акта не является, и снимок остаётся по одной
     * расчётной валюте (docs/components/RiskValidator.md §«Проверки средств
     * счёта»).
     */
    private void checkBorrowOrDebt(Balance settlement, List<RiskCheckResult> checks) {
        BigDecimal cash = settlement.getExternalCashBalance();
        BigDecimal equity = settlement.getExternalEquity();
        if (cash.signum() < 0 || equity.signum() < 0) {
            checks.add(RiskCheckResult.blocked(RiskCheckCode.BORROW_OR_DEBT_DETECTED,
                    "Settlement currency " + settlement.getExternalCurrency() + " carries a liability: cash "
                            + cash + ", equity " + equity, cash.min(equity)));
        }
    }

    /**
     * Режим счёта и режим позиций — посылки контура, а не данность
     * (docs/rules/trading-constraints.md): контур держит фьючерсный режим, в
     * котором площадка займа не даёт по построению, и нетто-позиции. Иное —
     * отказ {@code ACCOUNT_MODE_OUT_OF_CONTOUR} (docs/spec/risk-limits.json,
     * величина {@code accountModeOutOfContour}).
     *
     * <p><b>Пустой режим свежего снимка — тоже выход из контура:</b>
     * непроверенная посылка выполненной не читается (docs/concept.md П1).
     * Меряется на том же свежем снимке, что признаки обязательств, и на
     * всяком проверяемом действии: признак описывает контур, а не класс
     * акта. Строки расчётной валюты проверка не требует — режим несёт
     * контейнер снимка, а не его валютная строка. Сам вопрос «режим вне
     * контура» задаёт модель снимка —
     * {@link BalanceContainer#isAccountModeOutOfContour()}.
     */
    private void checkAccountMode(BalanceContainer container, List<RiskCheckResult> checks) {
        if (isFalse(container.isAccountModeOutOfContour())) {
            return;
        }
        checks.add(RiskCheckResult.blocked(RiskCheckCode.ACCOUNT_MODE_OUT_OF_CONTOUR,
                "Account mode out of contour: account " + container.getAccountMode()
                        + ", positions " + container.getPositionMode(), null));
    }

    /**
     * Свободная маржа счёта против маржи, которую потребует риск-создающий
     * акт (docs/spec/risk-limits.json, величины {@code actRequiredMargin} и
     * {@code balanceNotEnoughBlocksAction}): нотинал акта по его плановой
     * цене, делённый на рабочее плечо пары, плюс комиссия открытия по
     * ставке тейкера. Не хватает — отказ {@code BALANCE_NOT_ENOUGH}, и
     * реакция на него — карв-аут: без проверки заявку отвергала бы площадка,
     * а её отказ уводил бы сделку в аварийный контур.
     *
     * <p><b>Снимок не видит ног, поставленных после него.</b> Живые входные
     * ноги сделки, которых площадка не подтвердила либо подтвердила позже
     * момента снимка, прибавляются к требованию своим нотиналом по плановой
     * цене: иначе уровни сетки, входящие проходами подряд внутри
     * толерантности, делили бы один и тот же свободный остаток.
     *
     * <p>Плечо и ставка здесь не проверяются: пустое плечо у акта, создающего
     * риск, отвергает {@link #checkLeverage}, пустую ставку у акта со
     * стопом — {@link #checkFeeRate}; мерить без них нечем, и вердикт уже
     * отказ.
     */
    private void checkBalanceEnough(CalculatedStrategyAction calculatedAction, DealContext dealContext,
                                    InstrumentExternalRules rules, AccountInstrumentState pairState,
                                    Balance settlement, List<RiskCheckResult> checks) {
        Integer leverage = pairState.getLeverage();
        if (isFalse(isRiskCreatingEntry(calculatedAction.getSourceAction())) || isNull(leverage)
                || leverage <= 0 || isNull(rules.takerFeeRate())) {
            return;
        }
        BigDecimal actPrice = calculatedAction.getCalculatedPrice().getRoundedPrice();
        if (isNull(actPrice)) {
            checks.add(RiskCheckResult.blocked(RiskCheckCode.CALCULATED_ACTION_INVALID,
                    "Act price is not resolved: required margin is unmeasured", null));
            return;
        }
        BigDecimal notional = actNotional(calculatedAction, rules).add(legsUnreflectedNotional(dealContext, rules));
        BigDecimal required = notional.divide(new BigDecimal(leverage), DomainMath.CONTEXT)
                .add(notional.multiply(rules.takerFeeRate()));
        BigDecimal available = settlement.getExternalAvailableBalance();
        if (required.compareTo(available) > 0) {
            checks.add(RiskCheckResult.blocked(RiskCheckCode.BALANCE_NOT_ENOUGH,
                    "Required margin " + required + " above available balance " + available, required));
        }
    }

    /**
     * Нотинал живых входных ног сделки, которых снимок средств не отражает:
     * площадка их не подтвердила, либо подтвердила позже момента снимка.
     */
    private BigDecimal legsUnreflectedNotional(DealContext dealContext, InstrumentExternalRules rules) {
        OffsetDateTime snapshotAt = dealContext.getBalanceContainer().getExternalUpdatedAt();
        return liveEntryLegs(dealContext.getDeal()).stream()
                .filter(leg -> isNull(leg.getExternalCreatedAt()) || leg.getExternalCreatedAt().isAfter(snapshotAt))
                .map(leg -> zeroIfNull(leg.getPlannedSizeContracts())
                        .multiply(rules.contractValue())
                        .multiply(zeroIfNull(leg.getPlannedEntryPrice())))
                .reduce(ZERO, BigDecimal::add);
    }

    /**
     * Торгуемость инструмента запирает НАБОР риска, а не защиту
     * (docs/rules/risk-validator-scope.md §«Торгуемость инструмента
     * запирает набор риска, а не защиту»; форма — docs/spec/risk-limits.json,
     * величина {@code instrumentNotLiveBlocksAction}).
     *
     * <p><b>Область та же, что у блок-сета ступени, и различитель один</b> —
     * {@link #inSafetyBlockSet}. Защитное действие, уровня своего транша не
     * ослабляющее, и постановка уровня фиксации прибыли проходят: принятый
     * риск доживает под своей защитой. Гейт, запирающий постановку защиты,
     * снимал бы её ровно там, где риск повышен, а при живом риске уводил бы
     * сделку в аварийный контур — к снятию по рынку на инструменте, который
     * не торгуется.
     */
    private void checkInstrumentLive(InstrumentExternalRules rules, CalculatedStrategyAction calculatedAction,
                                     DealTranche tranche, StrategyTradeDirection direction,
                                     List<RiskCheckResult> checks) {
        if (isTrue(rules.isLive()) || isFalse(inSafetyBlockSet(calculatedAction, tranche, direction))) {
            return;
        }
        checks.add(RiskCheckResult.blocked(RiskCheckCode.INSTRUMENT_NOT_LIVE,
                "Instrument not tradeable: " + rules.getStatus() + ", act class is in the risk-taking block set",
                null));
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
     * (docs/rules/trading-constraints.md).
     *
     * <p><b>Пустое плечо у акта, создающего риск, — отказ, а не молчание.</b>
     * Исполнитель постановки пустого плеча площадке не пишет, и вход
     * наливается на том плече, которое на счёте стояло, — то есть цену
     * ликвидации задаёт значение, которого никто не назначал. Благоприятное
     * умолчание запрещено (docs/rules/absent-value-semantics.md). Действие,
     * риска не создающее, пустым плечом не отвергается: плеча оно площадке
     * не пишет, а отказ переносу защиты оставил бы позицию без него.
     *
     * <p><b>Плечо выше предела плеча конфигурации у акта, создающего риск, —
     * тот же код.</b> Назначение выше предела отвергает поверхность, но предел
     * мог понизиться после назначения, и назначенное значение перестало быть
     * допустимым: исход меняется тем же ходом — назначением плеча.
     *
     * <p>Пустой биржевой максимум сверять не с чем: его охраняет площадка.
     */
    private void checkLeverage(StrategyAction action, AccountInstrumentState pairState,
                               InstrumentExternalRules rules, RiskAppetite appetite,
                               List<RiskCheckResult> checks) {
        if (isNull(pairState.getLeverage()) && isTrue(isRiskCreatingEntry(action))) {
            checks.add(RiskCheckResult.blocked(RiskCheckCode.LEVERAGE_NOT_CONFIGURED,
                    "Leverage is not assigned for the account on the instrument", null));
            return;
        }
        if (isTrue(isRiskCreatingEntry(action)) && isTrue(appetite.leverageAboveLimit(pairState.getLeverage()))) {
            checks.add(RiskCheckResult.blocked(RiskCheckCode.LEVERAGE_NOT_CONFIGURED,
                    "Leverage above the configured max leverage " + appetite.getGlobalMaxLeverage(),
                    new BigDecimal(pairState.getLeverage())));
        }
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

    /**
     * Risk-creating вход — order-action, открывающий либо наращивающий
     * позицию: класс «risk-creating / increasing» дома
     * (docs/rules/risk-policy.md §«Риск акта зависит от класса действия»).
     */
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
     *
     * <p><b>У risk-creating входа сторона мерится дважды</b> — от якоря и от
     * плановой цены своей ноги, строго за ней (docs/spec/stop-distance.json,
     * величина {@code stopOnOwnLegLossSide}): на живом эпизоде якорь — средняя
     * цена, и уровень, посчитанный от неё, может лечь между ценой ноги и
     * средней. За безубытком ноги такой уровень отвергает сайзинг, а полосу
     * до безубытка не ловил ни один рубеж; на ней же ложна посылка ценового
     * приоритета отложенного покрытия встроенной защиты
     * (docs/rules/live-risk-protection.md). Без живого эпизода якорь и есть
     * цена ноги, и вторая мера совпадает с первой. Код отказа тот же: предмет
     * один — уровень не на убыточной стороне.
     *
     * <p>Пустая цена ноги вторую меру пропускает, но не молча: у входа её
     * пустоту отвергает проверка слагаемых акта ({@code CALCULATED_ACTION_INVALID}),
     * и сверять сторону здесь не с чем.
     *
     * @param legPrice плановая (округлённая) цена своей ноги действия
     */
    private void checkStopLossSide(StrategyAction action, ResolvedStopLossPrice stopLoss, BigDecimal entryAnchor,
                                   BigDecimal legPrice, StrategyTradeDirection direction,
                                   List<RiskCheckResult> checks) {
        if (isNull(stopLoss) || isNull(stopLoss.getTriggerPrice()) || isNull(entryAnchor)) {
            return;
        }
        if (isNull(action) || isFalse(StrategyPlacementRole.PRIMARY.equals(action.placementRole()))
                || isFalse(StrategyLevelSource.DECLARED.equals(action.levelSource()))) {
            return;
        }
        BigDecimal trigger = stopLoss.getTriggerPrice();
        if (isFalse(onLossSide(trigger, entryAnchor, direction))) {
            checks.add(RiskCheckResult.blocked(RiskCheckCode.STOP_LOSS_INVALID_SIDE,
                    "Stop-loss on wrong side of entry " + entryAnchor, trigger));
            return;
        }
        if (isTrue(isRiskCreatingEntry(action)) && nonNull(legPrice)
                && isFalse(onLossSide(trigger, legPrice, direction))) {
            checks.add(RiskCheckResult.blocked(RiskCheckCode.STOP_LOSS_INVALID_SIDE,
                    "Stop-loss on wrong side of own entry leg price " + legPrice, trigger));
        }
    }

    /**
     * Уровень лежит строго на убыточной стороне от опорной цены: у длинной
     * ниже, у короткой выше. Равенство убыточной стороной не читается.
     */
    private Boolean onLossSide(BigDecimal trigger, BigDecimal reference, StrategyTradeDirection direction) {
        return StrategyTradeDirection.LONG.equals(direction)
                ? trigger.compareTo(reference) < 0
                : trigger.compareTo(reference) > 0;
    }

    /**
     * Перенос уровня остановки убытка обязан лечь ЗА марк-ценой живой
     * позиции — там, куда цена уже прошла: у длинной ниже марк-цены, у
     * короткой выше (docs/spec/stop-distance.json, величина
     * {@code transferStopBehindMark}). Иначе перенос в безубыток до прохода
     * цены за уровень исполняется сразу — убытком под именем «безубыток» — либо
     * выбивается шумом.
     *
     * <p><b>Операнд — марк-цена, потому что по ней срабатывает стоп</b>
     * (docs/rules/strategy-validation.md, база срабатывания защиты); берётся
     * она с живой позиции графа, нового чтения проверка не заводит.
     * Ненаблюдённая марк-цена — отказ: прохода цены тогда не доказывает ничто.
     *
     * <p><b>Область — перенос за якорь, на прибыльную сторону.</b> Отказ здесь
     * откладывает действие, а позицию держит прежний уровень. Первичной
     * постановке отложить было бы нечем: уровень, оказавшийся по ту сторону
     * рынка, срабатывает выходом — и это ровно worst-case выход, который
     * позиции обещан. Перенос, ужесточающий стоп на УБЫТОЧНОЙ стороне, тоже
     * не откладывается: цена, уже прошедшая новый уровень, закрывает позицию
     * с убытком меньше прежнего стопа, а отсрочка держала бы худший.
     */
    private void checkTransferStopBehindMark(StrategyAction action, ResolvedStopLossPrice stopLoss,
                                             Position position, BigDecimal entryAnchor,
                                             StrategyTradeDirection direction, List<RiskCheckResult> checks) {
        if (isNull(action) || isFalse(StrategyPlacementRole.TRANSFER.equals(action.placementRole()))
                || isNull(stopLoss) || isNull(stopLoss.getTriggerPrice())
                || isNull(position) || isFalse(position.hasLiveSize())) {
            return;
        }
        BigDecimal trigger = stopLoss.getTriggerPrice();
        if (nonNull(entryAnchor) && RiskMath.signedStopDistance(direction, entryAnchor, trigger).signum() > 0) {
            return;
        }
        BigDecimal mark = position.getExternalMarkPrice();
        if (isNull(mark)) {
            checks.add(RiskCheckResult.blocked(RiskCheckCode.STOP_LOSS_BEYOND_MARK_PRICE,
                    "Stop-loss transfer while the mark price of the live position is not observed", trigger));
            return;
        }
        boolean behindMark = StrategyTradeDirection.LONG.equals(direction)
                ? trigger.compareTo(mark) < 0
                : trigger.compareTo(mark) > 0;
        if (isFalse(behindMark)) {
            checks.add(RiskCheckResult.blocked(RiskCheckCode.STOP_LOSS_BEYOND_MARK_PRICE,
                    "Stop-loss transfer not behind mark price " + mark, trigger));
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

    /**
     * Инвариант «ликвидация за стопом»: ближайший к ликвидации уровень
     * остановки убытка лежит между ценой и границей ликвидации
     * (docs/rules/risk-policy.md, правило о ликвидации до входа).
     *
     * <p><b>Тропы две, и различает их класс акта.</b> Акт, создающий риск,
     * меняет позицию, и её ликвидацию площадка ещё не называла: граница —
     * ОЦЕНКА позиции после акта (docs/spec/risk-limits.json, величина
     * {@code entryStopBeforeLiquidation}). У прочих актов позиция не
     * меняется, и граница — цена ликвидации, которую площадка называет у
     * живой позиции.
     *
     * <p><b>Неизмеренная оценка — тот же отказ, а не проход:</b> тиров нет,
     * позиция выше последнего тира, уровень живой позиции не резолвится —
     * неизмеренный инвариант выполненным не читается (docs/concept.md П1).
     *
     * <p><b>Операнд, чья пустота уже отказ СВОИМ кодом, проверку молчит</b>,
     * как у прочих уровневых проверок и у достаточности маржи: уровня акта
     * нет — {@code RISK_CREATING_ENTRY_WITHOUT_STOP}; якоря либо плановой
     * цены акта нет — {@code CALCULATED_ACTION_INVALID} у потолков; плеча
     * нет — {@code LEVERAGE_NOT_CONFIGURED}; ставки нет —
     * {@code FEE_RATE_UNAVAILABLE}. Вердикт в каждом из этих случаев уже
     * отказ, и второй код о том же операнде диагностики не добавил бы.
     */
    private void checkLiquidation(CalculatedStrategyAction calculatedAction, DealContext dealContext,
                                  InstrumentExternalRules rules, AccountInstrumentState pairState,
                                  BigDecimal entryAnchor, List<RiskCheckResult> checks) {
        StrategyTradeDirection direction = dealContext.getDeal().getDirection();
        ResolvedStopLossPrice actStop = calculatedAction.getCalculatedPrice().getStopLossPrice();
        if (isFalse(isRiskCreatingEntry(calculatedAction.getSourceAction()))) {
            checkLiquidationGuard(actStop, dealContext.getDeal().livePosition(), direction, checks);
            return;
        }
        if (isNull(actStop) || isNull(actStop.getTriggerPrice()) || isNull(entryAnchor)
                || isNull(calculatedAction.getCalculatedPrice().getRoundedPrice())
                || isNull(pairState.getLeverage()) || isNull(rules.takerFeeRate())) {
            return;
        }
        BigDecimal nearestStop = stopNearestLiquidation(actStop.getTriggerPrice(), dealContext);
        if (isNull(nearestStop)) {
            checks.add(RiskCheckResult.blocked(RiskCheckCode.STOP_LOSS_TOO_CLOSE_TO_LIQUIDATION,
                    "Liquidation before stop not measured: stop level of the position after the act"
                            + " is not resolved", null));
            return;
        }
        BigDecimal bound = liquidationBoundAfterAct(calculatedAction, dealContext, rules, pairState, entryAnchor);
        if (isNull(bound)) {
            checks.add(RiskCheckResult.blocked(RiskCheckCode.STOP_LOSS_TOO_CLOSE_TO_LIQUIDATION,
                    "Liquidation before stop not measured: no position tier covers the position after the act",
                    nearestStop));
            return;
        }
        boolean beforeLiquidation = StrategyTradeDirection.LONG.equals(direction)
                ? nearestStop.compareTo(bound) > 0
                : nearestStop.compareTo(bound) < 0;
        if (isFalse(beforeLiquidation)) {
            checks.add(RiskCheckResult.blocked(RiskCheckCode.STOP_LOSS_TOO_CLOSE_TO_LIQUIDATION,
                    "Stop-loss not ahead of liquidation bound after the act " + bound, nearestStop));
        }
    }

    /**
     * Уровень остановки убытка позиции после акта, ближайший к ликвидации:
     * наименее благоприятный из устанавливаемого актом и действующего на всю
     * позицию — у LONG нижний, у SHORT верхний (docs/spec/risk-limits.json,
     * величина {@code stopNearestLiquidation}). Позиция на инструменте одна,
     * и первым к её ликвидации обязан прийти самый дальний стоп.
     *
     * <p><b>Пусто при живом эпизоде без действующего уровня:</b> хоть один
     * транш с экспозицией своего уровня не несёт, и уровень акта вместо
     * неизвестного не подставляется. Живого эпизода и действующего уровня
     * нет — уровень акта.
     */
    private BigDecimal stopNearestLiquidation(BigDecimal actLevel, DealContext dealContext) {
        BigDecimal liveLevel = dealContext.getDeal().currentStopLevel();
        if (isNull(liveLevel)) {
            return isTrue(liveEpisode(dealContext.getDeal().livePosition())) ? null : actLevel;
        }
        return StrategyTradeDirection.LONG.equals(dealContext.getDeal().getDirection())
                ? actLevel.min(liveLevel)
                : actLevel.max(liveLevel);
    }

    /**
     * Граница ликвидации, с которой сверяется стоп: оценка позиции после
     * акта, а при живом эпизоде с наблюдённой ценой ликвидации площадки —
     * ближайшая ко входу из двух, у LONG верхняя, у SHORT нижняя
     * (docs/spec/risk-limits.json, величина {@code liquidationBoundAfterAct}).
     * Площадка видит маржу, убывшую на финансировании, которой оценка не
     * видит; строже она — лишний отказ, а не пропуск. Оценки нет — пусто.
     */
    private BigDecimal liquidationBoundAfterAct(CalculatedStrategyAction calculatedAction, DealContext dealContext,
                                                InstrumentExternalRules rules, AccountInstrumentState pairState,
                                                BigDecimal entryAnchor) {
        BigDecimal estimate = estimatedLiquidationPrice(calculatedAction, dealContext, rules, pairState,
                entryAnchor);
        Position live = dealContext.getDeal().livePosition();
        if (isNull(estimate) || isFalse(liveEpisode(live)) || isNull(live.getExternalLiquidationPrice())) {
            return estimate;
        }
        return StrategyTradeDirection.LONG.equals(dealContext.getDeal().getDirection())
                ? estimate.max(live.getExternalLiquidationPrice())
                : estimate.min(live.getExternalLiquidationPrice());
    }

    /**
     * Оценка цены ликвидации изолированной позиции после акта
     * (docs/spec/risk-limits.json, величина {@code estimatedLiquidationPrice}):
     * у LONG {@code avg·(1 − 1/L + f)/(1 − mmr − f)}, у SHORT
     * {@code avg·(1 + 1/L − f)/(1 + mmr + f)}. Обе комиссии стоят на стороне
     * приближения ликвидации — ошибка консервативна.
     *
     * <p><b>Пусто — не измерено:</b> тир позиции не найден либо рабочее
     * плечо пары непозитивно. Ни то, ни другое нулём и соседним значением
     * не подменяется.
     */
    private BigDecimal estimatedLiquidationPrice(CalculatedStrategyAction calculatedAction, DealContext dealContext,
                                                 InstrumentExternalRules rules, AccountInstrumentState pairState,
                                                 BigDecimal entryAnchor) {
        Integer leverage = pairState.getLeverage();
        BigDecimal feeRate = rules.takerFeeRate();
        if (isNull(leverage) || leverage <= 0 || isNull(feeRate)) {
            return null;
        }
        BigDecimal contracts = postActContracts(calculatedAction, dealContext);
        BigDecimal notional = postActNotional(calculatedAction, dealContext, rules, entryAnchor);
        BigDecimal maintenanceMarginRate = postActMaintenanceMarginRate(rules.getPositionTiers(), contracts);
        if (isNull(maintenanceMarginRate) || contracts.signum() <= 0) {
            return null;
        }
        BigDecimal average = notional.divide(contracts.multiply(rules.contractValue()), DomainMath.CONTEXT);
        BigDecimal inverseLeverage = ONE.divide(new BigDecimal(leverage), DomainMath.CONTEXT);
        boolean isLong = StrategyTradeDirection.LONG.equals(dealContext.getDeal().getDirection());
        BigDecimal numerator = isLong
                ? ONE.subtract(inverseLeverage).add(feeRate)
                : ONE.add(inverseLeverage).subtract(feeRate);
        BigDecimal denominator = isLong
                ? ONE.subtract(maintenanceMarginRate).subtract(feeRate)
                : ONE.add(maintenanceMarginRate).add(feeRate);
        if (denominator.signum() <= 0) {
            return null;
        }
        return average.multiply(numerator).divide(denominator, DomainMath.CONTEXT);
    }

    /**
     * Размер позиции, которую сделка будет держать, когда нальются её живые
     * ноги и проверяемый акт: неисполненные контракты живых ног входа, живой
     * эпизод и акт (docs/spec/risk-limits.json, величина
     * {@code postActContracts}). Слагаемые те же, что у потолка нотинала
     * сделки, и по той же причине не пересекаются.
     */
    private BigDecimal postActContracts(CalculatedStrategyAction calculatedAction, DealContext dealContext) {
        BigDecimal legs = liveEntryLegs(dealContext.getDeal()).stream()
                .map(RiskValidator::legUnfilledContracts)
                .reduce(ZERO, BigDecimal::add);
        Position live = dealContext.getDeal().livePosition();
        BigDecimal episode = isTrue(liveEpisode(live)) ? live.getExternalSize() : ZERO;
        return legs.add(episode).add(calculatedAction.getCalculatedSize().getSizeContracts());
    }

    /**
     * Нотинал той же позиции — левая сторона неравенства потолка нотинала
     * (docs/spec/risk-limits.json, величина {@code postActNotional}):
     * экспозиция сделки до акта плюс нотинал акта. Второй суммы не
     * заводится — слагаемые считают те же формы, что у потолка.
     *
     * <p>Плановая цена акта и якорь живого эпизода здесь непусты: их
     * пустоту проверка отвергает раньше своими кодами, и нулём слагаемое не
     * подменяется ({@link #checkLiquidation}).
     */
    private BigDecimal postActNotional(CalculatedStrategyAction calculatedAction, DealContext dealContext,
                                       InstrumentExternalRules rules, BigDecimal entryAnchor) {
        return dealNotional(dealContext.getDeal(), rules, entryAnchor).add(actNotional(calculatedAction, rules));
    }

    /**
     * Ставка поддерживающей маржи тира, в который ляжет позиция после акта
     * (docs/spec/risk-limits.json, величина
     * {@code postActMaintenanceMarginRate}): на стыке тиров — большая,
     * ликвидация тогда ближе и оценка консервативна.
     *
     * <p><b>Пусто — тира нет:</b> перечень не материализован либо позиция
     * выше последнего тира. Ставка первого тира вместо неизвестной не
     * подставляется — у больших позиций ставка больше. Покрывающий тир без
     * ставки делает пустым и ответ: большая из известных могла бы оказаться
     * меньше неизвестной. Покрывает ли тир размер, отвечает сам тир —
     * {@link PositionTier#covers(BigDecimal)}.
     */
    private static BigDecimal postActMaintenanceMarginRate(List<PositionTier> tiers, BigDecimal contracts) {
        List<PositionTier> covering = emptyIfNull(tiers).stream()
                .filter(tier -> nonNull(tier) && isTrue(tier.covers(contracts)))
                .collect(Collectors.toList());
        if (isEmpty(covering) || covering.stream().anyMatch(tier -> isNull(tier.maintenanceMarginRate()))) {
            return null;
        }
        return covering.stream()
                .map(PositionTier::maintenanceMarginRate)
                .max(BigDecimal::compareTo)
                .orElse(null);
    }

    /** Неисполненная доля ноги входа в контрактах — налитое уже стои́т в живом эпизоде. */
    private static BigDecimal legUnfilledContracts(Order leg) {
        return zeroIfNull(leg.getPlannedSizeContracts()).subtract(zeroIfNull(leg.getAccumulatedFillSize()));
    }

    /**
     * Сверка стопа с ценой ликвидации, которую площадка называет у живой
     * позиции, — тропа актов, риска не создающих: позиция ими не меняется.
     * Цена не наблюдена — проверка не меряется.
     */
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

    /**
     * Себестоимость, от которой меряется дистанция: средняя цена живого
     * эпизода либо, пока эпизода нет, плановая цена действия.
     *
     * <p><b>Ветвь, а не откат</b> (docs/spec/stop-distance.json,
     * {@code entryAnchor}): у живого эпизода с ещё не наблюдённой средней
     * ценой откат к плановой был бы благоприятным умолчанием — якорь тогда
     * пуст, и риск-создающий акт отказывает вычислением. Живой эпизод —
     * предикат дома, конъюнкция активного статуса и положительного
     * размера (docs/spec/protection-coverage.json, {@code hasLiveEpisode}):
     * активная строка с нулевым размером эпизодом не является, и её средняя
     * цена якорем не становится.
     */
    private BigDecimal entryAnchor(Position position, CalculatedPrice price) {
        if (isTrue(liveEpisode(position))) {
            return position.getExternalAverageEntryPrice();
        }
        return isNull(price) ? null : price.getRoundedPrice();
    }

    private static Boolean liveEpisode(Position position) {
        return nonNull(position) && isTrue(position.hasLiveRisk());
    }

    /** Per-order лимит размера по режиму цены: EXPLICIT — limit-лимит, иначе market-лимит. */
    private BigDecimal applicableMaxSize(InstrumentExternalRules rules, CalculatedPrice price) {
        if (PriceMode.EXPLICIT.equals(price.getPriceMode())) {
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

    /**
     * Свёртка перечня в решение: перечень несёт только отказы, поэтому
     * решение блокирующее ровно при непустом перечне
     * (docs/components/models/RiskCheckResult.md §«Исход проверки — только
     * отказ»).
     */
    private RiskValidationResult aggregate(List<RiskCheckResult> checks) {
        RiskDecision decision = isEmpty(checks) ? RiskDecision.ALLOWED : RiskDecision.BLOCKED;
        return RiskValidationResult.builder()
                .decision(decision)
                .checks(checks)
                .comment("risk validation " + decision)
                .build();
    }
}
