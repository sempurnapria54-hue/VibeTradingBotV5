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
import com.example.tradingbot.domain.command.payload.SubmitOrderCommandPayload;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.integration.service.IntegrationService;
import com.example.tradingbot.persistence.service.DealActionStateDataService;
import com.example.tradingbot.persistence.service.OrderDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Исполняет SUBMIT_ORDER: загружает локальный Order; если externalId
 * пуст — отправляет на биржу (placeOrder), на successful ACK сохраняет
 * externalId + PENDING; обновляет DealActionState = SUBMITTED. ACK не
 * runtime truth — факт подтверждает REFRESH_ORDER. (Recovery-поиск по
 * client id — refinement.) См. docs/components/SubmitOrderExecutor.md.
 */
@Component
@RequiredArgsConstructor
public class SubmitOrderExecutor implements CommandExecutor {

    private final OrderDataService orderDataService;
    private final DealActionStateDataService dealActionStateDataService;
    private final IntegrationService integrationService;

    @Override
    public ServiceCommandType supportedType() {
        return ServiceCommandType.SUBMIT_ORDER;
    }

    @Override
    @Transactional
    public ServiceCommandExecutionResult execute(ServiceCommand command, DealActionState actionState,
                                                 DealContext dealContext) {
        SubmitOrderCommandPayload payload = (SubmitOrderCommandPayload) command.getPayload();
        Order order = orderDataService.getRequiredById(payload.getOrderId());
        if (isBlank(order.getExternalId())) {
            ExchangeAck ack = integrationService.placeOrder(order, dealContext.getInstrument().getExternalId());
            if (isFalse(ack.getSuccess())) {
                return ServiceCommandExecutionResult.failure(RuntimeErrorCode.VALIDATION_ERROR, ack.getMessage());
            }
            order.setExternalId(ack.getExternalId());
            order.setStatus(Order.Status.PENDING);
            orderDataService.save(order);
        }
        actionState.setStatus(DealActionStateStatus.SUBMITTED);
        dealActionStateDataService.save(actionState);
        return ServiceCommandExecutionResult.ok();
    }
}
