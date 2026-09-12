package com.example.tradingcore.domain.command.executor;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;

import com.example.tradingbot.domain.exchange.ExchangeAck;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingcore.domain.command.DealActionState;
import com.example.tradingcore.domain.command.DealActionStateStatus;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.RuntimeErrorCode;
import com.example.tradingcore.domain.command.ServiceCommand;
import com.example.tradingcore.domain.command.ServiceCommandExecutionResult;
import com.example.tradingcore.domain.command.ServiceCommandType;
import com.example.tradingcore.domain.command.payload.CancelAlgoOrderCommandPayload;
import com.example.tradingcore.integration.internal.api.exchange.ExchangeOperationsClient;
import com.example.tradingcore.persistence.service.AlgoOrderDataService;
import com.example.tradingcore.persistence.service.DealActionStateDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Снимает ОТДЕЛЬНУЮ условную заявку: отправляет снятие и фиксирует причину
 * write-once. Эндпоинт ветвится по семье условия — ветвление держит
 * коннектор на своей границе (docs/models/mapping/AlgoOrder.md).
 *
 * <p><b>В снятое состояние по подтверждению приёма не переводит:</b> факт
 * снятия подтверждает добыча (docs/rules/ack-not-runtime-truth.md).
 *
 * <p><b>Встроенной защиты эта команда не адресует</b> — у той своя команда
 * и непересекающийся словарь причин
 * (docs/components/CancelAttachedProtectionExecutor.md).
 *
 * <p><b>Четвёрку чисел риска не пересчитывает:</b> операнд «действующая
 * защита» меняет не снятие, а добыча — снятие заявку не финализирует
 * (docs/lifecycles/AlgoOrder.md), и пересчёт здесь был бы пересчётом по
 * намерению.
 */
@Component
@RequiredArgsConstructor
public class CancelAlgoOrderExecutor implements CommandExecutor {

    private final AlgoOrderDataService algoOrderDataService;
    private final DealActionStateDataService dealActionStateDataService;
    private final ExchangeOperationsClient exchangeOperationsClient;

    @Override
    public ServiceCommandType supportedType() {
        return ServiceCommandType.CANCEL_ALGO_ORDER_COMMAND;
    }

    @Override
    @Transactional
    public ServiceCommandExecutionResult execute(ServiceCommand command, DealActionState actionState,
                                                 DealContext dealContext) {
        CancelAlgoOrderCommandPayload payload = (CancelAlgoOrderCommandPayload) command.getPayload();
        AlgoOrder algoOrder = algoOrderDataService.getRequiredById(payload.getAlgoOrderId());
        ExchangeAck ack = exchangeOperationsClient.cancelAlgoOrder(
                dealContext.getExchangeAccount().getInternalId(), algoOrder,
                dealContext.getInstrument().getExternalId());
        if (isFalse(ack.getSuccess())) {
            return ServiceCommandExecutionResult.failure(RuntimeErrorCode.VALIDATION_ERROR, ack.getMessage());
        }
        applyIntent(algoOrder, payload.getCancelReason());
        markSubmitted(actionState);
        return ServiceCommandExecutionResult.ok();
    }

    /** Намерение снятия — write-once: наблюдение его не перезаписывает. */
    private void applyIntent(AlgoOrder algoOrder, AlgoOrder.CloseReason cancelReason) {
        if (isNull(algoOrder.getCloseReason())) {
            algoOrder.setCloseReason(cancelReason);
            algoOrderDataService.save(algoOrder);
        }
    }

    private void markSubmitted(DealActionState actionState) {
        if (nonNull(actionState)) {
            actionState.setStatus(DealActionStateStatus.SUBMITTED);
            dealActionStateDataService.save(actionState);
        }
    }
}
