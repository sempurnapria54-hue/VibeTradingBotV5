package com.example.tradingbot.domain.command.executor;

import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.StringUtils.isBlank;

import com.example.tradingbot.domain.command.DealActionState;
import com.example.tradingbot.domain.command.DealActionStateStatus;
import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.command.ExchangeAck;
import com.example.tradingbot.domain.command.RuntimeErrorCode;
import com.example.tradingbot.domain.command.ServiceCommand;
import com.example.tradingbot.domain.command.ServiceCommandExecutionResult;
import com.example.tradingbot.domain.command.ServiceCommandType;
import com.example.tradingbot.domain.command.payload.SubmitAlgoOrderCommandPayload;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.integration.service.IntegrationService;
import com.example.tradingbot.persistence.service.AlgoOrderDataService;
import com.example.tradingbot.persistence.service.DealActionStateDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Исполняет SUBMIT_ALGO_ORDER: загружает локальный AlgoOrder; если
 * externalId пуст — отправляет на биржу, на successful ACK сохраняет
 * externalId + PENDING (строгий transitTo); обновляет DealActionState =
 * SUBMITTED. ACK не runtime truth — факт подтверждает REFRESH_ALGO_ORDER.
 * (Recovery-поиск по client id — refinement.) См.
 * docs/components/SubmitAlgoOrderExecutor.md.
 */
@Component
@RequiredArgsConstructor
public class SubmitAlgoOrderExecutor implements CommandExecutor {

    private final AlgoOrderDataService algoOrderDataService;
    private final DealActionStateDataService dealActionStateDataService;
    private final IntegrationService integrationService;

    @Override
    public ServiceCommandType supportedType() {
        return ServiceCommandType.SUBMIT_ALGO_ORDER;
    }

    @Override
    @Transactional
    public ServiceCommandExecutionResult execute(ServiceCommand command, DealActionState actionState,
                                                 DealContext dealContext) {
        SubmitAlgoOrderCommandPayload payload = (SubmitAlgoOrderCommandPayload) command.getPayload();
        AlgoOrder algoOrder = algoOrderDataService.getRequiredById(payload.getAlgoOrderId());
        if (isBlank(algoOrder.getExternalId())) {
            ExchangeAck ack = integrationService.placeAlgoOrder(algoOrder,
                    dealContext.getInstrument().getExternalId());
            if (isFalse(ack.getSuccess())) {
                return ServiceCommandExecutionResult.failure(RuntimeErrorCode.VALIDATION_ERROR, ack.getMessage());
            }
            algoOrder.setExternalId(ack.getExternalId());
            algoOrder.toPending();
            algoOrderDataService.save(algoOrder);
        }
        actionState.setStatus(DealActionStateStatus.SUBMITTED);
        dealActionStateDataService.save(actionState);
        return ServiceCommandExecutionResult.ok();
    }
}
