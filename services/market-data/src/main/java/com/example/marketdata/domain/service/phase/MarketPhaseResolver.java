package com.example.marketdata.domain.service.phase;

import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.model.aggregate.strategy.setting.StrategyMarketPhaseRule;
import com.example.tradingbot.domain.model.trade.market_phase.MarketPhase;
import com.example.strategy.engine.condition.ConditionEvaluationContext;
import com.example.strategy.engine.condition.StrategyConditionEvaluator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Резолвит тип фазы из авторских правил (StrategyMarketPhaseRule) поверх
 * StrategyConditionEvaluator: stateless first-match по позиции клаузы в
 * списке (первая клауза с истинным condition задаёт тип; ни одна →
 * UNKNOWN — консервативный дефолт). Сам по свечам ничего не считает и не
 * персистит — фаза вычисляется на лету при потреблении (MarketPhaseService).
 * Свежесть наследуется от входов: устаревший индикатор/структура не попадает
 * в контекст → операнд недоступен → клауза ложна → UNKNOWN. См.
 * docs/components/MarketPhaseResolver.md,
 * docs/components/MarketPhaseService.md.
 */
@Service
@RequiredArgsConstructor
public class MarketPhaseResolver {

    private final StrategyConditionEvaluator conditionEvaluator;

    /** First-match по позиции в списке; ни одна клауза не истинна → UNKNOWN. */
    public MarketPhase.Type resolve(List<StrategyMarketPhaseRule> phaseRules, ConditionEvaluationContext context) {
        if (isEmpty(phaseRules)) {
            return MarketPhase.Type.UNKNOWN;
        }
        return phaseRules.stream()
                .filter(rule -> isTrue(conditionEvaluator.evaluate(rule.getCondition(), context)))
                .findFirst()
                .map(StrategyMarketPhaseRule::getType)
                .orElse(MarketPhase.Type.UNKNOWN);
    }
}
