package com.example.tradingcore.domain.command.executor;

import static java.util.Objects.isNull;
import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;

import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingcore.domain.command.DealActionState;
import com.example.tradingcore.domain.command.DealActionStateStatus;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.RuntimeErrorCode;
import com.example.tradingcore.domain.command.ServiceCommand;
import com.example.tradingcore.domain.command.ServiceCommandExecutionResult;
import com.example.tradingcore.domain.command.ServiceCommandType;
import com.example.tradingcore.persistence.service.DealActionStateDataService;
import com.example.tradingcore.persistence.service.DealTrancheDataService;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Консолидирует результат входа ТРАНША после подтверждённых заявки и
 * позиции — единственное звено действия консолидации входа
 * (docs/components/FinalizeDealEntryExecutor.md).
 *
 * <p>Опирается на уже добытые факты, на биржу не ходит, преконтроль не
 * вызывает: новых торговых решений звено не принимает.
 *
 * <p><b>Статус подтверждённого входа транша пишется ЗДЕСЬ, той же
 * транзакцией, что и завершение исполнения.</b> Иначе вывод стадии из
 * фактов ломался бы на рестарте: строка была бы завершена, а транш остался
 * бы в отправленном входе, и следующий проход начал бы консолидацию
 * заново. Обработчик отправленного входа своими выходными проверками
 * ГЕЙТИТ эмиссию, а ребро едет в транзакции завершения.
 *
 * <p><b>Транш адресуется строкой исполнения:</b> консолидация входа —
 * единственное потраншевое системное действие, и её транш назван в анкере,
 * а не выводится из графа сделки.
 *
 * <p><b>Энфорсеров счёта звено не пишет:</b> оно консолидирует ВХОД,
 * терминалом сделки не является и её результата не знает
 * (docs/rules/loss-streak-halt.md, docs/rules/risk-policy.md).
 */
@Component
@RequiredArgsConstructor
public class FinalizeDealEntryExecutor implements CommandExecutor {

    private final DealActionStateDataService dealActionStateDataService;
    private final DealTrancheDataService dealTrancheDataService;

    @Override
    public ServiceCommandType supportedType() {
        return ServiceCommandType.FINALIZE_DEAL_ENTRY_COMMAND;
    }

    @Override
    @Transactional
    public ServiceCommandExecutionResult execute(ServiceCommand command, DealActionState actionState,
                                                 DealContext dealContext) {
        if (isNull(actionState) || isNull(actionState.getDealTrancheId())) {
            return ServiceCommandExecutionResult.failure(RuntimeErrorCode.INTERNAL_ERROR,
                    "консолидация входа адресует транш, а анкер его не называет dealId=" + command.getDealId());
        }
        DealTranche tranche = trancheOf(dealContext, actionState.getDealTrancheId());
        if (isNull(tranche)) {
            return ServiceCommandExecutionResult.failure(RuntimeErrorCode.INTERNAL_ERROR,
                    "транш анкера не предъявлен графом сделки trancheId=" + actionState.getDealTrancheId());
        }
        if (DealTranche.Status.ENTRY_SUBMITTED.equals(tranche.getStatus())) {
            tranche.setStatus(DealTranche.Status.ENTRY_FINALIZED);
            dealTrancheDataService.save(tranche);
        }
        actionState.setStatus(DealActionStateStatus.COMPLETED);
        dealActionStateDataService.save(actionState);
        return ServiceCommandExecutionResult.ok();
    }

    private DealTranche trancheOf(DealContext dealContext, Long trancheId) {
        return emptyIfNull(dealContext.getDeal().getTranches()).stream()
                .filter(tranche -> Objects.equals(trancheId, tranche.getId()))
                .findFirst()
                .orElse(null);
    }
}
