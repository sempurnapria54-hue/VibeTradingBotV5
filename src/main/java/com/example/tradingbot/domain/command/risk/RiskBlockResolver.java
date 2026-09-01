package com.example.tradingbot.domain.command.risk;

import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.command.RuntimeErrorCode;
import com.example.tradingbot.domain.command.risk.RiskCheckResult.RiskCheckCode;
import com.example.tradingbot.domain.command.risk.RiskCheckResult.RiskCheckStatus;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.core.position.Position;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Превращает результат risk-проверки в действие handler'а, чтобы handler
 * не содержал большой switch по всем risk-кодам:
 * RiskValidationResult → {@link RiskBlockAction}
 * (docs/components/RiskBlockResolver.md). Сам команды не исполняет;
 * политику маппинга кодов риска в действие держит здесь, исполнение — у FSM
 * handler. {@code liveRiskExists} — производное состояние (DealContext +
 * currentStatus + runtime graph), отдельным параметром не передаётся.
 */
@Component
public class RiskBlockResolver {

    private static final Set<DealTranche.Status> LIVE_RISK_STATUSES = EnumSet.of(
            DealTranche.Status.ENTRY_SUBMITTED,
            DealTranche.Status.ENTRY_FINALIZED,
            DealTranche.Status.PROTECTION_SWITCHED,
            DealTranche.Status.MANAGING,
            DealTranche.Status.EXIT_PENDING);

    public RiskBlockAction resolve(DealContext dealContext, DealTranche.Status currentStatus,
                                   RiskValidationResult riskValidationResult) {
        return switch (riskValidationResult.getDecision()) {
            case ALLOWED -> RiskBlockAction.builder()
                    .type(RiskBlockAction.Type.CONTINUE)
                    .comment("risk allowed")
                    .build();
            case WARNING -> RiskBlockAction.builder()
                    .type(RiskBlockAction.Type.CONTINUE_WITH_WARNING)
                    .comment(riskValidationResult.getComment())
                    .build();
            case BLOCKED -> resolveBlocked(dealContext, currentStatus, riskValidationResult);
        };
    }

    private RiskBlockAction resolveBlocked(DealContext dealContext, DealTranche.Status currentStatus,
                                           RiskValidationResult result) {
        if (hasBlockingCode(result, RiskCheckCode.BALANCE_NOT_FRESH)
                || hasBlockingCode(result, RiskCheckCode.BALANCE_INVALID)) {
            return RiskBlockAction.builder()
                    .type(RiskBlockAction.Type.REQUEST_REFRESH)
                    .comment("balance not fresh/invalid; refresh required")
                    .build();
        }
        if (isTrue(liveRiskExists(dealContext, currentStatus))) {
            return RiskBlockAction.builder()
                    .type(RiskBlockAction.Type.MOVE_DEAL_TO_ERROR)
                    .errorCode(RuntimeErrorCode.VALIDATION_ERROR)
                    .comment("risk blocked with live risk present: " + result.getComment())
                    .build();
        }
        return RiskBlockAction.builder()
                .type(RiskBlockAction.Type.CLOSE_CANDIDATE_DEAL)
                .closeReason(Deal.CloseReason.RISK_CONTROL)
                .comment("risk blocked before live risk: " + result.getComment())
                .build();
    }

    private Boolean liveRiskExists(DealContext dealContext, DealTranche.Status currentStatus) {
        Deal deal = dealContext.getDeal();
        Position position = deal.getPosition();
        if (Objects.nonNull(position) && isTrue(position.hasLiveRisk())) {
            return Boolean.TRUE;
        }
        if (LIVE_RISK_STATUSES.contains(currentStatus)) {
            return Boolean.TRUE;
        }
        if (isNotEmpty(deal.getOrders()) && deal.getOrders().stream().anyMatch(order -> isTrue(order.isLive()))) {
            return Boolean.TRUE;
        }
        return isNotEmpty(deal.getAlgoOrders())
                && deal.getAlgoOrders().stream().anyMatch(algo -> isTrue(algo.isLive()));
    }

    private boolean hasBlockingCode(RiskValidationResult result, RiskCheckCode code) {
        return result.getChecks().stream()
                .anyMatch(check -> Objects.equals(check.getCode(), code)
                        && RiskCheckStatus.BLOCKED.equals(check.getStatus()));
    }
}
