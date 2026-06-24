package com.example.tradingbot.domain.command.executor;

import com.example.tradingbot.domain.command.DealActionState;
import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.command.DealFinalizationState;
import com.example.tradingbot.domain.command.DealFinalizationStateStatus;
import com.example.tradingbot.domain.command.DealFinalizationType;
import com.example.tradingbot.domain.command.ServiceCommand;
import com.example.tradingbot.domain.command.ServiceCommandExecutionResult;
import com.example.tradingbot.domain.command.ServiceCommandType;
import com.example.tradingbot.persistence.service.DealFinalizationStateDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Исполняет FINALIZE_DEAL_EXIT — консолидацию фактов штатного выхода после
 * снятия live risk, готовит сделку к терминальному MARK_DEAL_CLOSED.
 * Опирается на уже добытые факты, на биржу не ходит, RiskValidator не
 * вызывает. Расчёт Deal.resultProfit сюда НЕ входит — он отнесён к шагу 7
 * (граница 6 ↔ 7); здесь только механика финализации. Статус Deal сам не
 * двигает. Retry-anchor — DealFinalizationState(deal, FINALIZE_EXIT);
 * идемпотентность — UNIQUE(deal_id, type). См.
 * docs/components/FinalizeDealExitExecutor.md.
 */
@Component
@RequiredArgsConstructor
public class FinalizeDealExitExecutor implements CommandExecutor {

    private final DealFinalizationStateDataService finalizationStateDataService;

    @Override
    public ServiceCommandType supportedType() {
        return ServiceCommandType.FINALIZE_DEAL_EXIT;
    }

    @Override
    @Transactional
    public ServiceCommandExecutionResult execute(ServiceCommand command, DealActionState actionState,
                                                 DealContext dealContext) {
        DealFinalizationState state = finalizationStateDataService
                .findByDealIdAndType(command.getDealId(), DealFinalizationType.FINALIZE_EXIT)
                .orElseThrow(() -> new IllegalStateException(
                        "DealFinalizationState(FINALIZE_EXIT) not found dealId=" + command.getDealId()));
        if (DealFinalizationStateStatus.COMPLETED.equals(state.getStatus())) {
            return ServiceCommandExecutionResult.ok();
        }
        state.setStatus(DealFinalizationStateStatus.COMPLETED);
        finalizationStateDataService.save(state);
        return ServiceCommandExecutionResult.ok();
    }
}
