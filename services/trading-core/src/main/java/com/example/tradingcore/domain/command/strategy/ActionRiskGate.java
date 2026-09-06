package com.example.tradingcore.domain.command.strategy;

import static java.util.Objects.isNull;

import com.example.strategy.engine.calc.CalculatedStrategyAction;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.risk.RiskBlockAction;
import com.example.tradingcore.domain.command.risk.RiskBlockResolver;
import com.example.tradingcore.domain.command.risk.RiskValidationResult;
import com.example.tradingcore.domain.command.risk.RiskValidator;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Тонкий узел между преконтролем риска и планом действия
 * (docs/components/ActionRiskGate.md): прогоняет вердикт и, если реакция
 * блокирующая, отдаёт её планом.
 *
 * <p><b>Разрешающие реакции плана не порождают</b> — действие продолжает
 * свой ход. Поэтому исход пустой, а не «план с {@code CONTINUE}»: план
 * есть указание обработчику, и указания «ничего не делай» у него нет.
 *
 * <p><b>Почему узел отдельный.</b> Связка «валидатор → резолвер → план»
 * одна и та же у всех валидируемых действий, а её копия у каждого
 * исполнителя расходилась бы первой же правкой карты реакции: сегодня
 * разрешающими считаются две реакции из шести, и перечень принадлежит
 * карте, а не исполнителю.
 *
 * <p><b>Решений узел не принимает:</b> состав проверок держит
 * {@link RiskValidator}, род реакции — {@link RiskBlockResolver}; статусов
 * не пишет и команд не создаёт.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActionRiskGate {

    /** Разрешающие реакции: действие продолжает ход, плана не возникает. */
    private static final Set<RiskBlockAction.Type> PERMISSIVE = EnumSet.of(
            RiskBlockAction.Type.CONTINUE, RiskBlockAction.Type.CONTINUE_WITH_WARNING);

    private final RiskValidator riskValidator;
    private final RiskBlockResolver riskBlockResolver;

    /**
     * Ветвь рассчитанного действия: вход, добор, замещение с увеличением,
     * создание и перенос защиты (docs/rules/risk-validator-scope.md).
     */
    public Optional<ActionPlan> gate(CalculatedStrategyAction calculatedAction, DealContext dealContext,
                                     DealTranche tranche) {
        return plan(riskValidator.validate(calculatedAction, dealContext), dealContext, tranche);
    }

    /**
     * Ветвь снятия отдельной защиты при живой экспозиции: снятие риск не
     * снимает, а увеличивает (docs/rules/live-risk-protection.md §«Снятие
     * защиты — риск-увеличивающее действие»).
     */
    public Optional<ActionPlan> gateProtectionRemoval(DealContext dealContext, DealTranche tranche,
                                                      Long algoOrderId) {
        return plan(riskValidator.validateProtectionRemoval(dealContext, tranche, algoOrderId),
                dealContext, tranche);
    }

    private Optional<ActionPlan> plan(RiskValidationResult result, DealContext dealContext, DealTranche tranche) {
        DealTranche.Status status = isNull(tranche) ? null : tranche.getStatus();
        RiskBlockAction reaction = riskBlockResolver.resolve(dealContext, status, result);
        if (PERMISSIVE.contains(reaction.getType())) {
            logWarning(reaction);
            return Optional.empty();
        }
        return Optional.of(ActionPlan.blocked(reaction));
    }

    /**
     * Предупреждение сохраняется в журнале — оно и есть весь эффект
     * разрешающей реакции с предупреждением: продолжить действие, но
     * оставить след.
     */
    private void logWarning(RiskBlockAction reaction) {
        if (RiskBlockAction.Type.CONTINUE_WITH_WARNING.equals(reaction.getType())) {
            log.warn("Risk precheck warning: {}", reaction.getComment());
        }
    }
}
