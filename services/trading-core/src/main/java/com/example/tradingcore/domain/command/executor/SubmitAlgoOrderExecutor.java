package com.example.tradingcore.domain.command.executor;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.StringUtils.isBlank;

import com.example.tradingbot.domain.exchange.ExchangeAck;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingcore.domain.command.DealActionState;
import com.example.tradingcore.domain.command.DealActionStateStatus;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.RuntimeErrorCode;
import com.example.tradingcore.domain.command.ServiceCommand;
import com.example.tradingcore.domain.command.ServiceCommandExecutionResult;
import com.example.tradingcore.domain.command.ServiceCommandType;
import com.example.tradingcore.domain.command.payload.SubmitAlgoOrderCommandPayload;
import com.example.tradingcore.integration.exchange.ExchangeOperationsClient;
import com.example.tradingcore.persistence.service.AlgoOrderDataService;
import com.example.tradingcore.persistence.service.DealActionStateDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Отправляет отдельную условную заявку на площадку.
 *
 * <p>Внешний идентификатор пуст — перед повторной отправкой ищет заявку по
 * стабильному клиентскому идентификатору: постановка могла реально пройти,
 * а ответ потеряться. Подтверждение приёма состоянием не считается — факт
 * подтверждает добыча (docs/rules/ack-not-runtime-truth.md,
 * docs/components/SubmitAlgoOrderExecutor.md).
 */
@Component
@RequiredArgsConstructor
public class SubmitAlgoOrderExecutor implements CommandExecutor {

    private final AlgoOrderDataService algoOrderDataService;
    private final DealActionStateDataService dealActionStateDataService;
    private final ExchangeOperationsClient exchangeOperationsClient;

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
        String accountInternalId = dealContext.getExchangeAccount().getInternalId();
        String externalInstrumentId = dealContext.getInstrument().getExternalId();
        if (isBlank(algoOrder.getExternalId())
                && isFalse(recoverByClientId(algoOrder, accountInternalId, externalInstrumentId, actionState))) {
            ExchangeAck ack = exchangeOperationsClient.placeAlgoOrder(accountInternalId, algoOrder,
                    externalInstrumentId);
            if (isFalse(ack.getSuccess())) {
                return ServiceCommandExecutionResult.failure(RuntimeErrorCode.VALIDATION_ERROR, ack.getMessage());
            }
            applySubmitted(algoOrder, ack.getExternalId());
        }
        actionState.setStatus(DealActionStateStatus.SUBMITTED);
        dealActionStateDataService.save(actionState);
        return ServiceCommandExecutionResult.ok();
    }

    /**
     * Только перед ПОВТОРНОЙ отправкой ищем заявку по стабильному
     * клиентскому идентификатору: предыдущая постановка могла реально
     * пройти. Найдена — восстанавливаем факт отправки и второй раз не шлём.
     */
    private Boolean recoverByClientId(AlgoOrder algoOrder, String accountInternalId,
                                      String externalInstrumentId, DealActionState actionState) {
        if (isFalse(isRetry(actionState))) {
            return false;
        }
        AlgoOrder existing = exchangeOperationsClient.getAlgoOrder(accountInternalId, externalInstrumentId,
                null, algoOrder.getInternalId());
        if (isNull(existing) || isBlank(existing.getExternalId())) {
            return false;
        }
        applySubmitted(algoOrder, existing.getExternalId());
        return true;
    }

    private void applySubmitted(AlgoOrder algoOrder, String externalId) {
        algoOrder.setExternalId(externalId);
        algoOrder.toPending();
        algoOrderDataService.save(algoOrder);
    }

    private Boolean isRetry(DealActionState actionState) {
        return nonNull(actionState) && nonNull(actionState.getAttemptCount())
                && actionState.getAttemptCount() > 0;
    }
}
