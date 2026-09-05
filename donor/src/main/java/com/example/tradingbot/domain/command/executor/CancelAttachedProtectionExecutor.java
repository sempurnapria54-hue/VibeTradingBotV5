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
import com.example.tradingbot.domain.command.payload.CancelAttachedProtectionCommandPayload;
import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import com.example.tradingbot.integration.service.IntegrationService;
import com.example.tradingbot.persistence.service.DealActionStateDataService;
import com.example.tradingbot.persistence.service.OrderDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Исполняет CANCEL_ATTACHED_PROTECTION: снимает ВСТРОЕННУЮ защиту —
 * раздел модели Order, а не отдельную условную заявку. В CANCELED по ACK
 * не переводит: факт снятия подтверждает добыча. Причина фиксируется
 * write-once до наблюдения — она и есть наше стоящее НАМЕРЕНИЕ, из
 * которого разбор истории берёт причину найденной снятой записи. Числа
 * риска не пересчитывает: команда записывает намерение, а не факт. См.
 * docs/components/CancelAttachedProtectionExecutor.md.
 */
@Component
@RequiredArgsConstructor
public class CancelAttachedProtectionExecutor implements CommandExecutor {

    private final OrderDataService orderDataService;
    private final DealActionStateDataService dealActionStateDataService;
    private final IntegrationService integrationService;

    @Override
    public ServiceCommandType supportedType() {
        return ServiceCommandType.CANCEL_ATTACHED_PROTECTION_COMMAND;
    }

    @Override
    @Transactional
    public ServiceCommandExecutionResult execute(ServiceCommand command, DealActionState actionState,
                                                 DealContext dealContext) {
        CancelAttachedProtectionCommandPayload payload =
                (CancelAttachedProtectionCommandPayload) command.getPayload();
        AttachedAlgoOrder attached =
                orderDataService.getRequiredAttachedById(payload.getAttachedAlgoOrderId());
        ExchangeAck ack = integrationService.cancelAttachedProtection(attached,
                dealContext.getInstrument().getExternalId());
        if (isFalse(ack.getSuccess())) {
            return ServiceCommandExecutionResult.failure(RuntimeErrorCode.VALIDATION_ERROR, ack.getMessage());
        }
        applyIntent(attached, payload.getCancelReason());
        submitAction(actionState);
        return ServiceCommandExecutionResult.ok();
    }

    /** Намерение снятия — write-once: наблюдение его не перезаписывает. */
    private void applyIntent(AttachedAlgoOrder attached, AttachedAlgoOrder.CloseReason cancelReason) {
        if (isNull(attached.getCloseReason())) {
            attached.setCloseReason(cancelReason);
            orderDataService.saveAttached(attached);
        }
    }

    private void submitAction(DealActionState actionState) {
        if (nonNull(actionState)) {
            actionState.setStatus(DealActionStateStatus.SUBMITTED);
            dealActionStateDataService.save(actionState);
        }
    }
}
