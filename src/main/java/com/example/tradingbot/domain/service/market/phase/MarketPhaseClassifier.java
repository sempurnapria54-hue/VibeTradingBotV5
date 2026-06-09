package com.example.tradingbot.domain.service.market.phase;

import static java.util.Comparator.comparing;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.model.aggregate.strategy.setting.StrategyMarketPhaseRule;
import com.example.tradingbot.domain.model.trade.market_phase.MarketPhase;
import com.example.tradingbot.domain.service.market.condition.ConditionEvaluationContext;
import com.example.tradingbot.domain.service.market.condition.StrategyConditionEvaluator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Исполняет авторские правила определения фазы (StrategyMarketPhaseRule)
 * и возвращает тип фазы: stateless first-match по level ASC поверх
 * StrategyConditionEvaluator (первая клауза с истинным condition задаёт
 * тип; не сработала ни одна → UNKNOWN). confirmedAt — производный от
 * гейт-операндов сработавшей клаузы. Сам по свечам ничего не считает и
 * не персистит. См. docs/components/MarketPhaseClassifier.md,
 * docs/decisions/market-phase-conditional-classification.md.
 */
@Service
@RequiredArgsConstructor
public class MarketPhaseClassifier {

    private final StrategyConditionEvaluator conditionEvaluator;

    /** First-match по level ASC; ни одна клауза не истинна → UNKNOWN (консервативный дефолт). */
    public MarketPhaseClassification classify(List<StrategyMarketPhaseRule> phaseRules,
                                              ConditionEvaluationContext context) {
        if (isEmpty(phaseRules)) {
            return new MarketPhaseClassification(MarketPhase.Type.UNKNOWN, context.getEvaluationTime());
        }
        return phaseRules.stream()
                .sorted(comparing(StrategyMarketPhaseRule::getLevel))
                .filter(rule -> isTrue(conditionEvaluator.evaluate(rule.getCondition(), context)))
                .findFirst()
                .map(rule -> new MarketPhaseClassification(rule.getType(),
                        conditionEvaluator.deriveConfirmedAt(rule.getCondition(), context)))
                .orElseGet(() -> new MarketPhaseClassification(MarketPhase.Type.UNKNOWN, context.getEvaluationTime()));
    }
}
