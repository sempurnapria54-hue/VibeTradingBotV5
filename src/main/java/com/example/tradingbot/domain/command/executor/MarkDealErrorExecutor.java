package com.example.tradingbot.domain.command.executor;

import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;

import com.example.tradingbot.domain.command.DealActionState;
import com.example.tradingbot.domain.command.DealActionStateStatus;
import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.command.ServiceCommand;
import com.example.tradingbot.domain.command.ServiceCommandExecutionResult;
import com.example.tradingbot.domain.command.ServiceCommandType;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.persistence.service.DealActionStateDataService;
import com.example.tradingbot.persistence.service.DealDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Исполняет MARK_DEAL_ERROR_COMMAND — вход сделки в ошибочное состояние
 * (нетерминальный статус для обработчика ошибочной тропы). Сам терминал не
 * ставит: аварийный терминал — <b>отдельное исполнение</b> того же
 * системного действия, и разбор до него ведёт обработчик ошибочного
 * состояния.
 *
 * <p>Причина закрытия на этом ребре не пишется: ERROR не терминал,
 * итоговая причина ещё не определена (docs/spec/deal-lifecycle.json
 * §closeReasonWriter, значение NONE_ON_ERROR_EDGE).
 *
 * <p>См. docs/components/MarkDealErrorExecutor.md.
 */
@Component
@RequiredArgsConstructor
public class MarkDealErrorExecutor implements CommandExecutor {

    private final DealDataService dealDataService;
    private final DealActionStateDataService dealActionStateDataService;

    @Override
    public ServiceCommandType supportedType() {
        return ServiceCommandType.MARK_DEAL_ERROR_COMMAND;
    }

    @Override
    @Transactional
    public ServiceCommandExecutionResult execute(ServiceCommand command, DealActionState actionState,
                                                 DealContext dealContext) {
        Deal deal = dealContext.getDeal();
        if (isFalse(Deal.Status.ERROR.equals(deal.getStatus()))) {
            deal.setStatus(Deal.Status.ERROR);
            dealDataService.save(deal);
        }
        if (nonNull(actionState)) {
            actionState.setStatus(DealActionStateStatus.COMPLETED);
            dealActionStateDataService.save(actionState);
        }
        return ServiceCommandExecutionResult.ok();
    }
}
