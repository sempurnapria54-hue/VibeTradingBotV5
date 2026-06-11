package com.example.tradingbot.api.validation;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.apache.commons.lang3.BooleanUtils.isFalse;

import com.example.tradingbot.api.model.request.CreateStrategyApiRequest;
import com.example.tradingbot.api.model.request.UpdateStrategyStatusApiRequest;
import com.example.tradingbot.api.model.strategy.AtrParamsApiModel;
import com.example.tradingbot.api.model.strategy.BollingerBandsParamsApiModel;
import com.example.tradingbot.api.model.strategy.EfficiencyRatioParamsApiModel;
import com.example.tradingbot.api.model.strategy.EmaParamsApiModel;
import com.example.tradingbot.api.model.strategy.IndicatorParamsApiModel;
import com.example.tradingbot.api.model.strategy.MacdParamsApiModel;
import com.example.tradingbot.api.model.strategy.RsiParamsApiModel;
import com.example.tradingbot.api.model.strategy.StochasticParamsApiModel;
import com.example.tradingbot.api.model.strategy.StopLossSettingsApiModel;
import com.example.tradingbot.api.model.strategy.StrategyActionApiModel;
import com.example.tradingbot.api.model.strategy.StrategyAlgoOrderActionApiModel;
import com.example.tradingbot.api.model.strategy.StrategyConditionOperandApiModel;
import com.example.tradingbot.api.model.strategy.StrategyConditionRuleApiModel;
import com.example.tradingbot.api.model.strategy.StrategyDetailApiModel;
import com.example.tradingbot.api.model.strategy.StrategyIndicatorSettingApiModel;
import com.example.tradingbot.api.model.strategy.StrategyMarketPhaseRuleApiModel;
import com.example.tradingbot.api.model.strategy.StrategyMarketPhaseSettingApiModel;
import com.example.tradingbot.api.model.strategy.StrategyMarketStructureSettingApiModel;
import com.example.tradingbot.api.model.strategy.StrategyOrderActionApiModel;
import com.example.tradingbot.api.model.strategy.StrategyStepApiModel;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
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
import com.example.tradingbot.domain.model.trade.candle.TimeFrame;
import com.example.tradingbot.domain.model.trade.indicator.IndicatorValue;
import com.example.tradingbot.domain.model.trade.market_phase.MarketPhase;
import com.example.tradingbot.domain.model.trade.market_structure.MarketStructure;
import com.example.tradingbot.util.IndicatorComponents;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
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
 * sanity warmup-override, минимальный per-ruleType контракт операндов
 * (дозаполняется инкрементально). Семантика действий (REPLACE/CANCEL ↔
 * виды, CLOSE_FULL, partial-exit) и торгово-суждённые диапазоны —
 * отложены до шагов 4/7 / activate (422):
 * docs/decisions/strategy-materialization-and-validation.md.
 * Per-field презенс и числовые границы держит Bean Validation на
 * api-моделях. Warmup-floor — упрощённый минимум шага 2; настоящий
 * derive — у реализаций индикаторов (шаг 3).
 */
@Component
public class StrategyCreateRequestValidator {

    /** Допустимые ruleType в контексте классификации фазы (сравнивающие + структурно-событийные). */
    private static final Set<String> PHASE_ALLOWED_RULE_TYPES = Set.of(
            StrategyConditionRuleType.INDICATOR_COMPARE.name(),
            StrategyConditionRuleType.PRICE_COMPARE.name(),
            StrategyConditionRuleType.CROSSOVER.name(),
            StrategyConditionRuleType.RANGE_BREAKOUT_CONFIRMED.name(),
            StrategyConditionRuleType.VOLUME_FILTER_PASSED.name(),
            StrategyConditionRuleType.CANDLE_CLOSED.name(),
            StrategyConditionRuleType.MARKET_STRUCTURE_IS.name());

    /** Допустимые sourceType операндов в контексте классификации фазы (без MARKET_PHASE и runtime-сделки). */
    private static final Set<String> PHASE_ALLOWED_SOURCE_TYPES = Set.of(
            StrategyConditionSourceType.INDICATOR.name(),
            StrategyConditionSourceType.MARKET_STRUCTURE.name(),
            StrategyConditionSourceType.PRICE.name(),
            StrategyConditionSourceType.CONSTANT.name(),
            StrategyConditionSourceType.TIME.name());

    public void validateCreate(CreateStrategyApiRequest request) {
        List<String> violations = new ArrayList<>();
        Map<String, IndicatorValue.Type> indicatorTypes = indicatorTypes(request.getIndicatorSettings());
        Set<String> structureKeys = structureSettingKeys(request.getMarketStructureSettings());
        validateSettingsLists(request.getIndicatorSettings(), request.getMarketStructureSettings(),
                "strategy", indicatorTypes, violations);
        validateMarketPhaseSetting(request.getMarketPhaseSetting(), indicatorTypes, structureKeys, violations);
        validateDetails(request.getDetails(), indicatorTypes, structureKeys, violations);
        if (isNotEmpty(violations)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join("; ", violations));
        }
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

    private void validateDetails(List<StrategyDetailApiModel> details,
                                 Map<String, IndicatorValue.Type> indicatorTypes,
                                 Set<String> structureKeys, List<String> violations) {
        if (isNull(details)) {
            return;
        }
        validatePhaseCoverage(details, violations);
        for (int index = 0; index < details.size(); index++) {
            validateDetail(details.get(index), "details[" + index + "]", indicatorTypes, structureKeys, violations);
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

    private void validateDetail(StrategyDetailApiModel detail, String path,
                                Map<String, IndicatorValue.Type> indicatorTypes,
                                Set<String> structureKeys, List<String> violations) {
        validateEnum(MarketPhase.Type.class, detail.getMarketPhaseType(), path + ".marketPhaseType", violations);
        validateEnum(PhaseEntryPolicy.class, detail.getPhaseEntryPolicy(), path + ".phaseEntryPolicy", violations);
        validatePolicyMatrix(detail, path, violations);
        validateSteps(detail, path, indicatorTypes, structureKeys, violations);
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
        validateDuration(setting.getExpirationDuration(), path + ".expirationDuration", violations);
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
        validateDuration(setting.getExpirationDuration(), path + ".expirationDuration", violations);
        if (nonNull(setting.getEfficiencyRatioKey())) {
            validateIndicatorKeyOfType(setting.getEfficiencyRatioKey(), IndicatorValue.Type.EFFICIENCY_RATIO,
                    indicatorTypes, path + ".efficiencyRatioKey", violations);
        }
        if (nonNull(setting.getAtrKey())) {
            validateIndicatorKeyOfType(setting.getAtrKey(), IndicatorValue.Type.ATR,
                    indicatorTypes, path + ".atrKey", violations);
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

    private void validateSteps(StrategyDetailApiModel detail, String path,
                               Map<String, IndicatorValue.Type> indicatorTypes, Set<String> structureKeys,
                               List<String> violations) {
        Map<String, List<StrategyStepApiModel>> stepsByStatus = detail.getStepsByStatus();
        if (isNull(stepsByStatus)) {
            return;
        }
        Set<String> actionKeys = collectActionKeys(stepsByStatus, path, violations);
        stepsByStatus.forEach((status, steps) -> {
            validateEnum(Deal.Status.class, status, path + ".stepsByStatus key", violations);
            for (int index = 0; index < steps.size(); index++) {
                validateStep(steps.get(index), path + ".stepsByStatus[" + status + "][" + index + "]",
                        indicatorTypes, structureKeys, actionKeys, violations);
            }
        });
    }

    /** Ключ действия уникален в рамках детали (через все её шаги) — правила валидации 1-2. */
    private Set<String> collectActionKeys(Map<String, List<StrategyStepApiModel>> stepsByStatus,
                                          String path, List<String> violations) {
        Set<String> keys = new HashSet<>();
        stepsByStatus.values().forEach(steps -> steps.forEach(step -> {
            if (isNull(step.getActions())) {
                return;
            }
            step.getActions().forEach(action -> {
                if (nonNull(action.getKey()) && isFalse(keys.add(action.getKey()))) {
                    violations.add(path + ": duplicate action key " + action.getKey());
                }
            });
        }));
        return keys;
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

    private void validateRule(StrategyConditionRuleApiModel rule, String path,
                              Map<String, IndicatorValue.Type> indicatorTypes,
                              Set<String> structureKeys, List<String> violations) {
        validateEnum(StrategyConditionRuleType.class, rule.getRuleType(), path + ".ruleType", violations);
        if (nonNull(rule.getOperator())) {
            validateEnum(StrategyConditionOperator.class, rule.getOperator(), path + ".operator", violations);
        }
        if (nonNull(rule.getTimeframe())) {
            validateEnum(TimeFrame.class, rule.getTimeframe(), path + ".timeframe", violations);
        }
        validateOperand(rule.getLeftOperand(), path + ".leftOperand", indicatorTypes, structureKeys, violations);
        validateOperand(rule.getRightOperand(), path + ".rightOperand", indicatorTypes, structureKeys, violations);
        validateRuleContract(rule, path, violations);
    }

    /**
     * Минимальный per-ruleType контракт (инкрементальный: только типы,
     * нужные текущему авторингу; дозаполняется при реализации каждого
     * ruleType — strategy-condition-authoring-contract.md).
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
            case CANDLE_CLOSED -> {
                if (isNull(rule.getTimeframe())) {
                    violations.add(path + ": timeframe is required for CANDLE_CLOSED");
                }
            }
            case RANGE_BREAKOUT_CONFIRMED -> validateStructureOperandPresent(rule, path, violations);
            case MARKET_PHASE_IS -> validateMarketPhaseIs(rule, path, violations);
            case MARKET_STRUCTURE_IS -> validateMarketStructureIs(rule, path, violations);
            case INDICATOR_COMPARE -> validateComparing(rule, path,
                    StrategyConditionSourceType.INDICATOR, violations);
            case PRICE_COMPARE -> validateComparing(rule, path, StrategyConditionSourceType.PRICE, violations);
            case CROSSOVER -> validateCrossover(rule, path, violations);
            default -> {
            }
        }
    }

    /**
     * RANGE_BREAKOUT_CONFIRMED — структурно-событийное: ссылается на
     * MarketStructure операндом по structureKey (буфер/подтверждение —
     * params резолвера, не поле условия; событие пробоя читается готовым).
     */
    private void validateStructureOperandPresent(StrategyConditionRuleApiModel rule, String path,
                                                 List<String> violations) {
        Boolean hasStructure = hasOperandOfSource(rule, StrategyConditionSourceType.MARKET_STRUCTURE);
        if (isFalse(hasStructure)) {
            violations.add(path + ": " + rule.getRuleType()
                    + " requires a MARKET_STRUCTURE operand (structureKey)");
        }
    }

    /** MARKET_STRUCTURE_IS — зеркало MARKET_PHASE_IS: MARKET_STRUCTURE операнд vs CONSTANT ENUM MarketStructure.Type. */
    private void validateMarketStructureIs(StrategyConditionRuleApiModel rule, String path, List<String> violations) {
        if (isNull(rule.getOperator()) || isNull(rule.getLeftOperand()) || isNull(rule.getRightOperand())) {
            violations.add(path + ": MARKET_STRUCTURE_IS requires operator and both operands");
            return;
        }
        if (isFalse(hasOperandOfSource(rule, StrategyConditionSourceType.MARKET_STRUCTURE))) {
            violations.add(path + ": MARKET_STRUCTURE_IS requires a MARKET_STRUCTURE operand (structureKey)");
        }
        StrategyConditionOperandApiModel constant =
                constantOperand(rule.getLeftOperand(), rule.getRightOperand());
        if (isNull(constant)) {
            violations.add(path + ": MARKET_STRUCTURE_IS requires a CONSTANT operand with the structure type");
            return;
        }
        if (Objects.equals(constant.getValueType(), ConstantValueType.ENUM.name())
                && nonNull(constant.getValue())
                && isFalse(EnumUtils.isValidEnum(MarketStructure.Type.class, constant.getValue()))) {
            violations.add(path + ": unknown MarketStructure.Type " + constant.getValue());
        }
    }

    private Boolean hasOperandOfSource(StrategyConditionRuleApiModel rule, StrategyConditionSourceType source) {
        return (nonNull(rule.getLeftOperand())
                && Objects.equals(rule.getLeftOperand().getSourceType(), source.name()))
                || (nonNull(rule.getRightOperand())
                && Objects.equals(rule.getRightOperand().getSourceType(), source.name()));
    }

    private void validateMarketPhaseIs(StrategyConditionRuleApiModel rule, String path, List<String> violations) {
        if (isNull(rule.getOperator()) || isNull(rule.getLeftOperand()) || isNull(rule.getRightOperand())) {
            violations.add(path + ": MARKET_PHASE_IS requires operator and both operands");
            return;
        }
        StrategyConditionOperandApiModel constant =
                constantOperand(rule.getLeftOperand(), rule.getRightOperand());
        if (isNull(constant)) {
            violations.add(path + ": MARKET_PHASE_IS requires a CONSTANT operand with the phase");
            return;
        }
        if (Objects.equals(constant.getValueType(), ConstantValueType.ENUM.name())
                && nonNull(constant.getValue())
                && isFalse(EnumUtils.isValidEnum(MarketPhase.Type.class, constant.getValue()))) {
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
            case PRICE -> validateEnum(StrategyPriceSource.class, operand.getPriceSource(),
                    path + ".priceSource", violations);
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

    private void validateOrderAction(StrategyOrderActionApiModel action, String path,
                                     Map<String, IndicatorValue.Type> indicatorTypes,
                                     Set<String> structureKeys, List<String> violations) {
        validateEnum(Order.Type.class, action.getOrderType(), path + ".orderType", violations);
        validateEnum(StrategyTradeDirection.class, action.getDirection(), path + ".direction", violations);
        if (nonNull(action.getPlacement())) {
            validatePlacement(action, path + ".placement", structureKeys, violations);
        }
        if (nonNull(action.getAttachedProtection())) {
            validateEnum(AttachedAlgoOrder.Type.class, action.getAttachedProtection().getAttachedType(),
                    path + ".attachedProtection.attachedType", violations);
            validateStopLoss(action.getAttachedProtection().getStopLossSettings(),
                    path + ".attachedProtection.stopLossSettings", indicatorTypes, structureKeys, violations);
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
    }

    private void validateAlgoOrderAction(StrategyAlgoOrderActionApiModel action, String path,
                                         Map<String, IndicatorValue.Type> indicatorTypes, Set<String> structureKeys,
                                         List<String> violations) {
        validateEnum(AlgoOrder.ConditionType.class, action.getConditionType(), path + ".conditionType", violations);
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

    private void validateDuration(String value, String path, List<String> violations) {
        if (isNull(value)) {
            return;
        }
        try {
            Duration.parse(value);
        } catch (DateTimeParseException e) {
            violations.add(path + ": invalid ISO-8601 duration " + value);
        }
    }
}
