package com.example.strategies.domain.validation;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;
import static org.apache.commons.collections4.MapUtils.emptyIfNull;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.strategies.api.model.request.CreateStrategyApiRequest;
import com.example.strategies.api.model.request.UpdateStrategyStatusApiRequest;
import com.example.strategies.api.model.strategy.AtrParamsApiModel;
import com.example.strategies.api.model.strategy.BollingerBandsParamsApiModel;
import com.example.strategies.api.model.strategy.EfficiencyRatioParamsApiModel;
import com.example.strategies.api.model.strategy.EmaParamsApiModel;
import com.example.strategies.api.model.strategy.IndicatorParamsApiModel;
import com.example.strategies.api.model.strategy.MacdParamsApiModel;
import com.example.strategies.api.model.strategy.MarketStructureParamsApiModel;
import com.example.strategies.api.model.strategy.RsiParamsApiModel;
import com.example.strategies.api.model.strategy.StochasticParamsApiModel;
import com.example.strategies.api.model.strategy.StopLossSettingsApiModel;
import com.example.strategies.api.model.strategy.StrategyActionApiModel;
import com.example.strategies.api.model.strategy.StrategyAlgoOrderActionApiModel;
import com.example.strategies.api.model.strategy.StrategyAttachedProtectionSettingsApiModel;
import com.example.strategies.api.model.strategy.StrategyConditionApiModel;
import com.example.strategies.api.model.strategy.StrategyConditionOperandApiModel;
import com.example.strategies.api.model.strategy.StrategyConditionRuleApiModel;
import com.example.strategies.api.model.strategy.StrategyDetailApiModel;
import com.example.strategies.api.model.strategy.StrategyIndicatorSettingApiModel;
import com.example.strategies.api.model.strategy.StrategyMarketPhaseRuleApiModel;
import com.example.strategies.api.model.strategy.StrategyMarketPhaseSettingApiModel;
import com.example.strategies.api.model.strategy.StrategyMarketStructureSettingApiModel;
import com.example.strategies.api.model.strategy.StrategyOrderActionApiModel;
import com.example.strategies.api.model.strategy.StrategyPositionActionApiModel;
import com.example.strategies.api.model.strategy.StrategyStepApiModel;
import com.example.strategies.api.model.strategy.StrategyTrancheApiModel;
import com.example.tradingbot.domain.util.DomainMath;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.MarketDataExpiredAction;
import com.example.tradingbot.domain.model.aggregate.strategy.PhaseEntryPolicy;
import com.example.tradingbot.domain.model.aggregate.strategy.Strategy;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStepType;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StopLossCalculationType;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyActionType;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyPriceBaseType;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyPriceOffsetSide;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyPriceSource;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyTradeDirection;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.ConstantValueType;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.IndicatorComponent;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.Destiny;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionOperator;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionRuleType;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionSourceType;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.strategies.domain.model.TenantRiskAppetite;
import com.example.tradingbot.domain.util.Constants;
import com.example.tradingbot.domain.model.trade.candle.TimeFrame;
import com.example.tradingbot.domain.model.trade.indicator.IndicatorValue;
import com.example.tradingbot.domain.model.trade.market_phase.MarketPhase;
import com.example.tradingbot.domain.model.trade.market_structure.MarketBreakoutEvent;
import com.example.tradingbot.domain.model.trade.market_structure.MarketStructure;
import com.example.tradingbot.domain.util.IndicatorComponents;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.apache.commons.lang3.EnumUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Create-валидация стратегии: структурно-ссылочная, которой владеет
 * шаг 2 (400) — разрешённые enum'ы, парс duration, уникальность
 * ключей настроек/действий, разрешённость ссылок в рамках детали,
 * «ровно одна деталь на каждую фазу», матрица политика×фаза,
 * sanity warmup-override, контракт операндов по типу правила
 * (docs/rules/strategy-condition-contract.md), пара «вид, тип» действия
 * (docs/models/domain/aggregate/Strategy.md §Действия). Торгово-суждённые
 * диапазоны — отложены до activate (422):
 * docs/rules/strategy-validation.md §«Что проверяется на активации».
 * Per-field презенс и числовые границы держит Bean Validation на
 * api-моделях. Warmup-floor — упрощённый минимум шага 2; настоящий
 * derive — у реализаций индикаторов (шаг 3).
 */
@Component
public class StrategyDefinitionValidator {

    /**
     * Верхняя граница доли объявления, проценты: диапазон обеих долей — (0, 100].
     * Текст нарушения диапазон называет словами: точка с запятой — разделитель
     * склейки нарушений отказа, и внутри члена она делила бы его надвое.
     */
    private static final BigDecimal FRACTION_PERCENTS_MAX = BigDecimal.valueOf(100);

    /**
     * Операторы правил над перечнем — подтверждённого пробоя, утверждений о
     * структуре и о фазе: над перечнем есть только равенство и его
     * отрицание; прочие интерпретатор читает ложью, и правило с ними не
     * сработало бы никогда.
     */
    private static final Set<String> EQUALITY_OPERATORS = Set.of(
            StrategyConditionOperator.EQ.name(),
            StrategyConditionOperator.NE.name());

    /** Допустимые ruleType в контексте классификации фазы (сравнивающие + структурно-событийные). */
    private static final Set<String> PHASE_ALLOWED_RULE_TYPES = Set.of(
            StrategyConditionRuleType.INDICATOR_COMPARE.name(),
            StrategyConditionRuleType.PRICE_COMPARE.name(),
            StrategyConditionRuleType.CROSSOVER.name(),
            StrategyConditionRuleType.RANGE_BREAKOUT_CONFIRMED.name(),
            StrategyConditionRuleType.VOLUME_FILTER_PASSED.name(),
            StrategyConditionRuleType.MARKET_STRUCTURE_IS.name());

    /**
     * Источники рыночной цены, которых снапшот рыночных цен не несёт
     * (docs/models/mapping/MarketPriceData.md): тикер площадки отдаёт
     * последнюю цену и лучшие бид и аск.
     */
    private static final Set<String> UNAVAILABLE_PRICE_SOURCES = Set.of(
            StrategyPriceSource.MARK_PRICE.name(),
            StrategyPriceSource.INDEX_PRICE.name());

    /**
     * Типы условной заявки, образующие ЗАЩИТУ (docs/spec/strategy-reference.json
     * §{@code isProtectiveAction}). Тейк-профит защитой не является: он не
     * ограничивает убыток. {@code TRAILING_VALUE} здесь остаётся, хотя
     * создание его отвергает ({@code validateConditionTypeSupported}):
     * защитность — предикат исполнимой формы, и отказ типа не делает
     * действие незащитным — иначе поверх единственного отказа шаг получал бы
     * ещё и неполное покрытие.
     */
    private static final Set<String> PROTECTIVE_CONDITION_TYPES = Set.of(
            AlgoOrder.ConditionType.STOP_LOSS.name(),
            AlgoOrder.ConditionType.PARTIAL_STOP_LOSS.name(),
            AlgoOrder.ConditionType.OCO_FULL.name(),
            AlgoOrder.ConditionType.TRAILING_PERCENTS.name(),
            AlgoOrder.ConditionType.TRAILING_VALUE.name());

    /**
     * Типы, допустимые у каждого вида действия шага транша
     * (docs/models/domain/aggregate/Strategy.md §Действия). Замещение
     * оставлено заявке и условной заявке, хотя исполнителя замещения в ядре
     * нет: это названное ограничение со своим возвратом, а не пара без
     * исполнителя, объявленная по ошибке.
     */
    private static final Map<Class<? extends StrategyActionApiModel>, Set<StrategyActionType>> TYPES_BY_KIND =
            Map.of(
                    StrategyOrderActionApiModel.class,
                    EnumSet.of(StrategyActionType.CREATE_ACTION, StrategyActionType.REPLACE_ACTION),
                    StrategyAlgoOrderActionApiModel.class,
                    EnumSet.of(StrategyActionType.CREATE_ACTION, StrategyActionType.REPLACE_ACTION,
                            StrategyActionType.CANCEL_ACTION),
                    StrategyPositionActionApiModel.class,
                    EnumSet.of(StrategyActionType.EXIT_ACTION));

    /**
     * Статусы транша, под которыми ядро ОТБИРАЕТ его шаги: у каждого свой
     * обработчик (docs/components/Tranche*Handler.md, разделы «Шаги
     * статуса»). Под терминальным {@code CLOSED} шаги не отбирает никто.
     * Перечень объявлен положительно: статус, заведённый позже, отвергается,
     * пока отбор под ним не назван.
     */
    private static final Set<String> TRANCHE_SELECTED_STATUSES = Set.of(
            DealTranche.Status.PRECHECK.name(),
            DealTranche.Status.ENTRY_SUBMITTED.name(),
            DealTranche.Status.ENTRY_FINALIZED.name(),
            DealTranche.Status.PROTECTION_SWITCHED.name(),
            DealTranche.Status.MANAGING.name(),
            DealTranche.Status.EXIT_PENDING.name());

    /**
     * Статус сделки, под которым отбираются шаги уровня сделки: только
     * активная сделка (docs/components/DealActiveHandler.md). Координированный
     * выход их не отбирает — такой шаг работает ребром из активной сделки, и
     * двигать ему там нечего (docs/components/DealExitPendingHandler.md);
     * терминальные статусы не отбирает никто.
     */
    private static final Set<String> DEAL_SELECTED_STATUSES = Set.of(Deal.Status.ACTIVE.name());

    /** Допустимые sourceType операндов в контексте классификации фазы (без MARKET_PHASE и runtime-сделки). */
    private static final Set<String> PHASE_ALLOWED_SOURCE_TYPES = Set.of(
            StrategyConditionSourceType.INDICATOR.name(),
            StrategyConditionSourceType.MARKET_STRUCTURE.name(),
            StrategyConditionSourceType.PRICE.name(),
            StrategyConditionSourceType.CONSTANT.name(),
            StrategyConditionSourceType.TIME.name());

    public void validateCreate(CreateStrategyApiRequest request, TenantRiskAppetite appetite) {
        List<String> emptyMembers = new ArrayList<>();
        collectEmptyMembers(request, emptyMembers);
        rejectIfAny(emptyMembers);
        List<String> violations = new ArrayList<>();
        Map<String, IndicatorValue.Type> indicatorTypes = indicatorTypes(request.getIndicatorSettings());
        Set<String> structureKeys = structureSettingKeys(request.getMarketStructureSettings());
        validateSettingsLists(request.getIndicatorSettings(), request.getMarketStructureSettings(),
                "strategy", indicatorTypes, violations);
        validateMarketPhaseSetting(request.getMarketPhaseSetting(), indicatorTypes, structureKeys, violations);
        validateDetails(request.getDetails(), appetite, indicatorTypes, structureKeys, violations);
        rejectIfAny(violations);
    }

    private void rejectIfAny(List<String> violations) {
        if (isNotEmpty(violations)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join("; ", violations));
        }
    }

    /**
     * Член коллекции определения не пуст — у каждого перечня, который обход
     * дерева читает поэлементно, и у значения карты шагов по статусу (дом
     * правила — docs/rules/strategy-validation.md §«Что проверяется на
     * создании»).
     *
     * <p>Пустое место узлом не является: у него нет ни одного поля, которое
     * проверка могла бы прочесть, и дошедшее до проверок узла оно роняло
     * обход разыменованием — автор получал отказ сервера вместо отказа
     * создания. Bean Validation пустой член не отвергает: каскад по
     * {@code @Valid} его пропускает, а аннотация на элементе отвечала бы без
     * именованного кода (довод {@code validateFractionPositive}).
     *
     * <p><b>Отказ с пустым членом прочих нарушений не несёт, и это названная
     * цена.</b> Обход начинается, когда все члены на месте: сделать каждую
     * проверку терпимой к пустоте значило бы охранять ею два десятка мест,
     * и пропущенная охрана возвращала бы тот же отказ сервера. Пустые члены
     * при этом копятся между собой — автор получает их все одним ответом.
     */
    private void collectEmptyMembers(CreateStrategyApiRequest request, List<String> violations) {
        rejectEmptyMembers(request.getIndicatorSettings(), "strategy.indicatorSettings", violations);
        rejectEmptyMembers(request.getMarketStructureSettings(), "strategy.marketStructureSettings", violations);
        if (nonNull(request.getMarketPhaseSetting())) {
            List<StrategyMarketPhaseRuleApiModel> phaseRules = request.getMarketPhaseSetting().getPhaseRules();
            rejectEmptyMembers(phaseRules, "marketPhaseSetting.phaseRules", violations);
            for (int index = 0; index < emptyIfNull(phaseRules).size(); index++) {
                StrategyMarketPhaseRuleApiModel rule = phaseRules.get(index);
                if (isNull(rule)) {
                    continue;
                }
                rejectEmptyRules(rule.getCondition(), "marketPhaseSetting.phaseRules[" + index + "].condition",
                        violations);
            }
        }
        List<StrategyDetailApiModel> details = request.getDetails();
        rejectEmptyMembers(details, "details", violations);
        for (int index = 0; index < emptyIfNull(details).size(); index++) {
            StrategyDetailApiModel detail = details.get(index);
            if (isNull(detail)) {
                continue;
            }
            collectEmptyDetailMembers(detail, "details[" + index + "]", violations);
        }
    }

    /** Члены детали: объявления траншей и шаги обоих уровней. */
    private void collectEmptyDetailMembers(StrategyDetailApiModel detail, String path, List<String> violations) {
        List<StrategyTrancheApiModel> tranches = detail.getTranches();
        rejectEmptyMembers(tranches, path + ".tranches", violations);
        for (int index = 0; index < emptyIfNull(tranches).size(); index++) {
            StrategyTrancheApiModel tranche = tranches.get(index);
            if (isNull(tranche)) {
                continue;
            }
            collectEmptyStepMembers(tranche.getStepsByStatus(), path + ".tranches[" + index + "].stepsByStatus",
                    violations);
        }
        collectEmptyStepMembers(detail.getStepsByStatus(), path + ".stepsByStatus", violations);
    }

    /** Значение карты шагов, шаг, правило его условия и действие его пакета. */
    private void collectEmptyStepMembers(Map<String, List<StrategyStepApiModel>> stepsByStatus, String path,
                                         List<String> violations) {
        for (Map.Entry<String, List<StrategyStepApiModel>> entry : emptyIfNull(stepsByStatus).entrySet()) {
            String statusPath = path + "[" + entry.getKey() + "]";
            List<StrategyStepApiModel> steps = entry.getValue();
            if (isNull(steps)) {
                emptyMember(statusPath, violations);
                continue;
            }
            rejectEmptyMembers(steps, statusPath, violations);
            for (int index = 0; index < steps.size(); index++) {
                StrategyStepApiModel step = steps.get(index);
                if (isNull(step)) {
                    continue;
                }
                String stepPath = statusPath + "[" + index + "]";
                rejectEmptyRules(step.getCondition(), stepPath + ".condition", violations);
                rejectEmptyMembers(step.getActions(), stepPath + ".actions", violations);
            }
        }
    }

    /** Правила условия; опущенное условие — предмет {@code validateConditionNotEmpty}. */
    private void rejectEmptyRules(StrategyConditionApiModel condition, String path, List<String> violations) {
        if (isNull(condition)) {
            return;
        }
        rejectEmptyMembers(condition.getRules(), path + ".rules", violations);
    }

    private void rejectEmptyMembers(List<?> members, String path, List<String> violations) {
        if (isNull(members)) {
            return;
        }
        for (int index = 0; index < members.size(); index++) {
            if (isNull(members.get(index))) {
                emptyMember(path + "[" + index + "]", violations);
            }
        }
    }

    private void emptyMember(String path, List<String> violations) {
        violations.add(path + " STRATEGY_COLLECTION_MEMBER_EMPTY: член коллекции определения пуст — "
                + "пустое место узлом не является");
    }

    /**
     * Пять неравенств создания — заново, на ТЕКУЩИХ числах тенанта.
     *
     * <p>Вход активации: числа риск-аппетита приходят снаружи и после
     * создания меняются, поэтому одноразовая проверка стареет
     * (docs/rules/strategy-validation.md §«Что проверяется на
     * активации»). Прочие проверки создания повторять незачем — они стоя́т
     * на дереве, а дерево неизменяемо.
     *
     * <p>Перепроверяются <b>все пять</b>, а не только зависящие от чисел:
     * разделение «эти зависят, эти нет» пришлось бы поддерживать при
     * каждой правке неравенств, а цена повтора — обход уже прочитанного
     * дерева в памяти.
     */
    public void validateRiskInequalities(List<StrategyDetailApiModel> details, TenantRiskAppetite appetite) {
        List<String> violations = new ArrayList<>();
        for (int index = 0; index < emptyIfNull(details).size(); index++) {
            StrategyDetailApiModel detail = details.get(index);
            String path = "details[" + index + "]";
            validateRiskNumbers(detail, path, appetite, violations);
            validateOverlapRisk(detail, path, violations);
            validateNotionalHeadroom(detail, path, appetite, violations);
            validateProtectionCoverage(detail, path, violations);
        }
        rejectIfAny(violations);
    }

    /** Валидация целевого статуса PUT: известный enum, кроме CREATED (он системный). */
    public Strategy.Status validateStatusUpdate(UpdateStrategyStatusApiRequest request) {
        String status = request.getStatus();
        if (isFalse(EnumUtils.isValidEnum(Strategy.Status.class, status))
                || Objects.equals(status, Strategy.Status.CREATED.name())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Target status must be one of ACTIVE/INACTIVE/DELETED: " + status);
        }
        return Strategy.Status.valueOf(status);
    }

    private void validateMarketPhaseSetting(StrategyMarketPhaseSettingApiModel setting,
                                            Map<String, IndicatorValue.Type> indicatorTypes,
                                            Set<String> structureKeys, List<String> violations) {
        if (isNull(setting)) {
            return;
        }
        validatePhaseRules(setting.getPhaseRules(), indicatorTypes, structureKeys, violations);
    }

    /**
     * Клаузы классификации фазы: тип-фазы — известный enum; condition —
     * только в контекстном whitelist фазы (ruleType сравнивающие/
     * структурно-событийные; операнды без MARKET_PHASE и runtime-сделки).
     */
    private void validatePhaseRules(List<StrategyMarketPhaseRuleApiModel> phaseRules,
                                    Map<String, IndicatorValue.Type> indicatorTypes,
                                    Set<String> structureKeys, List<String> violations) {
        if (isNull(phaseRules)) {
            return;
        }
        for (int index = 0; index < phaseRules.size(); index++) {
            StrategyMarketPhaseRuleApiModel rule = phaseRules.get(index);
            String path = "marketPhaseSetting.phaseRules[" + index + "]";
            validateEnum(MarketPhase.Type.class, rule.getType(), path + ".type", violations);
            validateConditionNotEmpty(rule.getCondition(), path + ".condition", violations);
            if (isNull(rule.getCondition()) || isNull(rule.getCondition().getRules())) {
                continue;
            }
            List<StrategyConditionRuleApiModel> rules = rule.getCondition().getRules();
            for (int ruleIndex = 0; ruleIndex < rules.size(); ruleIndex++) {
                validatePhaseConditionRule(rules.get(ruleIndex),
                        path + ".condition.rules[" + ruleIndex + "]", indicatorTypes, structureKeys, violations);
            }
        }
    }

    private void validatePhaseConditionRule(StrategyConditionRuleApiModel rule, String path,
                                            Map<String, IndicatorValue.Type> indicatorTypes,
                                            Set<String> structureKeys, List<String> violations) {
        validateOperatorAndTimeframe(rule, path, violations);
        if (isFalse(EnumUtils.isValidEnum(StrategyConditionRuleType.class, rule.getRuleType()))) {
            validateEnum(StrategyConditionRuleType.class, rule.getRuleType(), path + ".ruleType", violations);
            return;
        }
        if (isFalse(PHASE_ALLOWED_RULE_TYPES.contains(rule.getRuleType()))) {
            violations.add(path + ".ruleType " + rule.getRuleType()
                    + " is not allowed in market phase classification context");
        }
        validatePhaseOperand(rule.getLeftOperand(), path + ".leftOperand", indicatorTypes, structureKeys, violations);
        validatePhaseOperand(rule.getRightOperand(), path + ".rightOperand", indicatorTypes, structureKeys, violations);
        validateRuleContract(rule, path, violations);
    }

    private void validatePhaseOperand(StrategyConditionOperandApiModel operand, String path,
                                      Map<String, IndicatorValue.Type> indicatorTypes,
                                      Set<String> structureKeys, List<String> violations) {
        if (isNull(operand)) {
            return;
        }
        if (EnumUtils.isValidEnum(StrategyConditionSourceType.class, operand.getSourceType())
                && isFalse(PHASE_ALLOWED_SOURCE_TYPES.contains(operand.getSourceType()))) {
            violations.add(path + ".sourceType " + operand.getSourceType()
                    + " is not allowed in market phase classification context");
        }
        validateOperand(operand, path, indicatorTypes, structureKeys, violations);
    }

    private void validateDetails(List<StrategyDetailApiModel> details, TenantRiskAppetite appetite,
                                 Map<String, IndicatorValue.Type> indicatorTypes,
                                 Set<String> structureKeys, List<String> violations) {
        if (isNull(details)) {
            return;
        }
        validatePhaseCoverage(details, violations);
        for (int index = 0; index < details.size(); index++) {
            validateDetail(details.get(index), "details[" + index + "]", appetite, indicatorTypes,
                    structureKeys, violations);
        }
    }

    /** Ровно одна деталь на один MarketPhase.Type — и каждая фаза покрыта. */
    private void validatePhaseCoverage(List<StrategyDetailApiModel> details, List<String> violations) {
        Set<String> seen = new HashSet<>();
        details.forEach(detail -> {
            if (nonNull(detail.getMarketPhaseType()) && isFalse(seen.add(detail.getMarketPhaseType()))) {
                violations.add("duplicate detail for marketPhaseType " + detail.getMarketPhaseType());
            }
        });
        for (MarketPhase.Type phase : MarketPhase.Type.values()) {
            if (isFalse(seen.contains(phase.name()))) {
                violations.add("missing detail for marketPhaseType " + phase.name()
                        + " (non-trading phase is declared explicitly with NO_TRADE)");
            }
        }
    }

    private void validateDetail(StrategyDetailApiModel detail, String path, TenantRiskAppetite appetite,
                                Map<String, IndicatorValue.Type> indicatorTypes,
                                Set<String> structureKeys, List<String> violations) {
        validateEnum(MarketPhase.Type.class, detail.getMarketPhaseType(), path + ".marketPhaseType", violations);
        validateEnum(PhaseEntryPolicy.class, detail.getPhaseEntryPolicy(), path + ".phaseEntryPolicy", violations);
        validatePolicyMatrix(detail, path, violations);
        validateRiskNumbers(detail, path, appetite, violations);
        validateTranches(detail, path, violations);
        validateOverlapRisk(detail, path, violations);
        validateNotionalHeadroom(detail, path, appetite, violations);
        validateProtectionCoverage(detail, path, violations);
        validateSteps(detail, path, indicatorTypes, structureKeys, violations);
    }

    /**
     * Второе статическое неравенство создания: веер детали умещается в её
     * же потолок одновременного риска —
     * {@code N_overlap × riskPerActionPercent ≤
     * strategySimultaneousRiskPerDealPercent}
     * (docs/rules/strategy-validation.md §«Исключения: неравенства,
     * проверяемые на создании», исполнимая форма —
     * docs/spec/strategy-reference.json §{@code overlapRiskSatisfiable}).
     *
     * <p>Реджект собственный ({@code ..._UNSATISFIABLE}), а не общий с
     * первым и третьим: адресует отказ тот конъюнкт, который ложен.
     * Проверка консервативна — мажорирует сумму произведением, и это
     * названо домом.
     *
     * <p>Незаданные операнды сюда не доезжают решением: их отсутствие уже
     * отвергнуто своими проверками (риск-числа, {@code levelCount}), и
     * подстановка умолчания здесь мажорировала бы неравенство в
     * разрешающую сторону.
     */
    private void validateOverlapRisk(StrategyDetailApiModel detail, String path, List<String> violations) {
        if (isFalse(tradableDetail(detail))) {
            return;
        }
        Integer overlapCount = declaredOverlapCount(detail);
        BigDecimal perAction = detail.getRiskPerActionPercent();
        BigDecimal simultaneous = detail.getStrategySimultaneousRiskPerDealPercent();
        if (isNull(overlapCount) || isNull(perAction) || isNull(simultaneous)) {
            return;
        }
        BigDecimal fan = perAction.multiply(BigDecimal.valueOf(overlapCount));
        if (fan.compareTo(simultaneous) > 0) {
            violations.add(path + " STRATEGY_SIMULTANEOUS_RISK_UNSATISFIABLE: веер детали ("
                    + overlapCount + " × " + perAction + ") выше её максимума одновременного риска "
                    + simultaneous);
        }
    }

    /**
     * Пятое статическое неравенство создания: объявленный нотинал детали
     * укладывается в катастрофический потолок С ЗАПАСОМ
     * (docs/rules/risk-policy.md §«Нотинал укладывается в потолок с
     * запасом, а не в границу», исполнимая форма —
     * docs/spec/strategy-reference.json §{@code notionalHeadroomSatisfied}).
     *
     * <p>Потолок берётся в долях базы: на создании стратегии база ещё не
     * наблюдена, а отношение уже вычислимо. Запас — константа правила, не
     * число конфигурации.
     */
    private void validateNotionalHeadroom(StrategyDetailApiModel detail, String path,
                                          TenantRiskAppetite appetite, List<String> violations) {
        if (isFalse(tradableDetail(detail))) {
            return;
        }
        BigDecimal multiplier = detail.getStrategyCatastrophicRiskPerDealMultiplier();
        BigDecimal globalSimultaneous = appetite.globalSimultaneousRiskPerDealPercent();
        BigDecimal declaredShare = declaredNotionalShare(detail);
        if (isNull(multiplier) || isNull(globalSimultaneous) || isNull(declaredShare)) {
            return;
        }
        BigDecimal ceilingShare = globalSimultaneous
                .divide(Constants.Risk.FULL_COVERAGE_PERCENTS, DomainMath.CONTEXT)
                .multiply(multiplier);
        BigDecimal allowed = ceilingShare.multiply(BigDecimal.ONE.subtract(Constants.Risk.NOTIONAL_HEADROOM_SHARE));
        if (declaredShare.compareTo(allowed) > 0) {
            violations.add(path + " STRATEGY_NOTIONAL_HEADROOM_INSUFFICIENT: объявленный нотинал детали ("
                    + declaredShare + " базы) не оставляет запаса под катастрофическим потолком (допустимо "
                    + allowed + ")");
        }
    }

    /**
     * Четвёртое статическое неравенство создания: покрытие защитой после
     * шага не опускается ниже ста процентов
     * (docs/rules/live-risk-protection.md, исполнимые формы —
     * docs/spec/strategy-reference.json §{@code protectionCoverageComplete}
     * и §{@code partialStepsReturningLess}).
     *
     * <p>Два класса нарушения, реджект один — покрытие после шага ниже
     * ста: шаг, объявивший ПОЛНЫЙ набор, обязан дать ровно сто (сумма
     * выше ста — не усиленная защита, а невыполнимое объявление); шаг,
     * забирающий ЧАСТЬ, обязан вернуть не меньше забранного.
     */
    private void validateProtectionCoverage(StrategyDetailApiModel detail, String path, List<String> violations) {
        if (isFalse(tradableDetail(detail))) {
            return;
        }
        Map<String, StrategyActionApiModel> actionsByKey = actionsByKey(detail);
        for (StrategyTrancheApiModel tranche : emptyIfNull(detail.getTranches())) {
            for (Map.Entry<String, List<StrategyStepApiModel>> entry
                    : emptyIfNull(tranche.getStepsByStatus()).entrySet()) {
                validateStepCoverage(entry.getValue(), path + ".tranches[" + tranche.getKey() + "]."
                        + entry.getKey(), actionsByKey, violations);
            }
        }
    }

    private void validateStepCoverage(List<StrategyStepApiModel> steps, String path,
                                      Map<String, StrategyActionApiModel> actionsByKey, List<String> violations) {
        List<StrategyStepApiModel> declaredSteps = List.copyOf(emptyIfNull(steps));
        for (int index = 0; index < declaredSteps.size(); index++) {
            StrategyStepApiModel step = declaredSteps.get(index);
            String stepPath = path + "[" + index + "]";
            BigDecimal declared = protectiveCoverage(step);
            BigDecimal replaced = replacedCoverage(step, actionsByKey);
            if (isTrue(declaresFullProtectionSet(step, replaced))) {
                if (declared.compareTo(Constants.Risk.FULL_COVERAGE_PERCENTS) != 0) {
                    violations.add(stepPath + " STRATEGY_PROTECTION_COVERAGE_INCOMPLETE: "
                            + "шаг полного набора защиты объявляет " + declared + " % вместо ста");
                }
                continue;
            }
            if (declared.compareTo(replaced) < 0) {
                violations.add(stepPath + " STRATEGY_PROTECTION_COVERAGE_INCOMPLETE: "
                        + "шаг забирает " + replaced + " % действующего покрытия и возвращает " + declared + " %");
            }
        }
    }

    /**
     * Полный набор: первичная постановка защиты либо шаг, адресующий ВСЁ
     * действующее покрытие — снятием, замещением или их смесью.
     */
    private Boolean declaresFullProtectionSet(StrategyStepApiModel step, BigDecimal replacedCoverage) {
        return Objects.equals(step.getStepType(), StrategyStepType.MAIN_PROTECTION.name())
                || replacedCoverage.compareTo(Constants.Risk.FULL_COVERAGE_PERCENTS) >= 0;
    }

    /** Сумма долей защитных CREATE и REPLACE шага. */
    private BigDecimal protectiveCoverage(StrategyStepApiModel step) {
        BigDecimal sum = BigDecimal.ZERO;
        for (StrategyActionApiModel action : emptyIfNull(step.getActions())) {
            if (isFalse(protectiveAction(action)) || isFalse(coverageSettingAction(action))) {
                continue;
            }
            BigDecimal fraction = ((StrategyAlgoOrderActionApiModel) action).getCloseFractionPercents();
            sum = sum.add(nonNull(fraction) ? fraction : BigDecimal.ZERO);
        }
        return sum;
    }

    /**
     * Сколько действующего покрытия шаг адресует своими REPLACE и CANCEL.
     * Отбор — по ФОРМЕ действия, а доля читается у ЦЕЛИ: собственный
     * {@code conditionType} снимающего действия есть денормализованная
     * копия типа цели, и решение на копии стоять не может. Цель не
     * разрешена — действие считается адресующим ВСЁ покрытие:
     * неизвестность не выводит шаг из-под проверки.
     */
    private BigDecimal replacedCoverage(StrategyStepApiModel step,
                                        Map<String, StrategyActionApiModel> actionsByKey) {
        BigDecimal sum = BigDecimal.ZERO;
        for (StrategyActionApiModel action : emptyIfNull(step.getActions())) {
            if (isFalse(action instanceof StrategyAlgoOrderActionApiModel)
                    || isFalse(coverageTakingAction(action))) {
                continue;
            }
            sum = sum.add(targetCoverage(action.getTargetActionKey(), actionsByKey));
        }
        return sum;
    }

    /** Доля покрытия цели: цель не защита — ноль; цель не разрешена — всё. */
    private BigDecimal targetCoverage(String targetActionKey, Map<String, StrategyActionApiModel> actionsByKey) {
        StrategyActionApiModel target = isNull(targetActionKey) ? null : actionsByKey.get(targetActionKey);
        if (isNull(target)) {
            return Constants.Risk.FULL_COVERAGE_PERCENTS;
        }
        if (isFalse(protectiveAction(target))) {
            return BigDecimal.ZERO;
        }
        BigDecimal fraction = ((StrategyAlgoOrderActionApiModel) target).getCloseFractionPercents();
        return nonNull(fraction) ? fraction : Constants.Risk.FULL_COVERAGE_PERCENTS;
    }

    /** Действие ставит покрытие: создание либо замещение. */
    private Boolean coverageSettingAction(StrategyActionApiModel action) {
        return Objects.equals(action.getActionType(), StrategyActionType.CREATE_ACTION.name())
                || Objects.equals(action.getActionType(), StrategyActionType.REPLACE_ACTION.name());
    }

    /** Действие забирает покрытие: замещение либо снятие. */
    private Boolean coverageTakingAction(StrategyActionApiModel action) {
        return Objects.equals(action.getActionType(), StrategyActionType.REPLACE_ACTION.name())
                || Objects.equals(action.getActionType(), StrategyActionType.CANCEL_ACTION.name());
    }

    /** Защитное действие: условная заявка типа из множества защит. */
    private Boolean protectiveAction(StrategyActionApiModel action) {
        return action instanceof StrategyAlgoOrderActionApiModel algo
                && PROTECTIVE_CONDITION_TYPES.contains(algo.getConditionType());
    }

    /** Действие занимает нотинал: входной ордер обеих форм. */
    private Boolean entryOrderAction(StrategyOrderActionApiModel action) {
        return Objects.equals(action.getOrderType(), Order.Type.ENTRY.name())
                || Objects.equals(action.getOrderType(), Order.Type.ENTRY_ATTACHED_STOP_LOSS.name());
    }

    /** Все действия детали по ключу — вход резолва цели замещения и снятия. */
    private Map<String, StrategyActionApiModel> actionsByKey(StrategyDetailApiModel detail) {
        Map<String, StrategyActionApiModel> byKey = new HashMap<>();
        for (StrategyTrancheApiModel tranche : emptyIfNull(detail.getTranches())) {
            emptyIfNull(tranche.getStepsByStatus()).values().forEach(steps -> collectActions(steps, byKey));
        }
        emptyIfNull(detail.getStepsByStatus()).values().forEach(steps -> collectActions(steps, byKey));
        return byKey;
    }

    private void collectActions(List<StrategyStepApiModel> steps, Map<String, StrategyActionApiModel> byKey) {
        for (StrategyStepApiModel step : emptyIfNull(steps)) {
            for (StrategyActionApiModel action : emptyIfNull(step.getActions())) {
                if (nonNull(action.getKey())) {
                    byKey.put(action.getKey(), action);
                }
            }
        }
    }

    /**
     * {@code N_overlap} детали — сумма {@code levelCount} по её
     * объявлениям; {@code null}, если хоть одно объявление числа не
     * назвало (умолчания у него нет, и подстановка мажорировала бы
     * неравенство в разрешающую сторону).
     */
    private Integer declaredOverlapCount(StrategyDetailApiModel detail) {
        int sum = 0;
        for (StrategyTrancheApiModel tranche : emptyIfNull(detail.getTranches())) {
            if (isNull(tranche.getLevelCount())) {
                return null;
            }
            sum += tranche.getLevelCount();
        }
        return sum;
    }

    /**
     * Нотинал детали в долях базы: сумма по одновременно живым траншам —
     * доля аллокации входного действия транша, умноженная на его
     * {@code levelCount}. {@code null} — операнд не объявлен (его
     * отсутствие отвергается своей проверкой, подстановка нуля читала бы
     * «уровень не занял ничего»).
     */
    private BigDecimal declaredNotionalShare(StrategyDetailApiModel detail) {
        BigDecimal sum = BigDecimal.ZERO;
        for (StrategyTrancheApiModel tranche : emptyIfNull(detail.getTranches())) {
            BigDecimal trancheShare = trancheNotionalShare(tranche);
            if (isNull(trancheShare) || isNull(tranche.getLevelCount())) {
                return null;
            }
            sum = sum.add(trancheShare.multiply(BigDecimal.valueOf(tranche.getLevelCount())));
        }
        return sum;
    }

    private BigDecimal trancheNotionalShare(StrategyTrancheApiModel tranche) {
        BigDecimal sum = BigDecimal.ZERO;
        for (List<StrategyStepApiModel> steps : emptyIfNull(tranche.getStepsByStatus()).values()) {
            for (StrategyStepApiModel step : emptyIfNull(steps)) {
                for (StrategyActionApiModel action : emptyIfNull(step.getActions())) {
                    if (isFalse(action instanceof StrategyOrderActionApiModel order && entryOrderAction(order))) {
                        continue;
                    }
                    BigDecimal allocation = ((StrategyOrderActionApiModel) action).getAllocationPercents();
                    if (isNull(allocation)) {
                        return null;
                    }
                    sum = sum.add(allocation.divide(Constants.Risk.FULL_COVERAGE_PERCENTS,
                            DomainMath.CONTEXT));
                }
            }
        }
        return sum;
    }

    /**
     * Объявления траншей торгуемой детали
     * (docs/models/domain/aggregate/Strategy.md §StrategyTranche,
     * docs/rules/strategy-validation.md):
     *
     * <ul>
     *   <li><b>хотя бы одно</b> — деталь без траншей не имеет входа и
     *       торговать не может;</li>
     *   <li><b>ровно одно входное</b> — вход объявляется в одном месте:
     *       два объявления входа в одной фазе дали бы два независимых
     *       решения на одну сделку;</li>
     *   <li><b>ключ уникален</b> в пределах детали — по нему объявление
     *       адресуется;</li>
     *   <li><b>согласованность {@code levelCount} и {@code levelStep}</b> —
     *       смещение уровня осмысленно только у сетки и обязательно у неё.
     *       Пустой {@code levelCount} умолчания не имеет: единица
     *       мажорировала бы его в разрешающую сторону неравенства
     *       статического запаса (docs/rules/risk-policy.md).</li>
     * </ul>
     */
    private void validateTranches(StrategyDetailApiModel detail, String path, List<String> violations) {
        List<StrategyTrancheApiModel> tranches = detail.getTranches();
        if (isFalse(tradableDetail(detail))) {
            if (isNotEmpty(tranches)) {
                violations.add(path + ".tranches STRATEGY_TRANCHE_ON_NON_TRADING_DETAIL: "
                        + "неторгуемая деталь объявлений входа не несёт");
            }
            return;
        }
        if (isEmpty(tranches)) {
            violations.add(path + ".tranches STRATEGY_TRANCHE_NOT_DECLARED: "
                    + "торгуемая деталь обязана объявить хотя бы один транш");
            return;
        }
        Set<String> keys = new HashSet<>();
        int entryDeclarations = 0;
        for (int index = 0; index < tranches.size(); index++) {
            StrategyTrancheApiModel tranche = tranches.get(index);
            String tranchePath = path + ".tranches[" + index + "]";
            if (nonNull(tranche.getKey()) && isFalse(keys.add(tranche.getKey()))) {
                violations.add(tranchePath + ": duplicate tranche key " + tranche.getKey());
            }
            validateTrancheGrid(tranche, tranchePath, violations);
            validateTrancheReopen(tranche, tranchePath, violations);
            int entrySteps = entryStepCount(tranche);
            if (entrySteps > 1) {
                violations.add(tranchePath + " STRATEGY_TRANCHE_ENTRY_NOT_UNIQUE: "
                        + "у транша ровно одно входное объявление, объявлено " + entrySteps);
            }
            entryDeclarations += entrySteps > 0 ? 1 : 0;
        }
        if (entryDeclarations == 0) {
            violations.add(path + ".tranches STRATEGY_ENTRY_DECLARATION_MISSING: "
                    + "у торгуемой детали ни одно объявление не несёт входа — торговать нечем");
        }
    }

    /**
     * Признак переоткрытия объявляется ЯВНО: умолчания у него нет.
     * Пустое место читалось бы как «не допускает», то есть молчаливо
     * решало бы за автора стратегии вопрос, который он не поставил.
     */
    private void validateTrancheReopen(StrategyTrancheApiModel tranche, String path, List<String> violations) {
        if (isNull(tranche.getPositionReopenAllowed())) {
            violations.add(path + ".positionReopenAllowed STRATEGY_TRANCHE_REOPEN_NOT_DECLARED: "
                    + "признак переоткрытия объявляется явно, умолчания нет");
        }
    }

    /** Согласованность шаблона: смещение уровня — только у сетки и обязательно у неё. */
    private void validateTrancheGrid(StrategyTrancheApiModel tranche, String path, List<String> violations) {
        Integer levelCount = tranche.getLevelCount();
        if (isNull(levelCount)) {
            violations.add(path + ".levelCount STRATEGY_TRANCHE_LEVEL_COUNT_NOT_DECLARED: "
                    + "умолчания нет — единица мажорировала бы число в разрешающую сторону");
            return;
        }
        boolean isGrid = levelCount > 1;
        if (isGrid && isNull(tranche.getLevelStep())) {
            violations.add(path + ".levelStep STRATEGY_TRANCHE_LEVEL_STEP_MISSING: "
                    + "у шаблона с levelCount > 1 смещение уровня обязательно");
        }
        if (isFalse(isGrid) && nonNull(tranche.getLevelStep())) {
            violations.add(path + ".levelStep STRATEGY_TRANCHE_LEVEL_STEP_UNEXPECTED: "
                    + "у нешаблонного объявления смещать нечего");
        }
    }

    /** Сколько входных шагов несёт объявление: PRECHECK-шаги типа ENTRY либо GRID_ENTRY. */
    private int entryStepCount(StrategyTrancheApiModel tranche) {
        Map<String, List<StrategyStepApiModel>> stepsByStatus = tranche.getStepsByStatus();
        if (isNull(stepsByStatus)) {
            return 0;
        }
        List<StrategyStepApiModel> precheck = stepsByStatus.get(DealTranche.Status.PRECHECK.name());
        if (isEmpty(precheck)) {
            return 0;
        }
        return (int) precheck.stream()
                .filter(step -> StrategyStepType.ENTRY.name().equals(step.getStepType())
                        || StrategyStepType.GRID_ENTRY.name().equals(step.getStepType()))
                .count();
    }

    /**
     * Риск-числа торгуемой детали: объявлены все четыре, и два из них
     * вложены в конфигурационный риск-аппетит
     * (docs/spec/strategy-reference.json, величины
     * {@code hasRequiredRiskFields}, {@code strategyRiskWithinGlobal},
     * {@code catastrophicMultiplierWithinGlobal}).
     *
     * <p><b>Незаданное конфигурационное число отвергает создание, а не
     * пропускает его:</b> сверять объявление автора не с чем, и
     * пропуск был бы разрешающей ошибкой ровно там, где стои́т охрана.
     * Реджекты у неравенств РАЗНЫЕ — адресует отказ тот конъюнкт,
     * который ложен (П3).
     */
    private void validateRiskNumbers(StrategyDetailApiModel detail, String path,
                                     TenantRiskAppetite appetite, List<String> violations) {
        if (isFalse(tradableDetail(detail))) {
            return;
        }
        requireDeclared(detail.getRiskPerActionPercent(), path + ".riskPerActionPercent", violations);
        requireDeclared(detail.getCumulativeRiskPerDealMultiplier(),
                path + ".cumulativeRiskPerDealMultiplier", violations);
        requireDeclared(detail.getStrategySimultaneousRiskPerDealPercent(),
                path + ".strategySimultaneousRiskPerDealPercent", violations);
        requireDeclared(detail.getStrategyCatastrophicRiskPerDealMultiplier(),
                path + ".strategyCatastrophicRiskPerDealMultiplier", violations);
        validateWithinGlobal(detail.getStrategySimultaneousRiskPerDealPercent(),
                appetite.globalSimultaneousRiskPerDealPercent(),
                path + ".strategySimultaneousRiskPerDealPercent",
                "STRATEGY_SIMULTANEOUS_RISK_ABOVE_GLOBAL",
                "максимум одновременного риска стратегии выше конфигурационного", violations);
        validateWithinGlobal(detail.getStrategyCatastrophicRiskPerDealMultiplier(),
                appetite.globalCatastrophicRiskPerDealMultiplier(),
                path + ".strategyCatastrophicRiskPerDealMultiplier",
                "STRATEGY_CATASTROPHIC_MULTIPLIER_ABOVE_GLOBAL",
                "множитель катастрофического потолка выше конфигурационного предела", violations);
    }

    /** Деталь торгуема: политика фазы объявлена и она не NO_TRADE. */
    private Boolean tradableDetail(StrategyDetailApiModel detail) {
        return EnumUtils.isValidEnum(PhaseEntryPolicy.class, detail.getPhaseEntryPolicy())
                && isFalse(PhaseEntryPolicy.NO_TRADE.equals(
                        PhaseEntryPolicy.valueOf(detail.getPhaseEntryPolicy())));
    }

    private void requireDeclared(BigDecimal value, String path, List<String> violations) {
        if (isNull(value)) {
            violations.add(path + " STRATEGY_RISK_NUMBER_NOT_DECLARED: у торгуемой детали риск-число "
                    + "обязательно, умолчания нет");
        }
    }

    /** Объявление автора не выше конфигурационного предела; предела нет — отказ. */
    private void validateWithinGlobal(BigDecimal declared, BigDecimal configured, String path,
                                      String code, String message, List<String> violations) {
        if (isNull(declared)) {
            return;
        }
        if (isNull(configured)) {
            violations.add(path + " STRATEGY_RISK_APPETITE_NOT_CONFIGURED: конфигурационное число риск-аппетита "
                    + "не задано — объявленное стратегией сверять не с чем");
            return;
        }
        if (declared.compareTo(configured) > 0) {
            violations.add(path + " " + code + ": " + message);
        }
    }

    /** Матрица допустимости политика×фаза — инвариант доменной модели (PhaseEntryPolicy.isAllowedFor). */
    private void validatePolicyMatrix(StrategyDetailApiModel detail, String path, List<String> violations) {
        if (isFalse(EnumUtils.isValidEnum(MarketPhase.Type.class, detail.getMarketPhaseType()))
                || isFalse(EnumUtils.isValidEnum(PhaseEntryPolicy.class, detail.getPhaseEntryPolicy()))) {
            return;
        }
        MarketPhase.Type phase = MarketPhase.Type.valueOf(detail.getMarketPhaseType());
        PhaseEntryPolicy policy = PhaseEntryPolicy.valueOf(detail.getPhaseEntryPolicy());
        if (isFalse(policy.isAllowedFor(phase))) {
            violations.add(path + ": phaseEntryPolicy " + policy + " is not allowed for phase " + phase);
        }
    }

    private void validateSettingsLists(List<StrategyIndicatorSettingApiModel> indicators,
                                       List<StrategyMarketStructureSettingApiModel> structures,
                                       String path, Map<String, IndicatorValue.Type> indicatorTypes,
                                       List<String> violations) {
        if (nonNull(indicators)) {
            Set<String> keys = new HashSet<>();
            for (int index = 0; index < indicators.size(); index++) {
                validateIndicatorSetting(indicators.get(index),
                        path + ".indicatorSettings[" + index + "]", keys, violations);
            }
        }
        if (nonNull(structures)) {
            Set<String> keys = new HashSet<>();
            for (int index = 0; index < structures.size(); index++) {
                validateStructureSetting(structures.get(index),
                        path + ".marketStructureSettings[" + index + "]", keys, indicatorTypes, violations);
            }
        }
    }

    private void validateIndicatorSetting(StrategyIndicatorSettingApiModel setting, String path,
                                          Set<String> keys, List<String> violations) {
        if (nonNull(setting.getKey()) && isFalse(keys.add(setting.getKey()))) {
            violations.add(path + ": duplicate indicator setting key " + setting.getKey());
        }
        validateEnum(IndicatorValue.Type.class, setting.getIndicatorType(), path + ".indicatorType", violations);
        validateEnum(Destiny.class, setting.getDestiny(), path + ".destiny", violations);
        validateExpirationDeclared(setting.getExpirationDuration(), path + ".expirationDuration", violations);
        validateIndicatorParams(setting.getParams(), path + ".params", violations);
    }

    private void validateIndicatorParams(IndicatorParamsApiModel params, String path, List<String> violations) {
        if (isNull(params)) {
            return;
        }
        validateEnum(TimeFrame.class, params.getTimeframe(), path + ".timeframe", violations);
        Integer floor = warmupFloor(params);
        if (nonNull(params.getWarmup()) && nonNull(floor) && params.getWarmup() < floor) {
            violations.add(path + ".warmup: override " + params.getWarmup()
                    + " is below derived minimum " + floor);
        }
    }

    /**
     * Минимум warmup шага 2 по типу params: оконные/рекурсивные — от
     * периода; MACD — slow + signal; стохастик — сумма окон; OBV — 1.
     */
    private Integer warmupFloor(IndicatorParamsApiModel params) {
        return switch (params) {
            case AtrParamsApiModel atr -> atr.getPeriod();
            case EmaParamsApiModel ema -> ema.getPeriod();
            case RsiParamsApiModel rsi -> rsi.getPeriod();
            case BollingerBandsParamsApiModel bb -> bb.getPeriod();
            case EfficiencyRatioParamsApiModel er -> er.getPeriod();
            case MacdParamsApiModel macd -> sumOrNull(macd.getSlowPeriod(), macd.getSignalPeriod());
            case StochasticParamsApiModel st ->
                    sumOrNull(sumOrNull(st.getkPeriod(), st.getdPeriod()), st.getSmoothPeriod());
            default -> 1;
        };
    }

    private Integer sumOrNull(Integer left, Integer right) {
        if (isNull(left) || isNull(right)) {
            return null;
        }
        return left + right;
    }

    private void validateStructureSetting(StrategyMarketStructureSettingApiModel setting, String path,
                                          Set<String> keys, Map<String, IndicatorValue.Type> indicatorTypes,
                                          List<String> violations) {
        if (nonNull(setting.getKey()) && isFalse(keys.add(setting.getKey()))) {
            violations.add(path + ": duplicate market structure setting key " + setting.getKey());
        }
        validateEnum(TimeFrame.class, setting.getTimeframe(), path + ".timeframe", violations);
        validateEnum(Destiny.class, setting.getDestiny(), path + ".destiny", violations);
        validateExpirationDeclared(setting.getExpirationDuration(), path + ".expirationDuration", violations);
        validateStructureLookback(setting.getParams(), path + ".params", violations);
        if (nonNull(setting.getEfficiencyRatioKey())) {
            validateIndicatorKeyOfType(setting.getEfficiencyRatioKey(), IndicatorValue.Type.EFFICIENCY_RATIO,
                    indicatorTypes, path + ".efficiencyRatioKey", violations);
        }
        if (nonNull(setting.getAtrKey())) {
            validateIndicatorKeyOfType(setting.getAtrKey(), IndicatorValue.Type.ATR,
                    indicatorTypes, path + ".atrKey", violations);
        }
    }

    /**
     * Окно расчёта структуры ОБЪЯВЛЕНО и положительно.
     *
     * <p>Пустое окно владелец рыночных данных читает пропуском
     * идентичности с записью в журнал: структура не считается ни разу, и
     * условие на ней не срабатывает никогда — молча. Нулевое и
     * отрицательное окно отказывает чтению ряда. Обязательность и диапазон
     * разведены кодами: отказ адресует тот конъюнкт, который ложен. Дом
     * правила — docs/rules/strategy-validation.md.
     *
     * <p>Блок параметров, опущенный целиком, здесь не отвергается: его
     * наличие держит аннотация поверхности, а предмет проверки — окно
     * объявленного блока. Диапазон держит валидатор, а не аннотация
     * api-модели, по доводу {@code validateFractionPositive}: именованный
     * код с аннотацией был бы недостижим.
     *
     * <p><b>Глубина поиска свингов — та же форма диапазона, своим кодом.</b>
     * Нулевая делает пивотом каждый бар, отрицательная отказывает чтению
     * ряда у владельца рыночных данных; прежде её отсекала аннотация
     * поверхности без именованного кода. Обязательность у неё не
     * проверяется: пустая глубина — один из шести порогов, без которых
     * резолвер отвечает неизвестной структурой, и сужать её одну значило бы
     * развести пороги одного класса.
     */
    private void validateStructureLookback(MarketStructureParamsApiModel params, String path,
                                           List<String> violations) {
        if (isNull(params)) {
            return;
        }
        Integer swingLookbackBars = params.getSwingLookbackBars();
        if (nonNull(swingLookbackBars) && swingLookbackBars <= 0) {
            violations.add(path + ".swingLookbackBars STRATEGY_STRUCTURE_SWING_LOOKBACK_NOT_POSITIVE: "
                    + "глубина поиска свингов больше нуля баров, получено " + swingLookbackBars);
        }
        Integer lookbackBars = params.getLookbackBars();
        if (isNull(lookbackBars)) {
            violations.add(path + ".lookbackBars STRATEGY_STRUCTURE_LOOKBACK_NOT_DECLARED: "
                    + "окно расчёта структуры объявляется явно, умолчания нет");
            return;
        }
        if (lookbackBars <= 0) {
            violations.add(path + ".lookbackBars STRATEGY_STRUCTURE_LOOKBACK_NOT_POSITIVE: "
                    + "окно расчёта структуры больше нуля баров, получено " + lookbackBars);
        }
    }

    /** Soft-ссылка на каталожный индикатор стратегии должна резолвиться и быть нужного типа. */
    private void validateIndicatorKeyOfType(String key, IndicatorValue.Type expectedType,
                                            Map<String, IndicatorValue.Type> indicatorTypes, String path,
                                            List<String> violations) {
        if (isFalse(indicatorTypes.containsKey(key))) {
            violations.add(path + " references unknown indicator setting key " + key
                    + " (must reference an indicator setting of the strategy)");
            return;
        }
        if (isFalse(Objects.equals(indicatorTypes.get(key), expectedType))) {
            violations.add(path + " must reference an indicator of type " + expectedType + ", but " + key
                    + " is " + indicatorTypes.get(key));
        }
    }

    /**
     * Шаги ОБОИХ уровней объявления: потраншевые — на объявлениях,
     * ключ группировки читается как статус транша; узкая агрегатная
     * поверхность — на детали, ключ читается как статус СДЕЛКИ.
     *
     * <p>Ключ действия уникален в пределах ВСЕЙ детали, поэтому набор
     * ключей собирается по обоим уровням: цель {@code targetActionKey}
     * резолвится через них же.
     *
     * <p>Уровни различаются двумя проверками: статусом, под которым шаги
     * отбираются, и экземпцией пустого пакета — она есть только у шага
     * выхода уровня сделки.
     */
    private void validateSteps(StrategyDetailApiModel detail, String path,
                               Map<String, IndicatorValue.Type> indicatorTypes, Set<String> structureKeys,
                               List<String> violations) {
        Set<String> actionKeys = collectActionKeys(detail, path, violations);
        emptyIfNull(detail.getTranches()).forEach(tranche -> {
            String tranchePath = path + ".tranches[" + tranche.getKey() + "]";
            if (isNull(tranche.getStepsByStatus())) {
                return;
            }
            tranche.getStepsByStatus().forEach((status, steps) -> {
                validateEnum(DealTranche.Status.class, status, tranchePath + ".stepsByStatus key", violations);
                validateStatusSelectsSteps(DealTranche.Status.class, status, TRANCHE_SELECTED_STATUSES, steps,
                        tranchePath, violations);
                for (int index = 0; index < steps.size(); index++) {
                    String stepPath = tranchePath + ".stepsByStatus[" + status + "][" + index + "]";
                    validateTrancheActionPairs(steps.get(index), stepPath, violations);
                    validateStepPackageNotEmpty(steps.get(index), Boolean.FALSE, stepPath, violations);
                    validateStep(steps.get(index), stepPath, indicatorTypes, structureKeys, actionKeys, violations);
                }
            });
        });
        if (isNull(detail.getStepsByStatus())) {
            return;
        }
        detail.getStepsByStatus().forEach((status, steps) -> {
            validateEnum(Deal.Status.class, status, path + ".stepsByStatus key", violations);
            validateStatusSelectsSteps(Deal.Status.class, status, DEAL_SELECTED_STATUSES, steps, path, violations);
            for (int index = 0; index < steps.size(); index++) {
                String stepPath = path + ".stepsByStatus[" + status + "][" + index + "]";
                validateDealLevelStepType(steps.get(index), stepPath, violations);
                validateDealLevelActions(steps.get(index), stepPath, violations);
                validateStepPackageNotEmpty(steps.get(index), Boolean.TRUE, stepPath, violations);
                validateStep(steps.get(index), stepPath, indicatorTypes, structureKeys, actionKeys, violations);
            }
        });
    }

    /**
     * Шаги объявлены только под статусом, где их ОТБИРАЮТ (дом правила —
     * docs/rules/strategy-validation.md; отбор по статусам — разделы «Шаги
     * статуса» компонент-доков обработчиков транша и сделки).
     *
     * <p>Шаг под статусом без отбора не исполнится никогда, при любом рынке,
     * и принятый молча он обещал бы автору поведение, которого нет. Ключ вне
     * перечня статусов здесь не повторяется — его отвергает разбор перечня;
     * пустой перечень шагов не объявляет ничего и отказа не даёт.
     */
    private <E extends Enum<E>> void validateStatusSelectsSteps(Class<E> statusType, String status,
                                                                Set<String> selectedStatuses,
                                                                List<StrategyStepApiModel> steps, String path,
                                                                List<String> violations) {
        if (isEmpty(steps) || isFalse(EnumUtils.isValidEnum(statusType, status))
                || selectedStatuses.contains(status)) {
            return;
        }
        violations.add(path + ".stepsByStatus[" + status + "] STRATEGY_STEP_STATUS_WITHOUT_SELECTION: "
                + "под статусом " + status + " шаги не отбираются — объявленные здесь не исполнятся никогда");
    }

    /**
     * Агрегатная поверхность УЗКАЯ: на детали законны только шаги
     * {@code EXIT} и {@code FAIL_SAFE}. Выход из сделки — утверждение обо
     * всех траншах сразу; всё остальное поведение объявляется на транше,
     * и шаг иного типа здесь размножился бы по N объявлениям молча
     * (docs/models/domain/aggregate/Strategy.md).
     */
    private void validateDealLevelStepType(StrategyStepApiModel step, String path, List<String> violations) {
        if (StrategyStepType.EXIT.name().equals(step.getStepType())
                || StrategyStepType.FAIL_SAFE.name().equals(step.getStepType())) {
            return;
        }
        violations.add(path + ".stepType STRATEGY_DEAL_LEVEL_STEP_OUT_OF_SCOPE: "
                + "агрегатная поверхность допускает только EXIT и FAIL_SAFE, объявлено " + step.getStepType());
    }

    /**
     * Пакет шага уровня сделки несёт только действие выхода — вид
     * {@code POSITION}, тип {@code EXIT_ACTION}. Шаг этого уровня работает
     * РЕБРОМ: сработав, он уводит сделку в координированный выход, а пакет
     * исполнителями действий не запускается — объявленный выход исполняет
     * сворачивание. Действие иного типа или вида здесь молча не делало бы
     * ничего (docs/rules/no-partial-close.md §«Две законные формы полного
     * выхода»; дом правила — docs/rules/strategy-validation.md).
     */
    private void validateDealLevelActions(StrategyStepApiModel step, String path, List<String> violations) {
        if (isEmpty(step.getActions())) {
            return;
        }
        List<StrategyActionApiModel> actions = step.getActions();
        for (int index = 0; index < actions.size(); index++) {
            StrategyActionApiModel action = actions.get(index);
            Boolean positionKind = action instanceof StrategyPositionActionApiModel;
            if (isTrue(positionKind) && StrategyActionType.EXIT_ACTION.name().equals(action.getActionType())) {
                continue;
            }
            violations.add(path + ".actions[" + index + "] STRATEGY_DEAL_LEVEL_ACTION_OUT_OF_SCOPE: "
                    + "пакет шага уровня сделки допускает только выход позиции (POSITION, EXIT_ACTION), объявлено "
                    + action.getActionType() + (isTrue(positionKind) ? "" : " вне вида POSITION"));
        }
    }

    /**
     * Пара «вид, тип» действия шага транша принадлежит перечню допустимых.
     * Пару вне перечня ядро не исполняет: оркестратор действий не находит
     * ей исполнителя, читает действие неприменимым и пропускает молча —
     * объявленное поведение не исполнялось бы без отказа. Тип вне перечня
     * здесь не повторяется: его отвергает разбор перечня. Пакет шага сделки
     * сужен строже и проверяется {@code validateDealLevelActions} (дом
     * правила — docs/rules/strategy-validation.md).
     */
    private void validateTrancheActionPairs(StrategyStepApiModel step, String path, List<String> violations) {
        if (isEmpty(step.getActions())) {
            return;
        }
        List<StrategyActionApiModel> actions = step.getActions();
        for (int index = 0; index < actions.size(); index++) {
            StrategyActionApiModel action = actions.get(index);
            StrategyActionType type = EnumUtils.getEnum(StrategyActionType.class, action.getActionType());
            Set<StrategyActionType> allowed = TYPES_BY_KIND.getOrDefault(action.getClass(),
                    EnumSet.noneOf(StrategyActionType.class));
            if (isNull(type) || allowed.contains(type)) {
                continue;
            }
            violations.add(path + ".actions[" + index + "] STRATEGY_ACTION_KIND_TYPE_UNSUPPORTED: "
                    + "у этого вида действия допустимы " + allowed + ", объявлено " + type);
        }
    }

    /** Ключ действия уникален в рамках детали (через шаги ОБОИХ уровней) — правила валидации 1-2. */
    private Set<String> collectActionKeys(StrategyDetailApiModel detail, String path, List<String> violations) {
        Set<String> keys = new HashSet<>();
        detailStepStreams(detail).forEach(step -> {
            if (isNull(step.getActions())) {
                return;
            }
            step.getActions().forEach(action -> {
                if (nonNull(action.getKey()) && isFalse(keys.add(action.getKey()))) {
                    violations.add(path + ": duplicate action key " + action.getKey());
                }
            });
        });
        return keys;
    }

    /** Все шаги детали — потраншевые и агрегатные, в порядке объявления. */
    private List<StrategyStepApiModel> detailStepStreams(StrategyDetailApiModel detail) {
        List<StrategyStepApiModel> steps = new ArrayList<>();
        emptyIfNull(detail.getTranches()).stream()
                .map(StrategyTrancheApiModel::getStepsByStatus)
                .filter(Objects::nonNull)
                .forEach(byStatus -> byStatus.values().forEach(steps::addAll));
        if (nonNull(detail.getStepsByStatus())) {
            detail.getStepsByStatus().values().forEach(steps::addAll);
        }
        return steps;
    }

    private void validateStep(StrategyStepApiModel step, String path,
                              Map<String, IndicatorValue.Type> indicatorTypes, Set<String> structureKeys,
                              Set<String> actionKeys, List<String> violations) {
        validateEnum(StrategyStepType.class, step.getStepType(), path + ".stepType", violations);
        if (nonNull(step.getMarketDataExpiredSetting())) {
            validateEnum(MarketDataExpiredAction.class,
                    step.getMarketDataExpiredSetting().getProtectedPositionAction(),
                    path + ".marketDataExpiredSetting.protectedPositionAction", violations);
            validateEnum(MarketDataExpiredAction.class,
                    step.getMarketDataExpiredSetting().getUnprotectedPositionAction(),
                    path + ".marketDataExpiredSetting.unprotectedPositionAction", violations);
        }
        validateConditionNotEmpty(step.getCondition(), path + ".condition", violations);
        if (nonNull(step.getCondition()) && nonNull(step.getCondition().getRules())) {
            List<StrategyConditionRuleApiModel> rules = step.getCondition().getRules();
            for (int index = 0; index < rules.size(); index++) {
                validateRule(rules.get(index), path + ".condition.rules[" + index + "]",
                        indicatorTypes, structureKeys, violations);
            }
        }
        if (nonNull(step.getActions())) {
            for (int index = 0; index < step.getActions().size(); index++) {
                validateAction(step.getActions().get(index), path + ".actions[" + index + "]",
                        indicatorTypes, structureKeys, actionKeys, violations);
            }
        }
    }

    /**
     * Пустой пакет действий законен ТОЛЬКО у шага {@code EXIT} уровня
     * СДЕЛКИ: это вторая объявленная форма полного выхода — «шаг EXIT несёт
     * только условие» (docs/rules/no-partial-close.md), — и всю работу там
     * делает ребро в координированный выход. Шаг транша ребром не работает:
     * что он делает, задаёт его пакет, а не тип, — и шаг выхода транша без
     * действий не делает ничего, как и шаг любого другого типа.
     *
     * <p>Прежде обязательность жила аннотацией {@code @NotEmpty}, то есть
     * код запрещал форму, которую корпус объявляет и на которую опирается
     * предусловие {@code netCloseAllowed}. Дом правила —
     * docs/rules/strategy-validation.md.
     *
     * @param bareExitAllowed шаг объявлен на уровне сделки — экземпция типа
     *                        выхода действует только там
     */
    private void validateStepPackageNotEmpty(StrategyStepApiModel step, Boolean bareExitAllowed, String path,
                                             List<String> violations) {
        if (isNotEmpty(step.getActions())) {
            return;
        }
        if (isTrue(bareExitAllowed) && StrategyStepType.EXIT.name().equals(step.getStepType())) {
            return;
        }
        violations.add(path + ".actions STRATEGY_STEP_ACTIONS_EMPTY: пакет действий пуст, "
                + "а пустой пакет законен только у шага EXIT уровня сделки");
    }

    /**
     * Условие шага и клаузы классификации фазы несёт хотя бы одно правило
     * (дом правила — docs/rules/strategy-condition-contract.md §«Условие
     * непусто»; код — docs/rules/strategy-validation.md).
     *
     * <p>Оценка читает пустое условие истиной — нейтральным элементом
     * конъюнкции, — и молча пропущенное оно дало бы безусловный шаг либо
     * безусловную фазу: у клаузы — фазу каждому инструменту на каждом
     * проходе, за которой следующие клаузы не читаются вовсе. Опущенное
     * условие и пустой перечень правил — одно состояние и один код.
     *
     * <p>Держит проверку валидатор, а не аннотация api-модели, по доводу
     * {@code validateFractionPositive}: именованный код с аннотацией был бы
     * недостижим.
     */
    private void validateConditionNotEmpty(StrategyConditionApiModel condition, String path,
                                           List<String> violations) {
        if (nonNull(condition) && isNotEmpty(condition.getRules())) {
            return;
        }
        violations.add(path + " STRATEGY_CONDITION_EMPTY: условие несёт хотя бы одно правило, "
                + "а пустое оценка читает истиной");
    }

    private void validateRule(StrategyConditionRuleApiModel rule, String path,
                              Map<String, IndicatorValue.Type> indicatorTypes,
                              Set<String> structureKeys, List<String> violations) {
        validateEnum(StrategyConditionRuleType.class, rule.getRuleType(), path + ".ruleType", violations);
        validateOperatorAndTimeframe(rule, path, violations);
        validateOperand(rule.getLeftOperand(), path + ".leftOperand", indicatorTypes, structureKeys, violations);
        validateOperand(rule.getRightOperand(), path + ".rightOperand", indicatorTypes, structureKeys, violations);
        validateRuleContract(rule, path, violations);
    }

    /**
     * Оператор и таймфрейм правила — из своих перечней, в ОБОИХ контекстах:
     * у правила шага и у клаузы классификации фазы. Граница держит перечни
     * строкой затем, чтобы сверять их самой (.claude/rules/codestyle.md
     * §«Слои моделей и enum'ы»); пропущенное значение роняло бы разбор
     * перечня в маппинге, то есть отказ пришёл бы не созданием
     * (docs/rules/strategy-validation.md §«Линия реза»). Пустота здесь не
     * отвергается: обязательность по типу правила держит его контракт.
     */
    private void validateOperatorAndTimeframe(StrategyConditionRuleApiModel rule, String path,
                                              List<String> violations) {
        if (nonNull(rule.getOperator())) {
            validateEnum(StrategyConditionOperator.class, rule.getOperator(), path + ".operator", violations);
        }
        if (nonNull(rule.getTimeframe())) {
            validateEnum(TimeFrame.class, rule.getTimeframe(), path + ".timeframe", violations);
        }
    }

    /**
     * Контракт полей и операндов по типу правила — дом
     * docs/rules/strategy-condition-contract.md §«Правило и операнды».
     */
    private void validateRuleContract(StrategyConditionRuleApiModel rule, String path, List<String> violations) {
        if (isFalse(EnumUtils.isValidEnum(StrategyConditionRuleType.class, rule.getRuleType()))) {
            return;
        }
        switch (StrategyConditionRuleType.valueOf(rule.getRuleType())) {
            case PROFIT_PERCENTS_REACHED, LOSS_PERCENTS_REACHED -> {
                if (isNull(rule.getPercents())) {
                    violations.add(path + ": percents is required for " + rule.getRuleType());
                }
            }
            case RANGE_BREAKOUT_CONFIRMED -> validateRangeBreakout(rule, path, violations);
            case MARKET_PHASE_IS -> validateMarketPhaseIs(rule, path, violations);
            case MARKET_STRUCTURE_IS -> validateMarketStructureIs(rule, path, violations);
            case INDICATOR_COMPARE -> validateComparing(rule, path,
                    StrategyConditionSourceType.INDICATOR, violations);
            case PRICE_COMPARE -> validateComparing(rule, path, StrategyConditionSourceType.PRICE, violations);
            case CROSSOVER -> validateCrossover(rule, path, violations);
            case VOLUME_FILTER_PASSED -> validateVolumeFilter(rule, path, violations);
            default -> {
            }
        }
    }

    /**
     * RANGE_BREAKOUT_CONFIRMED — структурно-событийное: ссылается на
     * MarketStructure операндом по structureKey (буфер/подтверждение —
     * params резолвера, не поле условия; событие пробоя читается готовым)
     * и объявляет направление пробоя константой перечня
     * {@link MarketBreakoutEvent.Direction} — та же форма, что у
     * MARKET_STRUCTURE_IS. Без направления вход «на подтверждённом
     * пробое» открывался бы и на сломе против сделки
     * (docs/rules/strategy-validation.md).
     */
    private void validateRangeBreakout(StrategyConditionRuleApiModel rule, String path, List<String> violations) {
        if (isFalse(hasOperandOfSource(rule, StrategyConditionSourceType.MARKET_STRUCTURE))) {
            violations.add(path + ": " + rule.getRuleType()
                    + " requires a MARKET_STRUCTURE operand (structureKey)");
            return;
        }
        if (isNull(rule.getOperator()) || isNull(rule.getLeftOperand()) || isNull(rule.getRightOperand())) {
            violations.add(path + ": RANGE_BREAKOUT_CONFIRMED requires operator and both operands");
            return;
        }
        validateEqualityOperator(rule, path, violations);
        StrategyConditionOperandApiModel constant =
                constantOperand(rule.getLeftOperand(), rule.getRightOperand());
        if (isNull(constant)
                || isFalse(EnumUtils.isValidEnum(MarketBreakoutEvent.Direction.class, constant.getValue()))) {
            violations.add(path + ": RANGE_BREAKOUT_CONFIRMED requires a CONSTANT operand"
                    + " with the breakout direction (UP or DOWN)");
        }
    }

    /**
     * MARKET_STRUCTURE_IS — зеркало MARKET_PHASE_IS: операнд структуры
     * против константы, чьё значение — член перечня
     * {@link MarketStructure.Type}; оператор — {@code EQ} либо {@code NE}.
     * Та же форма, что у RANGE_BREAKOUT_CONFIRMED (дом —
     * docs/rules/strategy-condition-contract.md §«Правило и операнды»).
     *
     * <p>Значение сверяется с перечнем при ЛЮБОМ объявленном типе значения
     * константы и при пустом значении: оценка читает значение константы
     * строкой, не глядя на её тип, и на значении вне перечня её ответ от
     * рынка не зависит — при {@code EQ} правило не сработало бы никогда, при
     * {@code NE} срабатывало бы всегда, молча.
     */
    private void validateMarketStructureIs(StrategyConditionRuleApiModel rule, String path, List<String> violations) {
        if (isNull(rule.getOperator()) || isNull(rule.getLeftOperand()) || isNull(rule.getRightOperand())) {
            violations.add(path + ": MARKET_STRUCTURE_IS requires operator and both operands");
            return;
        }
        if (isFalse(hasOperandOfSource(rule, StrategyConditionSourceType.MARKET_STRUCTURE))) {
            violations.add(path + ": MARKET_STRUCTURE_IS requires a MARKET_STRUCTURE operand (structureKey)");
        }
        validateEqualityOperator(rule, path, violations);
        StrategyConditionOperandApiModel constant =
                constantOperand(rule.getLeftOperand(), rule.getRightOperand());
        if (isNull(constant)) {
            violations.add(path + ": MARKET_STRUCTURE_IS requires a CONSTANT operand with the structure type");
            return;
        }
        if (isFalse(EnumUtils.isValidEnum(MarketStructure.Type.class, constant.getValue()))) {
            violations.add(path + ": unknown MarketStructure.Type " + constant.getValue());
        }
    }

    /**
     * Оператор правила над перечнем — только {@code EQ} либо {@code NE}
     * (довод — {@link #EQUALITY_OPERATORS}). Пустой оператор сюда не
     * доезжает: его отвергает ранний возврат вызывающего.
     */
    private void validateEqualityOperator(StrategyConditionRuleApiModel rule, String path,
                                          List<String> violations) {
        if (isFalse(EQUALITY_OPERATORS.contains(rule.getOperator()))) {
            violations.add(path + ": " + rule.getRuleType() + " accepts only EQ or NE, got " + rule.getOperator());
        }
    }

    private Boolean hasOperandOfSource(StrategyConditionRuleApiModel rule, StrategyConditionSourceType source) {
        return isTrue(ofSource(rule.getLeftOperand(), source)) || isTrue(ofSource(rule.getRightOperand(), source));
    }

    /** Операнд объявлен и его источник — названный. */
    private Boolean ofSource(StrategyConditionOperandApiModel operand, StrategyConditionSourceType source) {
        return nonNull(operand) && Objects.equals(operand.getSourceType(), source.name());
    }

    /**
     * MARKET_PHASE_IS — константа, чьё значение — член перечня
     * {@link MarketPhase.Type}, и оператор {@code EQ} либо {@code NE}; та же
     * форма и тот же довод, что у {@link #validateMarketStructureIs}: оценка
     * читает ложью всякий иной оператор и значение, не разобранное перечнем,
     * при любом объявленном типе значения.
     */
    private void validateMarketPhaseIs(StrategyConditionRuleApiModel rule, String path, List<String> violations) {
        if (isNull(rule.getOperator()) || isNull(rule.getLeftOperand()) || isNull(rule.getRightOperand())) {
            violations.add(path + ": MARKET_PHASE_IS requires operator and both operands");
            return;
        }
        validateEqualityOperator(rule, path, violations);
        StrategyConditionOperandApiModel constant =
                constantOperand(rule.getLeftOperand(), rule.getRightOperand());
        if (isNull(constant)) {
            violations.add(path + ": MARKET_PHASE_IS requires a CONSTANT operand with the phase");
            return;
        }
        if (isFalse(EnumUtils.isValidEnum(MarketPhase.Type.class, constant.getValue()))) {
            violations.add(path + ": unknown MarketPhase.Type " + constant.getValue());
        }
    }

    private StrategyConditionOperandApiModel constantOperand(StrategyConditionOperandApiModel left,
                                                             StrategyConditionOperandApiModel right) {
        if (Objects.equals(left.getSourceType(), StrategyConditionSourceType.CONSTANT.name())) {
            return left;
        }
        if (Objects.equals(right.getSourceType(), StrategyConditionSourceType.CONSTANT.name())) {
            return right;
        }
        return null;
    }

    private void validateComparing(StrategyConditionRuleApiModel rule, String path,
                                   StrategyConditionSourceType requiredSource, List<String> violations) {
        if (isNull(rule.getOperator()) || isNull(rule.getLeftOperand()) || isNull(rule.getRightOperand())) {
            violations.add(path + ": " + rule.getRuleType() + " requires operator and both operands");
            return;
        }
        Boolean hasRequired = Objects.equals(rule.getLeftOperand().getSourceType(), requiredSource.name())
                || Objects.equals(rule.getRightOperand().getSourceType(), requiredSource.name());
        if (isFalse(hasRequired)) {
            violations.add(path + ": " + rule.getRuleType() + " requires an operand with sourceType "
                    + requiredSource.name());
        }
    }

    private void validateCrossover(StrategyConditionRuleApiModel rule, String path, List<String> violations) {
        if (isNull(rule.getOperator()) || isNull(rule.getLeftOperand()) || isNull(rule.getRightOperand())) {
            violations.add(path + ": CROSSOVER requires operator and both operands");
            return;
        }
        Boolean crossOperator = Objects.equals(rule.getOperator(),
                StrategyConditionOperator.CROSSED_ABOVE.name())
                || Objects.equals(rule.getOperator(), StrategyConditionOperator.CROSSED_BELOW.name());
        if (isFalse(crossOperator)) {
            violations.add(path + ": CROSSOVER requires operator CROSSED_ABOVE or CROSSED_BELOW");
        }
        validateCrossoverPricePair(rule, path, violations);
    }

    /**
     * Пересечение с ценовым операндом пишется только в паре с индикаторным
     * (docs/rules/strategy-condition-contract.md §«Прошлое цены задаёт
     * индикатор-пара»; код — docs/rules/strategy-validation.md).
     *
     * <p>Пересечение сравнивает обе стороны и в прошлом, а своего прошлого у
     * цены нет: им служит цена закрытия свечи, на которой посчитано
     * предыдущее значение индикатора на ДРУГОЙ стороне. Цена против
     * константы либо против цены прошлого не имеет вовсе, и оценка читает
     * такое правило ложью всегда — пересечение не сработало бы никогда,
     * молча.
     */
    private void validateCrossoverPricePair(StrategyConditionRuleApiModel rule, String path,
                                            List<String> violations) {
        Boolean leftUnpaired = isTrue(ofSource(rule.getLeftOperand(), StrategyConditionSourceType.PRICE))
                && isFalse(ofSource(rule.getRightOperand(), StrategyConditionSourceType.INDICATOR));
        Boolean rightUnpaired = isTrue(ofSource(rule.getRightOperand(), StrategyConditionSourceType.PRICE))
                && isFalse(ofSource(rule.getLeftOperand(), StrategyConditionSourceType.INDICATOR));
        if (isTrue(leftUnpaired) || isTrue(rightUnpaired)) {
            violations.add(path + " STRATEGY_CROSSOVER_PRICE_WITHOUT_INDICATOR: пересечение с ценой пишется "
                    + "только в паре с индикатором — прошлое цены задаёт индикатор на другой стороне");
        }
    }

    /**
     * Объёмный фильтр читает прошлое своего ЛЕВОГО операнда, и операнд этот
     * — индикатор (docs/rules/strategy-condition-contract.md §«Прошлое цены
     * задаёт индикатор-пара»; код — docs/rules/strategy-validation.md).
     *
     * <p>У цены своего прошлого нет, у константы прошлое равно настоящему, и
     * рост, который фильтр мерит, у обеих не наблюдается никогда: фильтр
     * на таком операнде ложен всегда, молча. Опущенный операнд — то же
     * состояние и тот же код.
     */
    private void validateVolumeFilter(StrategyConditionRuleApiModel rule, String path, List<String> violations) {
        if (isTrue(ofSource(rule.getLeftOperand(), StrategyConditionSourceType.INDICATOR))) {
            return;
        }
        violations.add(path + ".leftOperand STRATEGY_VOLUME_FILTER_OPERAND_NOT_INDICATOR: объёмный фильтр "
                + "читает прошлое левого операнда, и прошлое есть только у индикатора");
    }

    private void validateOperand(StrategyConditionOperandApiModel operand, String path,
                                 Map<String, IndicatorValue.Type> indicatorTypes, Set<String> structureKeys,
                                 List<String> violations) {
        if (isNull(operand)) {
            return;
        }
        validateEnum(StrategyConditionSourceType.class, operand.getSourceType(), path + ".sourceType", violations);
        if (isFalse(EnumUtils.isValidEnum(StrategyConditionSourceType.class, operand.getSourceType()))) {
            return;
        }
        switch (StrategyConditionSourceType.valueOf(operand.getSourceType())) {
            case INDICATOR -> {
                validateReference(operand.getIndicatorKey(), indicatorTypes.keySet(),
                        path + ".indicatorKey", "indicator setting", violations);
                validateIndicatorComponent(operand, indicatorTypes, path, violations);
            }
            case MARKET_STRUCTURE -> validateReference(operand.getStructureKey(), structureKeys,
                    path + ".structureKey", "market structure setting", violations);
            case PRICE -> {
                validateEnum(StrategyPriceSource.class, operand.getPriceSource(),
                        path + ".priceSource", violations);
                rejectUnavailablePriceSource(operand.getPriceSource(), path + ".priceSource", violations);
            }
            case CONSTANT -> {
                validateEnum(ConstantValueType.class, operand.getValueType(), path + ".valueType", violations);
                if (isNull(operand.getValue())) {
                    violations.add(path + ".value is required for CONSTANT operand");
                }
            }
            default -> {
            }
        }
    }

    /**
     * Адресуемый компонент индикаторного операнда (D1): для
     * многокомпонентных (MACD/Stochastic/Bollinger) обязателен и должен
     * быть допустим для типа; для одно-компонентных не задаётся.
     */
    private void validateIndicatorComponent(StrategyConditionOperandApiModel operand,
                                            Map<String, IndicatorValue.Type> indicatorTypes, String path,
                                            List<String> violations) {
        IndicatorValue.Type type = indicatorTypes.get(operand.getIndicatorKey());
        if (isNull(type)) {
            return;
        }
        String component = operand.getIndicatorComponent();
        if (isFalse(IndicatorComponents.isMultiComponent(type))) {
            if (nonNull(component)) {
                violations.add(path + ".indicatorComponent must not be set for single-component indicator " + type);
            }
            return;
        }
        if (isNull(component)) {
            violations.add(path + ".indicatorComponent is required for multi-component indicator " + type
                    + " (allowed: " + IndicatorComponents.allowedFor(type) + ")");
            return;
        }
        if (isFalse(EnumUtils.isValidEnum(IndicatorComponent.class, component))) {
            validateEnum(IndicatorComponent.class, component, path + ".indicatorComponent", violations);
            return;
        }
        if (isFalse(IndicatorComponents.allowedFor(type).contains(IndicatorComponent.valueOf(component)))) {
            violations.add(path + ".indicatorComponent " + component + " is not valid for indicator " + type
                    + " (allowed: " + IndicatorComponents.allowedFor(type) + ")");
        }
    }

    private void validateAction(StrategyActionApiModel action, String path,
                                Map<String, IndicatorValue.Type> indicatorTypes, Set<String> structureKeys,
                                Set<String> actionKeys, List<String> violations) {
        validateEnum(StrategyActionType.class, action.getActionType(), path + ".actionType", violations);
        if (nonNull(action.getTargetActionKey())
                && isFalse(actionKeys.contains(action.getTargetActionKey()))) {
            violations.add(path + ".targetActionKey references unknown action key "
                    + action.getTargetActionKey() + " (reference must stay inside the detail)");
        }
        switch (action) {
            case StrategyOrderActionApiModel order -> validateOrderAction(order, path,
                    indicatorTypes, structureKeys, violations);
            case StrategyAlgoOrderActionApiModel algo -> validateAlgoOrderAction(algo, path,
                    indicatorTypes, structureKeys, violations);
            default -> {
            }
        }
    }

    /**
     * Доля аллокации ОБЪЯВЛЕНА у входного действия. Аннотацией это не
     * выражается: поле живёт на общей модели действия-заявки, а
     * обязательно только у входа.
     *
     * <p>Без проверки пустота проходила обе прежние: фильтр диапазона
     * исключал её условием непустоты, а сумма объявленного нотинала
     * читала её нулём — и опустить поле было ВЫГОДНЕЕ, чем объявить
     * 100 %, потому что тот же расклад со 100 давал реджект по
     * статическому запасу. Дом правила —
     * docs/rules/strategy-validation.md.
     */
    private void validateEntryAllocationDeclared(StrategyOrderActionApiModel action, String path,
                                                 List<String> violations) {
        if (isTrue(entryOrderAction(action)) && isNull(action.getAllocationPercents())) {
            violations.add(path + ".allocationPercents STRATEGY_ACTION_ALLOCATION_NOT_DECLARED: "
                    + "входное действие обязано объявить долю аллокации");
        }
    }

    /**
     * Намерение reduce-only ОБЪЯВЛЕНО у всякого действия-заявки — и
     * отрицанием, и утверждением, но не пустотой.
     *
     * <p>Пустота значения не имеет ни на одной стороне: транш ищет свою
     * ногу входа отрицанием намерения, и пустое намерение отрицанием не
     * является — нога выпадает из выборки, транш не получает входного
     * ребра и стоит в предвходовой проверке навсегда, а на площадку то же
     * значение уезжает «не только сокращать». Умолчания у поля нет по
     * тому же доводу, что у доли аллокации: подставленное значение
     * отвечало бы за автора. Дом правила — docs/rules/strategy-validation.md;
     * счётчик — docs/spec/strategy-reference.json
     * §{@code reduceOnlyIntentNotDeclared}.
     */
    private void validateReduceOnlyIntentDeclared(StrategyOrderActionApiModel action, String path,
                                                  List<String> violations) {
        if (isNull(action.getPositionReducingOnly())) {
            violations.add(path + ".positionReducingOnly STRATEGY_ACTION_REDUCE_ONLY_NOT_DECLARED: "
                    + "действие-заявка обязано объявить намерение reduce-only");
        }
    }

    /**
     * Доля, которой действие объявлено, ПОЛОЖИТЕЛЬНА — обе доли, а не одна.
     *
     * <p>Предмет проверки — диапазон, а не наличие: пустая доля проходит
     * (её наличие мерит своя проверка у входного действия). Нулевая доля —
     * не действие нулевого размера, а отсутствие действия, и объявлять её
     * нечем. Дом правила — docs/rules/strategy-validation.md; счётчики —
     * docs/spec/strategy-reference.json.
     *
     * <p>Проверка живёт здесь, а не аннотацией api-модели: дом объявляет
     * реджект ИМЕНОВАННЫМ кодом, а Bean Validation отвечает до тела
     * обработчика и именованного кода не несёт — ограничение жило бы
     * только в коде.
     */
    private void validateFractionPositive(BigDecimal fraction, String code, String path,
                                          List<String> violations) {
        if (isNull(fraction)) {
            return;
        }
        if (fraction.signum() <= 0 || fraction.compareTo(FRACTION_PERCENTS_MAX) > 0) {
            violations.add(path + " " + code + ": доля объявления больше нуля и не выше ста, получено "
                    + fraction);
        }
    }

    /**
     * {@code BREAKEVEN} допустим только как ПЕРЕНОС уже стоящего уровня.
     *
     * <p>Первичной защитой он быть не может: уровень нулевого P&amp;L лежит
     * на прибыльной стороне, поэтому worst-case выхода он не задаёт,
     * дистанция риска схлопывается до round-trip комиссии, а сайзинг
     * раздувается во столько же раз. Дом довода —
     * docs/spec/stop-distance.json §{@code breakevenRoleAllowed}; форма на
     * дереве стратегии — docs/spec/strategy-reference.json
     * §{@code breakevenAsPrimaryStop}.
     *
     * <p>Способ читается с ОБОИХ носителей уровня — своих настроек стопа и
     * настроек встроенной защиты входа: иначе тот же BREAKEVEN проходил бы
     * второй тропой.
     */
    private void validateBreakevenIsTransfer(StrategyActionApiModel action, StopLossSettingsApiModel ownStop,
                                             StopLossSettingsApiModel attachedStop, String path,
                                             List<String> violations) {
        String calculationType = nonNull(ownStop) && nonNull(ownStop.getCalculationType())
                ? ownStop.getCalculationType()
                : nonNull(attachedStop) ? attachedStop.getCalculationType() : null;
        if (isFalse(StopLossCalculationType.BREAKEVEN.name().equals(calculationType))) {
            return;
        }
        if (isFalse(levelTransfer(action))) {
            violations.add(path + " STRATEGY_BREAKEVEN_NOT_A_TRANSFER: "
                    + "BREAKEVEN объявляется только защитным REPLACE_ACTION с targetActionKey");
        }
    }

    /**
     * Действие ПЕРЕНОСИТ уже стоящий уровень: защитный {@code REPLACE_ACTION}
     * с названной целью (docs/spec/strategy-reference.json
     * §{@code actionIsLevelTransfer}). Всё прочее — первичная постановка.
     */
    private Boolean levelTransfer(StrategyActionApiModel action) {
        return action instanceof StrategyAlgoOrderActionApiModel
                && StrategyActionType.REPLACE_ACTION.name().equals(action.getActionType())
                && isNotBlank(action.getTargetActionKey());
    }

    /**
     * База срабатывания ЗАЩИТНОЙ условной заявки — только {@code MARK}.
     *
     * <p>Ликвидацию биржа считает по марк-цене, а last-цену на тонком рынке
     * двигают единичной сделкой: стоп по {@code LAST} снимается манипуляцией,
     * не сдвинув марк, а в обратном случае марк уходит к ликвидации, пока
     * {@code LAST}-триггер молчит. Дом довода и условия снятия —
     * docs/models/domain/core/AlgoOrder.md; реджект объявлен
     * docs/rules/strategy-validation.md.
     *
     * <p>Область — защитная условная заявка по собственному типу условия
     * (docs/spec/strategy-reference.json §{@code isProtectiveAction}): у
     * СНИМАЮЩЕГО действия тип условия лишь копирует тип цели, и решение на
     * копии стоять не может.
     */
    private void validateProtectiveTriggerIsMark(StrategyAlgoOrderActionApiModel action, String path,
                                                 List<String> violations) {
        if (isFalse(PROTECTIVE_CONDITION_TYPES.contains(action.getConditionType()))) {
            return;
        }
        requireMarkTrigger(action.getTriggerPriceType(), path + ".triggerPriceType", violations);
        if (nonNull(action.getStopLossSettings())) {
            requireMarkTrigger(action.getStopLossSettings().getTriggerPriceType(),
                    path + ".stopLossSettings.triggerPriceType", violations);
        }
    }

    /**
     * Встроенная защита входа — тоже защита, и база её срабатывания доезжает
     * до площадки ({@code slTriggerPxType}, docs/models/mapping/Order.md):
     * ограничение {@code MARK} действует на неё той же областью, что на
     * защитную условную заявку (docs/models/domain/core/AlgoOrder.md).
     */
    private void validateAttachedTriggerIsMark(StrategyAttachedProtectionSettingsApiModel protection, String path,
                                               List<String> violations) {
        if (isNull(protection.getStopLossSettings())) {
            return;
        }
        requireMarkTrigger(protection.getStopLossSettings().getTriggerPriceType(),
                path + ".attachedProtection.stopLossSettings.triggerPriceType", violations);
    }

    private void requireMarkTrigger(String triggerPriceType, String path, List<String> violations) {
        if (isNull(triggerPriceType)) {
            return;
        }
        if (isFalse(AlgoOrder.TriggerPriceType.MARK.name().equals(triggerPriceType))) {
            violations.add(path + " STRATEGY_TRIGGER_PRICE_TYPE_NOT_MARK: "
                    + "защита срабатывает только по MARK, получено " + triggerPriceType);
        }
    }

    private void validateOrderAction(StrategyOrderActionApiModel action, String path,
                                     Map<String, IndicatorValue.Type> indicatorTypes,
                                     Set<String> structureKeys, List<String> violations) {
        validateEnum(Order.Type.class, action.getOrderType(), path + ".orderType", violations);
        validateEnum(StrategyTradeDirection.class, action.getDirection(), path + ".direction", violations);
        validateReduceOnlyIntentDeclared(action, path, violations);
        validateEntryAllocationDeclared(action, path, violations);
        validateFractionPositive(action.getAllocationPercents(), "STRATEGY_ACTION_ALLOCATION_NOT_POSITIVE",
                path + ".allocationPercents", violations);
        validateBreakevenIsTransfer(action, null,
                nonNull(action.getAttachedProtection()) ? action.getAttachedProtection().getStopLossSettings() : null,
                path, violations);
        if (nonNull(action.getPlacement())) {
            validatePlacement(action, path + ".placement", structureKeys, violations);
        }
        if (nonNull(action.getAttachedProtection())) {
            validateEnum(AttachedAlgoOrder.Type.class, action.getAttachedProtection().getAttachedType(),
                    path + ".attachedProtection.attachedType", violations);
            validateStopLoss(action.getAttachedProtection().getStopLossSettings(),
                    path + ".attachedProtection.stopLossSettings", indicatorTypes, structureKeys, violations);
            validateAttachedTriggerIsMark(action.getAttachedProtection(), path, violations);
        }
        if (Objects.equals(action.getOrderType(), Order.Type.ENTRY_ATTACHED_STOP_LOSS.name())
                && isNull(action.getAttachedProtection())) {
            violations.add(path + ": attachedProtection is required for ENTRY_ATTACHED_STOP_LOSS");
        }
    }

    private void validatePlacement(StrategyOrderActionApiModel action, String path,
                                   Set<String> structureKeys, List<String> violations) {
        validateEnum(StrategyPriceBaseType.class, action.getPlacement().getBaseType(),
                path + ".baseType", violations);
        if (nonNull(action.getPlacement().getOffsetSide())) {
            validateEnum(StrategyPriceOffsetSide.class, action.getPlacement().getOffsetSide(),
                    path + ".offsetSide", violations);
        }
        if (nonNull(action.getPlacement().getPriceSource())) {
            validateEnum(StrategyPriceSource.class, action.getPlacement().getPriceSource(),
                    path + ".priceSource", violations);
        }
        if (isFalse(EnumUtils.isValidEnum(StrategyPriceBaseType.class, action.getPlacement().getBaseType()))) {
            return;
        }
        StrategyPriceBaseType baseType = StrategyPriceBaseType.valueOf(action.getPlacement().getBaseType());
        Boolean structural = isFalse(Objects.equals(baseType, StrategyPriceBaseType.ENTRY_PRICE))
                && isFalse(Objects.equals(baseType, StrategyPriceBaseType.MARKET_PRICE));
        if (structural) {
            validateReference(action.getPlacement().getStructureKey(), structureKeys,
                    path + ".structureKey", "market structure setting", violations);
        }
        if (Objects.equals(baseType, StrategyPriceBaseType.MARKET_PRICE)
                && isNull(action.getPlacement().getPriceSource())) {
            violations.add(path + ".priceSource is required for MARKET_PRICE base");
        }
        if (Objects.equals(baseType, StrategyPriceBaseType.MARKET_PRICE)) {
            rejectUnavailablePriceSource(action.getPlacement().getPriceSource(), path + ".priceSource", violations);
        }
    }

    /**
     * Источник рыночной цены, которого источник данных не отдаёт, отвергается
     * на ОБОИХ носителях — у размещения цены и у ценового операнда условия
     * (docs/spec/strategy-reference.json, величины
     * {@code priceSourceUnavailable} и {@code conditionPriceSourceUnavailable}).
     *
     * <p>Тикер площадки марк- и индексной цены не несёт, а калькулятор
     * подставил бы последнюю: базис уехал бы в цену входа молча. Отвергать
     * порознь нельзя — условие «цена ≥ марк-цена ± %» проходило бы создание и
     * подменялось той же тропой (docs/rules/strategy-validation.md).
     */
    private void rejectUnavailablePriceSource(String priceSource, String path, List<String> violations) {
        if (nonNull(priceSource) && UNAVAILABLE_PRICE_SOURCES.contains(priceSource)) {
            violations.add(path + " STRATEGY_PRICE_SOURCE_UNAVAILABLE: "
                    + "источник данных не отдаёт " + priceSource);
        }
    }

    /**
     * Действие объявило ОБА блока настроек уровня — резолв источника
     * неоднозначен (docs/spec/strategy-reference.json, величина
     * {@code levelSourceAmbiguous}). Без реджекта пришлось бы вводить
     * приоритет блоков, то есть отвечать за автора там, где он сам себе
     * противоречит. Дом правила — docs/rules/strategy-validation.md.
     */
    private void validateLevelSourceUnambiguous(StrategyAlgoOrderActionApiModel action, String path,
                                                List<String> violations) {
        if (nonNull(action.getTrailingSettings()) && nonNull(action.getStopLossSettings())) {
            violations.add(path + " STRATEGY_LEVEL_SOURCE_AMBIGUOUS: действие объявило и блок трейлинга, "
                    + "и блок стопа — источник уровня не резолвится");
        }
    }

    /**
     * Действие, СТАВЯЩЕЕ уровень, объявило его источник — блок стопа либо
     * блок трейлинга (docs/spec/strategy-reference.json, величина
     * {@code actionsSettingLevelWithoutSource}). Обратное состояние к
     * {@link #validateLevelSourceUnambiguous}: без обоих блоков уровень
     * неизвестен ни в момент постановки, ни после, и под охрану стороны
     * уровня действие не попадает ни одним признаком.
     *
     * <p>Область — ставящие уровень: защитное создание и защитное замещение
     * ({@code actionSetsLevel}). Снимающее и выходные действия уровня не
     * ставят вовсе, и источник у них не требуется. Дом правила —
     * docs/rules/strategy-validation.md §«Что проверяется на создании».
     */
    private void validateLevelSourceDeclared(StrategyAlgoOrderActionApiModel action, String path,
                                             List<String> violations) {
        if (isFalse(protectiveAction(action)) || isFalse(coverageSettingAction(action))) {
            return;
        }
        if (isNull(action.getStopLossSettings()) && isNull(action.getTrailingSettings())) {
            violations.add(path + " STRATEGY_LEVEL_SOURCE_NOT_DECLARED: ставящее уровень действие "
                    + "не объявило ни блока стопа, ни блока трейлинга");
        }
    }

    /**
     * Трейлинг абсолютным откатом не объявляется — у всякого действия над
     * условной заявкой: у создающего, у замещающего и у снимающего.
     *
     * <p>Настройки трейлинга несут одну величину отката, и она процентная;
     * абсолютной величины у объявления нет, и тип {@code TRAILING_VALUE}
     * расчёт и площадка исполняли бы процентным трейлингом, не сообщая об
     * этом. Условие возврата — абсолютная величина отката у настроек
     * трейлинга. Дом правила — docs/rules/strategy-validation.md.
     */
    private void validateConditionTypeSupported(StrategyAlgoOrderActionApiModel action, String path,
                                                List<String> violations) {
        if (AlgoOrder.ConditionType.TRAILING_VALUE.name().equals(action.getConditionType())) {
            violations.add(path + ".conditionType STRATEGY_CONDITION_TYPE_UNSUPPORTED: трейлинг абсолютным "
                    + "откатом не объявляется — настройки трейлинга несут только процентный откат");
        }
    }

    private void validateAlgoOrderAction(StrategyAlgoOrderActionApiModel action, String path,
                                         Map<String, IndicatorValue.Type> indicatorTypes, Set<String> structureKeys,
                                         List<String> violations) {
        validateEnum(AlgoOrder.ConditionType.class, action.getConditionType(), path + ".conditionType", violations);
        validateConditionTypeSupported(action, path, violations);
        validateLevelSourceUnambiguous(action, path, violations);
        validateLevelSourceDeclared(action, path, violations);
        validateFractionPositive(action.getCloseFractionPercents(), "STRATEGY_ACTION_FRACTION_NOT_POSITIVE",
                path + ".closeFractionPercents", violations);
        validateProtectiveTriggerIsMark(action, path, violations);
        validateBreakevenIsTransfer(action, action.getStopLossSettings(), null, path, violations);
        if (nonNull(action.getTriggerPriceType())) {
            validateEnum(AlgoOrder.TriggerPriceType.class, action.getTriggerPriceType(),
                    path + ".triggerPriceType", violations);
        }
        validateStopLoss(action.getStopLossSettings(), path + ".stopLossSettings",
                indicatorTypes, structureKeys, violations);
    }

    private void validateStopLoss(StopLossSettingsApiModel settings, String path,
                                  Map<String, IndicatorValue.Type> indicatorTypes, Set<String> structureKeys,
                                  List<String> violations) {
        if (isNull(settings)) {
            return;
        }
        validateEnum(StopLossCalculationType.class, settings.getCalculationType(),
                path + ".calculationType", violations);
        validateEnum(AlgoOrder.TriggerPriceType.class, settings.getTriggerPriceType(),
                path + ".triggerPriceType", violations);
        validateStopDistanceDeclared(settings, path, violations);
        if (Objects.equals(settings.getCalculationType(), StopLossCalculationType.ATR_PERCENT.name())) {
            validateReference(settings.getIndicatorKey(), indicatorTypes.keySet(),
                    path + ".indicatorKey", "indicator setting", violations);
        }
        if (Objects.equals(settings.getCalculationType(),
                StopLossCalculationType.MARKET_STRUCTURE_BUFFER_PERCENT.name())) {
            validateReference(settings.getStructureKey(), structureKeys,
                    path + ".structureKey", "market structure setting", violations);
        }
    }

    /**
     * Доля дистанции согласована со способом расчёта уровня: у {@code BREAKEVEN}
     * она не объявляется, у прочих способов обязательна
     * (docs/spec/stop-distance.json §{@code distanceDeclaredWhenNeeded}).
     *
     * <p>Пропущенная доля иначе доезжала бы до расчёта цены уровня и отказывала
     * там — в рантайме сделки, где дом ошибку конфигурации встречать запрещает;
     * объявленная у безубытка стала бы вторым носителем величины, которая есть
     * функция ставки комиссии. Способ вне перечня отвергает его сверка, и
     * второго нарушения по доле он не даёт. Дом правила —
     * docs/rules/strategy-validation.md.
     */
    private void validateStopDistanceDeclared(StopLossSettingsApiModel settings, String path,
                                              List<String> violations) {
        if (isFalse(EnumUtils.isValidEnum(StopLossCalculationType.class, settings.getCalculationType()))) {
            return;
        }
        Boolean breakeven = StopLossCalculationType.BREAKEVEN.name().equals(settings.getCalculationType());
        if (breakeven && nonNull(settings.getDistancePercents())) {
            violations.add(path + ".distancePercents STRATEGY_STOP_DISTANCE_UNEXPECTED: "
                    + "у безубытка доля дистанции не объявляется — уровень есть функция ставки комиссии");
        }
        if (isFalse(breakeven) && isNull(settings.getDistancePercents())) {
            violations.add(path + ".distancePercents STRATEGY_STOP_DISTANCE_MISSING: "
                    + "способ расчёта " + settings.getCalculationType() + " требует доли дистанции");
        }
    }

    private void validateReference(String key, Set<String> knownKeys, String path, String targetName,
                                   List<String> violations) {
        if (isNull(key)) {
            violations.add(path + " is required and must reference a " + targetName + " of the strategy");
            return;
        }
        if (isFalse(knownKeys.contains(key))) {
            violations.add(path + " references unknown " + targetName + " key " + key
                    + " (reference must stay inside the strategy)");
        }
    }

    /** Карта key → тип индикатора контейнера (для ref/component/fork-A валидации; невалидные типы пропускаются). */
    private Map<String, IndicatorValue.Type> indicatorTypes(List<StrategyIndicatorSettingApiModel> settings) {
        Map<String, IndicatorValue.Type> result = new HashMap<>();
        if (nonNull(settings)) {
            settings.forEach(setting -> {
                if (nonNull(setting.getKey())
                        && EnumUtils.isValidEnum(IndicatorValue.Type.class, setting.getIndicatorType())) {
                    result.put(setting.getKey(), IndicatorValue.Type.valueOf(setting.getIndicatorType()));
                }
            });
        }
        return result;
    }

    private Set<String> structureSettingKeys(List<StrategyMarketStructureSettingApiModel> settings) {
        Set<String> keys = new HashSet<>();
        if (nonNull(settings)) {
            settings.forEach(setting -> keys.add(setting.getKey()));
        }
        return keys;
    }

    private <E extends Enum<E>> void validateEnum(Class<E> type, String value, String path,
                                                  List<String> violations) {
        if (isNull(value) || isFalse(EnumUtils.isValidEnum(type, value))) {
            violations.add(path + ": unknown value " + value
                    + " (expected one of " + EnumUtils.getEnumList(type) + ")");
        }
    }

    /**
     * Срок свежести объявления рыночных данных ОБЪЯВЛЕН — у индикатора и у
     * структуры; у классификации фазы своего срока нет вовсе
     * (docs/models/domain/aggregate/Strategy.md).
     *
     * <p>Без срока ядро объявление в запрос рыночных данных не включает:
     * операнд недоступен, предикат на нём консервативно ложен всегда, и
     * стратегия молча не входит никогда. Подставлять срок за автора нечем —
     * умолчания у поля нет. Пустая строка и пробелы — то же состояние и тот
     * же код. Держит проверку валидатор, а не аннотация api-модели, по
     * доводу {@code validateFractionPositive}: именованный код с аннотацией
     * был бы недостижим. Дом правила — docs/rules/strategy-validation.md.
     *
     * <p><b>Объявленный срок положителен.</b> Нулевой и отрицательный срок
     * разбираются, но ни одно значение не бывает свежим на таком сроке —
     * предикат ложен всегда, то есть исход тот же, что без срока. Обязательность
     * и диапазон разведены кодами, как у окна расчёта структуры: отказ
     * адресует тот конъюнкт, который ложен. Неразобранная строка диапазоном
     * не мерится — её отвергает разбор.
     */
    private void validateExpirationDeclared(String value, String path, List<String> violations) {
        if (isBlank(value)) {
            violations.add(path + " STRATEGY_MARKET_DATA_EXPIRATION_NOT_DECLARED: срок свежести объявления "
                    + "рыночных данных объявляется явно, умолчания нет");
            return;
        }
        Duration duration = validateDuration(value, path, violations);
        if (nonNull(duration) && isFalse(duration.isPositive())) {
            violations.add(path + " STRATEGY_MARKET_DATA_EXPIRATION_NOT_POSITIVE: срок свежести объявления "
                    + "рыночных данных больше нуля, получено " + value);
        }
    }

    /** Разобранная длительность; строка не разбирается — нарушение и {@code null}. */
    private Duration validateDuration(String value, String path, List<String> violations) {
        try {
            return Duration.parse(value);
        } catch (DateTimeParseException e) {
            violations.add(path + ": invalid ISO-8601 duration " + value);
            return null;
        }
    }
}
