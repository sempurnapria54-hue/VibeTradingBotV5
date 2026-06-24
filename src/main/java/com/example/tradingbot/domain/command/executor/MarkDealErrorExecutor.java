package com.example.tradingbot.domain.command.executor;

import static org.apache.commons.lang3.BooleanUtils.isFalse;

import com.example.tradingbot.domain.command.DealActionState;
import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.command.DealFinalizationState;
import com.example.tradingbot.domain.command.DealFinalizationStateStatus;
import com.example.tradingbot.domain.command.DealFinalizationType;
import com.example.tradingbot.domain.command.ServiceCommand;
import com.example.tradingbot.domain.command.ServiceCommandExecutionResult;
import com.example.tradingbot.domain.command.ServiceCommandType;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.persistence.service.DealDataService;
import com.example.tradingbot.persistence.service.DealFinalizationStateDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Исполняет MARK_DEAL_ERROR — пометку ошибочного состояния сделки.
 * Пишет Deal.status = ERROR (non-terminal runtime status для
 * ErrorHandler/safety-flow). Сам терминал не ставит — дальнейший разбор и
 * переход в EMERGENCY_CLOSED ведёт ErrorHandler. Торговых решений не
 * принимает, RiskValidator не вызывает. Retry-anchor —
 * DealFinalizationState(deal, MARK_ERROR); идемпотентность — повтор на
 * уже ERROR-сделке → no-op → COMPLETED. См.
 * docs/components/MarkDealErrorExecutor.md.
 */
@Component
@RequiredArgsConstructor
public class MarkDealErrorExecutor implements CommandExecutor {

    private final DealFinalizationStateDataService finalizationStateDataService;
    private final DealDataService dealDataService;

    @Override
    public ServiceCommandType supportedType() {
        return ServiceCommandType.MARK_DEAL_ERROR;
    }

    @Override
    @Transactional
    public ServiceCommandExecutionResult execute(ServiceCommand command, DealActionState actionState,
                                                 DealContext dealContext) {
        DealFinalizationState state = finalizationStateDataService
                .findByDealIdAndType(command.getDealId(), DealFinalizationType.MARK_ERROR)
                .orElseThrow(() -> new IllegalStateException(
                        "DealFinalizationState(MARK_ERROR) not found dealId=" + command.getDealId()));
        Deal deal = dealContext.getDeal();
        if (DealFinalizationStateStatus.COMPLETED.equals(state.getStatus())) {
            return ServiceCommandExecutionResult.ok();
        }
        if (isFalse(Deal.Status.ERROR.equals(deal.getStatus()))) {
            deal.setStatus(Deal.Status.ERROR);
            dealDataService.save(deal);
        }
        state.setStatus(DealFinalizationStateStatus.COMPLETED);
        finalizationStateDataService.save(state);
        return ServiceCommandExecutionResult.ok();
    }
}
