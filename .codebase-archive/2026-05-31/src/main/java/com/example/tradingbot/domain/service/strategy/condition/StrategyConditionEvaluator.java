package com.example.tradingbot.domain.service.strategy.condition;

import com.example.tradingbot.domain.model.trade.strategy.StrategyCondition;
import com.example.tradingbot.domain.model.trade.strategy.StrategyConditionRule;
import com.example.tradingbot.domain.model.trade.strategy.StrategyConditionRuleType;
import com.example.tradingbot.domain.service.deal.state_machine.DealContext;
import org.apache.commons.lang3.BooleanUtils;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import static com.example.tradingbot.util.CollectionUtils.emptyIfNull;

@Service
public class StrategyConditionEvaluator {

    public boolean evaluate(StrategyCondition condition, DealContext context) {
        if (Objects.isNull(condition)) {
            return true;
        }

        List<StrategyConditionRule> rules = emptyIfNull(condition.getRules()).stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(StrategyConditionRule::getLevel,
                                             Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        if (rules.isEmpty()) {
            return true;
        }

        for (StrategyConditionRule rule : rules) {
            if (BooleanUtils.isFalse(evaluateRule(rule, context))) {
                return false;
            }
        }

        return true;
    }

    private boolean evaluateRule(StrategyConditionRule rule, DealContext context) {
        StrategyConditionRuleType ruleType = rule.getRuleType();
        if (Objects.isNull(ruleType)) {
            return false;
        }

        return switch (ruleType) {
            case NO_OPEN_POSITION -> BooleanUtils.isFalse(context.hasActivePosition());
            case ENTRY_ORDER_FINALIZED -> context.isEntryOrderFinalized();
            case POSITION_OPENED -> context.hasActivePosition();
            case ATTACHED_STOP_LOSS_EXISTS -> context.hasAttachedProtection();
            case MAIN_PROTECTION_EXISTS -> context.hasMainProtection();
            case PROFIT_PERCENTS_REACHED,
                 LOSS_PERCENTS_REACHED,
                 RANGE_BREAKOUT_CONFIRMED,
                 TREND_CHANGED,
                 EFFICIENCY_BELOW_THRESHOLD -> false;
        };
    }
}
