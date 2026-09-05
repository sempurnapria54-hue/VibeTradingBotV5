package com.example.tradingbot.domain.command.executor;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.StringUtils.isBlank;

import com.example.tradingbot.domain.command.DealActionState;
import com.example.tradingbot.domain.command.DealActionStateStatus;
import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.exchange.ExchangeAck;
import com.example.tradingbot.domain.command.RuntimeErrorCode;
import com.example.tradingbot.domain.command.ServiceCommand;
import com.example.tradingbot.domain.command.ServiceCommandExecutionResult;
import com.example.tradingbot.domain.command.ServiceCommandType;
import com.example.tradingbot.domain.command.payload.SubmitAlgoOrderCommandPayload;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.algo_order.external_snapshot.AlgoOrderExternalSnapshot;
import com.example.tradingbot.integration.service.IntegrationService;
import com.example.tradingbot.persistence.service.AlgoOrderDataService;
import com.example.tradingbot.persistence.service.DealActionStateDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Исполняет SUBMIT_ALGO_ORDER: загружает локальный AlgoOrder; если
 * externalId пуст — перед повторным submit ищет факт по stable client id
 * (D-B3: place мог реально пройти, ответ потерян), иначе отправляет на
 * биржу, на successful ACK сохраняет externalId + PENDING (строгий
 * transitTo); обновляет DealActionState = SUBMITTED. ACK не runtime truth
 * — факт подтверждает REFRESH_ALGO_ORDER. См.
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
        return ServiceCommandType.SUBMIT_ALGO_ORDER_COMMAND;
    }

    @Override
    @Transactional
    public ServiceCommandExecutionResult execute(ServiceCommand command, DealActionState actionState,
                                                 DealContext dealContext) {
        SubmitAlgoOrderCommandPayload payload = (SubmitAlgoOrderCommandPayload) command.getPayload();
        AlgoOrder algoOrder = algoOrderDataService.getRequiredById(payload.getAlgoOrderId());
        String instId = dealContext.getInstrument().getExternalId();
        if (isBlank(algoOrder.getExternalId()) && isFalse(recoverByClientId(algoOrder, instId, actionState))) {
            ExchangeAck ack = integrationService.placeAlgoOrder(algoOrder, instId);
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

    /**
     * D-B3: только перед ПОВТОРНЫМ submit ищем algo по stable client id —
     * предыдущий place мог реально выполниться. Найден → восстанавливаем
     * externalId, второй раз не отправляем (строгий transitTo PENDING).
     */
    private Boolean recoverByClientId(AlgoOrder algoOrder, String instId, DealActionState actionState) {
        if (isFalse(isRetry(actionState))) {
            return false;
        }
        AlgoOrderExternalSnapshot existing = integrationService.getAlgoOrder(instId, null, algoOrder.getInternalId());
        if (isNull(existing) || isBlank(existing.getExternalId())) {
            return false;
        }
        algoOrder.setExternalId(existing.getExternalId());
        algoOrder.toPending();
        algoOrderDataService.save(algoOrder);
        return true;
    }

    private Boolean isRetry(DealActionState actionState) {
        return nonNull(actionState) && nonNull(actionState.getAttemptCount()) && actionState.getAttemptCount() > 0;
    }
}
