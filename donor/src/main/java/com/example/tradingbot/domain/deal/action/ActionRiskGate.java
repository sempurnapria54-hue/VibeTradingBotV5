package com.example.tradingbot.domain.deal.action;

import static org.apache.commons.lang3.BooleanUtils.isFalse;

import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.command.calc.CalculatedStrategyAction;
import com.example.tradingbot.domain.command.risk.RiskBlockAction;
import com.example.tradingbot.domain.command.risk.RiskBlockResolver;
import com.example.tradingbot.domain.command.risk.RiskValidationResult;
import com.example.tradingbot.domain.command.risk.RiskValidator;
import com.example.tradingbot.domain.deal.ActionPlan;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Преконтроль действия для per-type исполнителей: прогоняет
 * {@link RiskValidator} и переводит вердикт в реакцию
 * {@link RiskBlockResolver}. Заведён потому, что связка «валидатор →
 * резолвер → план» одна и та же у всех валидируемых действий
 * (docs/rules/risk-validator-scope.md), а её копия у каждого исполнителя
 * расходилась бы первой же правкой карты реакции.
 *
 * <p>Сам решений не принимает: род реакции даёт резолвер, состав проверок
 * — валидатор. См. docs/processes/risk-evaluation.md.
 */
@Component
@RequiredArgsConstructor
public class ActionRiskGate {

    private final RiskValidator riskValidator;
    private final RiskBlockResolver riskBlockResolver;

    /**
     * Преконтроль рассчитанного действия, создающего либо ослабляющего
     * контроль риска. {@code null} — риск разрешил продолжить; иначе
     * блокирующая реакция планом.
     */
    public ActionPlan blockingPlan(CalculatedStrategyAction calculated, DealContext dealContext,
                                   DealTranche tranche) {
        return toPlan(riskValidator.validate(calculated, dealContext), dealContext, tranche);
    }

    /**
     * Преконтроль СНЯТИЯ отдельной защиты при живой экспозиции — ветка
     * ослабления защиты (docs/rules/live-risk-protection.md §«Снятие
     * защиты — риск-увеличивающее действие»). {@code null} — снятие
     * законно.
     */
    public ActionPlan removalBlockingPlan(DealTranche tranche, Long algoOrderId, DealContext dealContext) {
        return toPlan(riskValidator.validateProtectionRemoval(tranche, algoOrderId), dealContext, tranche);
    }

    private ActionPlan toPlan(RiskValidationResult risk, DealContext dealContext, DealTranche tranche) {
        if (RiskValidationResult.RiskDecision.ALLOWED.equals(risk.getDecision())) {
            return null;
        }
        RiskBlockAction blockAction = riskBlockResolver.resolve(dealContext, tranche.getStatus(), risk);
        return isBlocking(blockAction.getType()) ? ActionPlan.blocked(blockAction) : null;
    }

    /** Продолжающие реакции (CONTINUE / CONTINUE_WITH_WARNING) действие не блокируют. */
    private boolean isBlocking(RiskBlockAction.Type type) {
        return isFalse(RiskBlockAction.Type.CONTINUE.equals(type))
                && isFalse(RiskBlockAction.Type.CONTINUE_WITH_WARNING.equals(type));
    }
}
