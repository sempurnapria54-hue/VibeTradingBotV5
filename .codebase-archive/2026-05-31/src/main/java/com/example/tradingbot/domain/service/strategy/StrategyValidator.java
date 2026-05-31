package com.example.tradingbot.domain.service.strategy;

import com.example.tradingbot.domain.model.core.algo_order.ConditionType;
import com.example.tradingbot.domain.model.core.deal.Deal;
import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.trade.market.MarketPhase;
import com.example.tradingbot.domain.model.trade.strategy.PhaseEntryPolicy;
import com.example.tradingbot.domain.model.trade.strategy.StopLossSettings;
import com.example.tradingbot.domain.model.trade.strategy.Strategy;
import com.example.tradingbot.domain.model.trade.strategy.StrategyAction;
import com.example.tradingbot.domain.model.trade.strategy.StrategyActionType;
import com.example.tradingbot.domain.model.trade.strategy.StrategyAlgoOrderAction;
import com.example.tradingbot.domain.model.trade.strategy.StrategyAttachedProtectionSettings;
import com.example.tradingbot.domain.model.trade.strategy.StrategyCondition;
import com.example.tradingbot.domain.model.trade.strategy.StrategyConditionRule;
import com.example.tradingbot.domain.model.trade.strategy.StrategyConditionRuleType;
import com.example.tradingbot.domain.model.trade.strategy.StrategyDetails;
import com.example.tradingbot.domain.model.trade.strategy.StrategyOrderAction;
import com.example.tradingbot.domain.model.trade.strategy.StrategyPositionAction;
import com.example.tradingbot.domain.model.trade.strategy.StrategyPriceBaseType;
import com.example.tradingbot.domain.model.trade.strategy.StrategyPricePlacement;
import com.example.tradingbot.domain.model.trade.strategy.StrategyStatus;
import com.example.tradingbot.domain.model.trade.strategy.StrategyStep;
import com.example.tradingbot.domain.model.trade.strategy.TrailingSettings;
import com.example.tradingbot.exception.StrategyConflictException;
import com.example.tradingbot.exception.StrategyValidationException;
import com.example.tradingbot.persistence.service.InstrumentDataService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.BooleanUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.example.tradingbot.util.Constant.ErrorCode.STRATEGY_REQUEST_INVALID;
import static com.example.tradingbot.util.Constant.ErrorCode.STRATEGY_STATUS_TRANSITION_NOT_ALLOWED;
import static com.example.tradingbot.util.Constant.ErrorCode.STRATEGY_VALIDATION_FAILED;

@Component
@RequiredArgsConstructor
public class StrategyValidator {

    private static final BigDecimal MAX_RISK_PER_TRADE_PERCENT = new BigDecimal("1.0");
    private static final BigDecimal MAX_PERCENT_VALUE = new BigDecimal("100");
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final int MAX_ALLOWED_LEVERAGE = 10;

    private final InstrumentDataService instrumentDataService;
    private final StrategyStatusResolver strategyStatusResolver;

    public void validateForCreate(Strategy strategy) {
        validateStrategy(strategy, HttpStatus.BAD_REQUEST, STRATEGY_REQUEST_INVALID);
    }

    public void validateForActivation(Strategy strategy) {
        validateStrategy(strategy, HttpStatus.UNPROCESSABLE_ENTITY, STRATEGY_VALIDATION_FAILED);
    }

    public void validateStatusTransition(Strategy strategy, StrategyStatus targetStatus) {
        StrategyStatus currentStatus = requireStrategyStatus(strategy);
        if (BooleanUtils.isFalse(strategyStatusResolver.canTransition(currentStatus, targetStatus))) {
            throw new StrategyConflictException(
                    STRATEGY_STATUS_TRANSITION_NOT_ALLOWED,
                    "Cannot change strategy status from "
                            + currentStatus
                            + " to "
                            + targetStatus
                            + " for internalId="
                            + strategy.getInternalId()
            );
        }
    }

    private void validateStrategy(Strategy strategy, HttpStatus httpStatus, String errorCode) {
        if (Objects.isNull(strategy)) {
            throw new StrategyValidationException(httpStatus, errorCode, "Strategy body is required");
        }

        requireNotNull(strategy.getInstrumentId(), "Strategy instrumentId is required", httpStatus, errorCode);
        requireInstrumentExists(strategy.getInstrumentId(), httpStatus, errorCode);
        requireText(strategy.getName(), "Strategy name is required", httpStatus, errorCode);
        requireNotNull(strategy.getVersion(), "Strategy version is required", httpStatus, errorCode);
        validateDetails(strategy.getDetails(), httpStatus, errorCode);
    }

    private void requireInstrumentExists(Long instrumentId, HttpStatus httpStatus, String errorCode) {
        if (BooleanUtils.isFalse(instrumentDataService.existsById(instrumentId))) {
            throw new StrategyValidationException(
                    httpStatus,
                    errorCode,
                    "Instrument not found: instrumentId=" + instrumentId
            );
        }
    }

    private void validateDetails(List<StrategyDetails> details, HttpStatus httpStatus, String errorCode) {
        if (org.springframework.util.CollectionUtils.isEmpty(details)) {
            throw new StrategyValidationException(httpStatus, errorCode, "Strategy details must not be empty");
        }

        Set<MarketPhase.Type> seenPhaseTypes = EnumSet.noneOf(MarketPhase.Type.class);
        for (StrategyDetails detail : details) {
            validateDetail(detail, seenPhaseTypes, httpStatus, errorCode);
        }
    }

    private void validateDetail(
            StrategyDetails detail,
            Set<MarketPhase.Type> seenPhaseTypes,
            HttpStatus httpStatus,
            String errorCode
    ) {
        if (Objects.isNull(detail)) {
            throw new StrategyValidationException(httpStatus, errorCode, "Strategy detail must not be null");
        }

        requireNotNull(detail.getMarketPhaseType(), "Strategy detail marketPhaseType is required", httpStatus, errorCode);
        if (BooleanUtils.isFalse(seenPhaseTypes.add(detail.getMarketPhaseType()))) {
            throw new StrategyValidationException(
                    httpStatus,
                    errorCode,
                    "Duplicate strategy detail for marketPhaseType=" + detail.getMarketPhaseType()
            );
        }

        requireNotNull(detail.getPhaseEntryPolicy(), "Strategy detail phaseEntryPolicy is required", httpStatus, errorCode);
        requireNotNull(
                detail.getRiskPerTradePercent(),
                "Strategy detail riskPerTradePercent is required",
                httpStatus,
                errorCode
        );
        requireNotNull(detail.getMaxLeverage(), "Strategy detail maxLeverage is required", httpStatus, errorCode);
        requireNotNull(
                detail.getTargetRiskRewardRatio(),
                "Strategy detail targetRiskRewardRatio is required",
                httpStatus,
                errorCode
        );

        validatePhaseEntryPolicyMatrix(detail.getMarketPhaseType(), detail.getPhaseEntryPolicy(), httpStatus, errorCode);
        validatePositivePercent(
                detail.getRiskPerTradePercent(),
                "Strategy detail riskPerTradePercent must be greater than 0",
                httpStatus,
                errorCode
        );
        if (detail.getRiskPerTradePercent().compareTo(MAX_RISK_PER_TRADE_PERCENT) > 0) {
            throw new StrategyValidationException(
                    httpStatus,
                    errorCode,
                    "Strategy detail riskPerTradePercent must not exceed 1.0"
            );
        }

        if (detail.getMaxLeverage() <= 0 || detail.getMaxLeverage() > MAX_ALLOWED_LEVERAGE) {
            throw new StrategyValidationException(
                    httpStatus,
                    errorCode,
                    "Strategy detail maxLeverage must be in range 1..10"
            );
        }

        if (detail.getTargetRiskRewardRatio().compareTo(ZERO) <= 0) {
            throw new StrategyValidationException(
                    httpStatus,
                    errorCode,
                    "Strategy detail targetRiskRewardRatio must be greater than 0"
            );
        }

        if (detail.isTradingEnabled() && org.springframework.util.CollectionUtils.isEmpty(detail.getStepsByStatus())) {
            throw new StrategyValidationException(
                    httpStatus,
                    errorCode,
                    "stepsByStatus must not be empty for trading detail marketPhaseType=" + detail.getMarketPhaseType()
            );
        }

        validateStepsByStatus(detail.getStepsByStatus(), detail.getMarketPhaseType(), httpStatus, errorCode);
    }

    private void validatePhaseEntryPolicyMatrix(
            MarketPhase.Type marketPhaseType,
            PhaseEntryPolicy phaseEntryPolicy,
            HttpStatus httpStatus,
            String errorCode
    ) {
        Set<PhaseEntryPolicy> allowedPolicies;
        switch (marketPhaseType) {
            case BULL_TREND, BEAR_TREND ->
                    allowedPolicies = EnumSet.of(
                            PhaseEntryPolicy.FOLLOW_PHASE,
                            PhaseEntryPolicy.CONTRARIAN,
                            PhaseEntryPolicy.NO_TRADE
                    );
            case RANGE -> allowedPolicies = EnumSet.of(PhaseEntryPolicy.GRID, PhaseEntryPolicy.NO_TRADE);
            case UNKNOWN -> allowedPolicies = EnumSet.of(PhaseEntryPolicy.NO_TRADE);
            default -> allowedPolicies = EnumSet.noneOf(PhaseEntryPolicy.class);
        }

        if (BooleanUtils.isFalse(allowedPolicies.contains(phaseEntryPolicy))) {
            throw new StrategyValidationException(
                    httpStatus,
                    errorCode,
                    "Unsupported phaseEntryPolicy="
                            + phaseEntryPolicy
                            + " for marketPhaseType="
                            + marketPhaseType
            );
        }
    }

    private void validateStepsByStatus(
            Map<Deal.Status, List<StrategyStep>> stepsByStatus,
            MarketPhase.Type marketPhaseType,
            HttpStatus httpStatus,
            String errorCode
    ) {
        if (Objects.isNull(stepsByStatus)) {
            return;
        }

        for (Map.Entry<Deal.Status, List<StrategyStep>> entry : stepsByStatus.entrySet()) {
            if (Objects.isNull(entry.getKey())) {
                throw new StrategyValidationException(
                        httpStatus,
                        errorCode,
                        "stepsByStatus contains null deal status for marketPhaseType=" + marketPhaseType
                );
            }

            if (org.springframework.util.CollectionUtils.isEmpty(entry.getValue())) {
                throw new StrategyValidationException(
                        httpStatus,
                        errorCode,
                        "stepsByStatus contains empty step list for status="
                                + entry.getKey()
                                + " and marketPhaseType="
                                + marketPhaseType
                );
            }

            for (StrategyStep step : entry.getValue()) {
                validateStep(step, entry.getKey(), marketPhaseType, httpStatus, errorCode);
            }
        }
    }

    private void validateStep(
            StrategyStep step,
            Deal.Status dealStatus,
            MarketPhase.Type marketPhaseType,
            HttpStatus httpStatus,
            String errorCode
    ) {
        if (Objects.isNull(step)) {
            throw new StrategyValidationException(
                    httpStatus,
                    errorCode,
                    "Strategy step must not be null for status=" + dealStatus
            );
        }

        requireNotNull(step.getStepType(), "Strategy stepType is required", httpStatus, errorCode);
        requireNotNull(step.getCondition(), "Strategy step condition is required", httpStatus, errorCode);
        validateCondition(step.getCondition(), dealStatus, marketPhaseType, httpStatus, errorCode);

        if (org.springframework.util.CollectionUtils.isEmpty(step.getActions())) {
            throw new StrategyValidationException(
                    httpStatus,
                    errorCode,
                    "Strategy step actions must not be empty for stepType=" + step.getStepType()
            );
        }

        validateActions(step.getActions(), step.getStepType().name(), httpStatus, errorCode);
    }

    private void validateCondition(
            StrategyCondition condition,
            Deal.Status dealStatus,
            MarketPhase.Type marketPhaseType,
            HttpStatus httpStatus,
            String errorCode
    ) {
        if (org.springframework.util.CollectionUtils.isEmpty(condition.getRules())) {
            throw new StrategyValidationException(
                    httpStatus,
                    errorCode,
                    "Strategy condition rules must not be empty for status="
                            + dealStatus
                            + " and marketPhaseType="
                            + marketPhaseType
            );
        }

        Set<Integer> levels = new LinkedHashSet<>();
        for (StrategyConditionRule rule : condition.getRules()) {
            validateConditionRule(rule, levels, httpStatus, errorCode);
        }
    }

    private void validateConditionRule(
            StrategyConditionRule rule,
            Set<Integer> levels,
            HttpStatus httpStatus,
            String errorCode
    ) {
        if (Objects.isNull(rule)) {
            throw new StrategyValidationException(httpStatus, errorCode, "Strategy condition rule must not be null");
        }

        requireNotNull(rule.getLevel(), "Strategy condition rule level is required", httpStatus, errorCode);
        if (BooleanUtils.isFalse(levels.add(rule.getLevel()))) {
            throw new StrategyValidationException(
                    httpStatus,
                    errorCode,
                    "Strategy condition rule level must be unique inside one condition: level=" + rule.getLevel()
            );
        }

        requireNotNull(rule.getRuleType(), "Strategy condition ruleType is required", httpStatus, errorCode);
        validateConditionRulePercents(rule, httpStatus, errorCode);
    }

    private void validateConditionRulePercents(
            StrategyConditionRule rule,
            HttpStatus httpStatus,
            String errorCode
    ) {
        Set<StrategyConditionRuleType> percentRuleTypes = EnumSet.of(
                StrategyConditionRuleType.PROFIT_PERCENTS_REACHED,
                StrategyConditionRuleType.LOSS_PERCENTS_REACHED,
                StrategyConditionRuleType.RANGE_BREAKOUT_CONFIRMED,
                StrategyConditionRuleType.EFFICIENCY_BELOW_THRESHOLD
        );

        if (percentRuleTypes.contains(rule.getRuleType())) {
            requireNotNull(
                    rule.getPercents(),
                    "Strategy condition rule percents is required for ruleType=" + rule.getRuleType(),
                    httpStatus,
                    errorCode
            );
            validatePositivePercent(
                    rule.getPercents(),
                    "Strategy condition rule percents must be greater than 0",
                    httpStatus,
                    errorCode
            );
        }
    }

    private void validateActions(
            List<StrategyAction> actions,
            String stepType,
            HttpStatus httpStatus,
            String errorCode
    ) {
        Map<Class<?>, Set<Integer>> levelsByActionClass = new LinkedHashMap<>();
        for (StrategyAction action : actions) {
            if (Objects.isNull(action)) {
                throw new StrategyValidationException(httpStatus, errorCode, "Strategy action must not be null");
            }

            Integer level = resolveActionLevel(action, httpStatus, errorCode);
            Set<Integer> levels = levelsByActionClass.computeIfAbsent(
                    action.getClass(),
                    key -> new LinkedHashSet<>()
            );
            if (BooleanUtils.isFalse(levels.add(level))) {
                throw new StrategyValidationException(
                        httpStatus,
                        errorCode,
                        "Strategy action level must be unique inside one step for actionType="
                                + action.getClass().getSimpleName()
                                + ", level="
                                + level
                                + ", stepType="
                                + stepType
                );
            }

            validateAction(action, httpStatus, errorCode);
        }
    }

    private Integer resolveActionLevel(
            StrategyAction action,
            HttpStatus httpStatus,
            String errorCode
    ) {
        if (action instanceof StrategyOrderAction orderAction) {
            requireNotNull(orderAction.getLevel(), "Strategy order action level is required", httpStatus, errorCode);
            return orderAction.getLevel();
        }

        if (action instanceof StrategyAlgoOrderAction algoOrderAction) {
            requireNotNull(algoOrderAction.getLevel(), "Strategy algo action level is required", httpStatus, errorCode);
            return algoOrderAction.getLevel();
        }

        if (action instanceof StrategyPositionAction positionAction) {
            requireNotNull(
                    positionAction.getLevel(),
                    "Strategy position action level is required",
                    httpStatus,
                    errorCode
            );
            return positionAction.getLevel();
        }

        throw new StrategyValidationException(
                httpStatus,
                errorCode,
                "Unsupported strategy action type: " + action.getClass().getName()
        );
    }

    private void validateAction(
            StrategyAction action,
            HttpStatus httpStatus,
            String errorCode
    ) {
        if (action instanceof StrategyOrderAction orderAction) {
            validateOrderAction(orderAction, httpStatus, errorCode);
            return;
        }

        if (action instanceof StrategyAlgoOrderAction algoOrderAction) {
            validateAlgoOrderAction(algoOrderAction, httpStatus, errorCode);
            return;
        }

        if (action instanceof StrategyPositionAction positionAction) {
            validatePositionAction(positionAction, httpStatus, errorCode);
            return;
        }

        throw new StrategyValidationException(
                httpStatus,
                errorCode,
                "Unsupported strategy action type: " + action.getClass().getName()
        );
    }

    private void validateOrderAction(
            StrategyOrderAction action,
            HttpStatus httpStatus,
            String errorCode
    ) {
        requireNotNull(action.getActionType(), "Strategy order actionType is required", httpStatus, errorCode);
        requireAllowedOrderActionType(action.getActionType(), httpStatus, errorCode);
        requireNotNull(action.getOrderType(), "Strategy orderType is required", httpStatus, errorCode);
        requireNotNull(action.getDirection(), "Strategy order direction is required", httpStatus, errorCode);

        if (Objects.nonNull(action.getAllocationPercents())) {
            validatePositivePercentRange(
                    action.getAllocationPercents(),
                    "Strategy order allocationPercents must be in range (0, 100]",
                    httpStatus,
                    errorCode
            );
        }

        if (Objects.equals(action.getActionType(), StrategyActionType.CREATE)
                && Objects.isNull(action.getAllocationPercents())) {
            throw new StrategyValidationException(
                    httpStatus,
                    errorCode,
                    "Strategy order allocationPercents is required for CREATE action"
            );
        }

        if (Objects.equals(action.getOrderType(), Order.Type.ENTRY_ATTACHED_STOP_LOSS)) {
            requireNotNull(
                    action.getAttachedProtection(),
                    "attachedProtection is required for ENTRY_ATTACHED_STOP_LOSS",
                    httpStatus,
                    errorCode
            );
            validateAttachedProtection(action.getAttachedProtection(), httpStatus, errorCode);
        }

        if (Objects.equals(action.getOrderType(), Order.Type.ENTRY) && Objects.nonNull(action.getAttachedProtection())) {
            throw new StrategyValidationException(
                    httpStatus,
                    errorCode,
                    "attachedProtection must be null for ENTRY orderType"
            );
        }

        if (Objects.nonNull(action.getPlacement())) {
            validatePricePlacement(action.getPlacement(), httpStatus, errorCode);
        }
    }

    private void requireAllowedOrderActionType(
            StrategyActionType actionType,
            HttpStatus httpStatus,
            String errorCode
    ) {
        Set<StrategyActionType> allowedTypes = EnumSet.of(
                StrategyActionType.CREATE,
                StrategyActionType.AMEND,
                StrategyActionType.CANCEL
        );
        if (BooleanUtils.isFalse(allowedTypes.contains(actionType))) {
            throw new StrategyValidationException(
                    httpStatus,
                    errorCode,
                    "StrategyOrderAction supports only CREATE, AMEND, CANCEL"
            );
        }
    }

    private void validateAlgoOrderAction(
            StrategyAlgoOrderAction action,
            HttpStatus httpStatus,
            String errorCode
    ) {
        requireNotNull(action.getActionType(), "Strategy algo actionType is required", httpStatus, errorCode);
        requireAllowedAlgoActionType(action.getActionType(), httpStatus, errorCode);
        requireNotNull(action.getConditionType(), "Strategy algo conditionType is required", httpStatus, errorCode);

        if (Objects.nonNull(action.getStopLossSettings())) {
            validateStopLossSettings(action.getStopLossSettings(), httpStatus, errorCode);
        }

        if (Objects.nonNull(action.getTrailingSettings())) {
            validateTrailingSettings(action.getTrailingSettings(), httpStatus, errorCode);
        }

        if (Objects.nonNull(action.getCloseFractionPercents())) {
            validatePositivePercentRange(
                    action.getCloseFractionPercents(),
                    "Strategy algo closeFractionPercents must be in range (0, 100]",
                    httpStatus,
                    errorCode
            );
        }

        if (Objects.nonNull(action.getTriggerProfitPercents())) {
            validatePositivePercent(
                    action.getTriggerProfitPercents(),
                    "Strategy algo triggerProfitPercents must be greater than 0",
                    httpStatus,
                    errorCode
            );
        }

        validateAlgoConditionRequirements(action, httpStatus, errorCode);
    }

    private void requireAllowedAlgoActionType(
            StrategyActionType actionType,
            HttpStatus httpStatus,
            String errorCode
    ) {
        Set<StrategyActionType> allowedTypes = EnumSet.of(
                StrategyActionType.CREATE,
                StrategyActionType.AMEND,
                StrategyActionType.CANCEL
        );
        if (BooleanUtils.isFalse(allowedTypes.contains(actionType))) {
            throw new StrategyValidationException(
                    httpStatus,
                    errorCode,
                    "StrategyAlgoOrderAction supports only CREATE, AMEND, CANCEL"
            );
        }
    }

    private void validateAlgoConditionRequirements(
            StrategyAlgoOrderAction action,
            HttpStatus httpStatus,
            String errorCode
    ) {
        ConditionType conditionType = action.getConditionType();

        if (Objects.equals(conditionType, ConditionType.STOP_LOSS)
                || Objects.equals(conditionType, ConditionType.PARTIAL_STOP_LOSS)
                || Objects.equals(conditionType, ConditionType.OCO_FULL)) {
            requireNotNull(
                    action.getStopLossSettings(),
                    "stopLossSettings is required for conditionType=" + conditionType,
                    httpStatus,
                    errorCode
            );
        }

        if (Objects.equals(conditionType, ConditionType.TAKE_PROFIT)
                || Objects.equals(conditionType, ConditionType.PARTIAL_TAKE_PROFIT)
                || Objects.equals(conditionType, ConditionType.OCO_FULL)) {
            requireNotNull(
                    action.getTriggerProfitPercents(),
                    "triggerProfitPercents is required for conditionType=" + conditionType,
                    httpStatus,
                    errorCode
            );
            requireNotNull(
                    action.getTriggerPriceType(),
                    "triggerPriceType is required for conditionType=" + conditionType,
                    httpStatus,
                    errorCode
            );
        }

        if (Objects.equals(conditionType, ConditionType.PARTIAL_STOP_LOSS)
                || Objects.equals(conditionType, ConditionType.PARTIAL_TAKE_PROFIT)
                || Objects.equals(conditionType, ConditionType.OCO_FULL)) {
            requireNotNull(
                    action.getCloseFractionPercents(),
                    "closeFractionPercents is required for conditionType=" + conditionType,
                    httpStatus,
                    errorCode
            );
        }

        if (Objects.equals(conditionType, ConditionType.TRAILING_PERCENTS)) {
            requireNotNull(
                    action.getTrailingSettings(),
                    "trailingSettings is required for conditionType=TRAILING_PERCENTS",
                    httpStatus,
                    errorCode
            );
        }

        if (Objects.equals(conditionType, ConditionType.TRAILING_VALUE)) {
            throw new StrategyValidationException(
                    httpStatus,
                    errorCode,
                    "conditionType=TRAILING_VALUE is not supported by Strategy.md model"
            );
        }
    }

    private void validatePositionAction(
            StrategyPositionAction action,
            HttpStatus httpStatus,
            String errorCode
    ) {
        requireNotNull(action.getActionType(), "Strategy position actionType is required", httpStatus, errorCode);
        Set<StrategyActionType> allowedTypes = EnumSet.of(
                StrategyActionType.CLOSE_FULL,
                StrategyActionType.CLOSE_PARTIAL
        );
        if (BooleanUtils.isFalse(allowedTypes.contains(action.getActionType()))) {
            throw new StrategyValidationException(
                    httpStatus,
                    errorCode,
                    "StrategyPositionAction supports only CLOSE_FULL and CLOSE_PARTIAL"
            );
        }

        if (Objects.nonNull(action.getCloseFractionPercents())) {
            validatePositivePercentRange(
                    action.getCloseFractionPercents(),
                    "Strategy position closeFractionPercents must be in range (0, 100]",
                    httpStatus,
                    errorCode
            );
        }

        if (Objects.equals(action.getActionType(), StrategyActionType.CLOSE_PARTIAL)
                && Objects.isNull(action.getCloseFractionPercents())) {
            throw new StrategyValidationException(
                    httpStatus,
                    errorCode,
                    "closeFractionPercents is required for CLOSE_PARTIAL"
            );
        }
    }

    private void validateAttachedProtection(
            StrategyAttachedProtectionSettings attachedProtection,
            HttpStatus httpStatus,
            String errorCode
    ) {
        requireNotNull(
                attachedProtection.getAttachedType(),
                "attachedProtection.attachedType is required",
                httpStatus,
                errorCode
        );
        if (BooleanUtils.isFalse(
                Objects.equals(attachedProtection.getAttachedType(), AttachedAlgoOrder.Type.ATTACHED_STOP_LOSS)
        )) {
            throw new StrategyValidationException(
                    httpStatus,
                    errorCode,
                    "Only ATTACHED_STOP_LOSS is supported for attachedProtection.attachedType"
            );
        }

        requireNotNull(
                attachedProtection.getStopLossSettings(),
                "attachedProtection.stopLossSettings is required",
                httpStatus,
                errorCode
        );
        validateStopLossSettings(attachedProtection.getStopLossSettings(), httpStatus, errorCode);
    }

    private void validatePricePlacement(
            StrategyPricePlacement placement,
            HttpStatus httpStatus,
            String errorCode
    ) {
        requireNotNull(placement.getBaseType(), "Strategy placement.baseType is required", httpStatus, errorCode);
        requireNotNull(placement.getOffsetSide(), "Strategy placement.offsetSide is required", httpStatus, errorCode);
        requireNotNull(placement.getPercents(), "Strategy placement.percents is required", httpStatus, errorCode);
        validatePositivePercent(
                placement.getPercents(),
                "Strategy placement.percents must be greater than 0",
                httpStatus,
                errorCode
        );

        if (Objects.equals(placement.getBaseType(), StrategyPriceBaseType.MARKET_PRICE)
                && Objects.isNull(placement.getMarketPriceType())) {
            throw new StrategyValidationException(
                    httpStatus,
                    errorCode,
                    "marketPriceType is required for baseType=MARKET_PRICE"
            );
        }

        if (BooleanUtils.isFalse(Objects.equals(placement.getBaseType(), StrategyPriceBaseType.MARKET_PRICE))
                && Objects.nonNull(placement.getMarketPriceType())) {
            throw new StrategyValidationException(
                    httpStatus,
                    errorCode,
                    "marketPriceType must be null for baseType=" + placement.getBaseType()
            );
        }
    }

    private void validateStopLossSettings(
            StopLossSettings stopLossSettings,
            HttpStatus httpStatus,
            String errorCode
    ) {
        requireText(
                stopLossSettings.getCalculationType(),
                "stopLossSettings.calculationType is required",
                httpStatus,
                errorCode
        );
        requireNotNull(
                stopLossSettings.getDistancePercents(),
                "stopLossSettings.distancePercents is required",
                httpStatus,
                errorCode
        );
        validatePositivePercent(
                stopLossSettings.getDistancePercents(),
                "stopLossSettings.distancePercents must be greater than 0",
                httpStatus,
                errorCode
        );
        requireNotNull(
                stopLossSettings.getTriggerPriceType(),
                "stopLossSettings.triggerPriceType is required",
                httpStatus,
                errorCode
        );
    }

    private void validateTrailingSettings(
            TrailingSettings trailingSettings,
            HttpStatus httpStatus,
            String errorCode
    ) {
        requireNotNull(
                trailingSettings.getCallbackPercents(),
                "trailingSettings.callbackPercents is required",
                httpStatus,
                errorCode
        );
        validatePositivePercent(
                trailingSettings.getCallbackPercents(),
                "trailingSettings.callbackPercents must be greater than 0",
                httpStatus,
                errorCode
        );

        if (Objects.nonNull(trailingSettings.getActivationProfitPercents())) {
            validatePositivePercent(
                    trailingSettings.getActivationProfitPercents(),
                    "trailingSettings.activationProfitPercents must be greater than 0",
                    httpStatus,
                    errorCode
            );
        }

        if (Objects.nonNull(trailingSettings.getActivationBufferPercents())
                && trailingSettings.getActivationBufferPercents().compareTo(ZERO) < 0) {
            throw new StrategyValidationException(
                    httpStatus,
                    errorCode,
                    "trailingSettings.activationBufferPercents must not be negative"
            );
        }
    }

    private void validatePositivePercent(
            BigDecimal value,
            String message,
            HttpStatus httpStatus,
            String errorCode
    ) {
        if (value.compareTo(ZERO) <= 0) {
            throw new StrategyValidationException(httpStatus, errorCode, message);
        }
    }

    private void validatePositivePercentRange(
            BigDecimal value,
            String message,
            HttpStatus httpStatus,
            String errorCode
    ) {
        if (value.compareTo(ZERO) <= 0 || value.compareTo(MAX_PERCENT_VALUE) > 0) {
            throw new StrategyValidationException(httpStatus, errorCode, message);
        }
    }

    private void requireText(
            String value,
            String message,
            HttpStatus httpStatus,
            String errorCode
    ) {
        if (BooleanUtils.isFalse(StringUtils.hasText(value))) {
            throw new StrategyValidationException(httpStatus, errorCode, message);
        }
    }

    private void requireNotNull(
            Object value,
            String message,
            HttpStatus httpStatus,
            String errorCode
    ) {
        if (Objects.isNull(value)) {
            throw new StrategyValidationException(httpStatus, errorCode, message);
        }
    }

    private StrategyStatus requireStrategyStatus(Strategy strategy) {
        if (Objects.isNull(strategy)) {
            throw new StrategyConflictException(
                    STRATEGY_STATUS_TRANSITION_NOT_ALLOWED,
                    "Strategy is required for status transition"
            );
        }

        if (Objects.isNull(strategy.getStatus())) {
            throw new StrategyConflictException(
                    STRATEGY_STATUS_TRANSITION_NOT_ALLOWED,
                    "Strategy status is required for internalId=" + strategy.getInternalId()
            );
        }

        return strategy.getStatus();
    }
}
