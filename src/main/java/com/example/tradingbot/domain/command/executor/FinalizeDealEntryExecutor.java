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
 * Исполняет FINALIZE_DEAL_ENTRY — консолидацию результата входа после
 * подтверждённого ордера и позиции. Опирается на уже добытые факты, на
 * биржу не ходит, RiskValidator не вызывает. Статус Deal сам не двигает
 * (ENTRY_SUBMITTED → ENTRY_FINALIZED делает FSM по фактам) — только
 * закрывает свою финализационную строку. Retry-anchor —
 * DealFinalizationState(deal, FINALIZE_ENTRY); идемпотентность — через
 * UNIQUE(deal_id, type) (повтор → no-op → COMPLETED). См.
 * docs/components/FinalizeDealEntryExecutor.md.
 */
@Component
@RequiredArgsConstructor
public class FinalizeDealEntryExecutor implements CommandExecutor {

    private final DealFinalizationStateDataService finalizationStateDataService;

    @Override
    public ServiceCommandType supportedType() {
        return ServiceCommandType.FINALIZE_DEAL_ENTRY;
    }

    @Override
    @Transactional
    public ServiceCommandExecutionResult execute(ServiceCommand command, DealActionState actionState,
                                                 DealContext dealContext) {
        DealFinalizationState state = finalizationStateDataService
                .findByDealIdAndType(command.getDealId(), DealFinalizationType.FINALIZE_ENTRY)
                .orElseThrow(() -> new IllegalStateException(
                        "DealFinalizationState(FINALIZE_ENTRY) not found dealId=" + command.getDealId()));
        if (DealFinalizationStateStatus.COMPLETED.equals(state.getStatus())) {
            return ServiceCommandExecutionResult.ok();
        }
        state.setStatus(DealFinalizationStateStatus.COMPLETED);
        finalizationStateDataService.save(state);
        return ServiceCommandExecutionResult.ok();
    }
}
