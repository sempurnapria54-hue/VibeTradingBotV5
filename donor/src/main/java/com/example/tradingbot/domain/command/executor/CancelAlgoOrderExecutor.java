package com.example.tradingbot.domain.command.executor;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;

import com.example.tradingbot.domain.command.DealActionState;
import com.example.tradingbot.domain.command.DealActionStateStatus;
import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.exchange.ExchangeAck;
import com.example.tradingbot.domain.command.RuntimeErrorCode;
import com.example.tradingbot.domain.command.ServiceCommand;
import com.example.tradingbot.domain.command.ServiceCommandExecutionResult;
import com.example.tradingbot.domain.command.ServiceCommandType;
import com.example.tradingbot.domain.command.payload.CancelAlgoOrderCommandPayload;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.integration.service.IntegrationService;
import com.example.tradingbot.persistence.service.AlgoOrderDataService;
import com.example.tradingbot.persistence.service.DealActionStateDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Исполняет CANCEL_ALGO_ORDER: загружает algo-order, отправляет cancel
 * (endpoint ветвится по семье algo из conditionType — внутри
 * IntegrationService), фиксирует cancel-причину write-once. В CANCELED
 * по ACK не переводит — подтверждает REFRESH_ALGO_ORDER. Обновляет
 * DealActionState = SUBMITTED. См.
 * docs/components/CancelAlgoOrderExecutor.md.
 */
@Component
@RequiredArgsConstructor
public class CancelAlgoOrderExecutor implements CommandExecutor {

    private final AlgoOrderDataService algoOrderDataService;
    private final DealActionStateDataService dealActionStateDataService;
    private final IntegrationService integrationService;

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
        ExchangeAck ack = integrationService.cancelAlgoOrder(algoOrder,
                dealContext.getInstrument().getExternalId());
        if (isFalse(ack.getSuccess())) {
            return ServiceCommandExecutionResult.failure(RuntimeErrorCode.VALIDATION_ERROR, ack.getMessage());
        }
        if (isNull(algoOrder.getCloseReason())) {
            algoOrder.setCloseReason(payload.getCancelReason());
            algoOrderDataService.save(algoOrder);
        }
        if (nonNull(actionState)) {
            actionState.setStatus(DealActionStateStatus.SUBMITTED);
            dealActionStateDataService.save(actionState);
        }
        return ServiceCommandExecutionResult.ok();
    }
}
