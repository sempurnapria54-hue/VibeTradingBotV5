package com.example.tradingbot.domain.command.executor;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;

import com.example.tradingbot.domain.command.DealActionState;
import com.example.tradingbot.domain.command.DealActionStateStatus;
import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.command.ExchangeAck;
import com.example.tradingbot.domain.command.RuntimeErrorCode;
import com.example.tradingbot.domain.command.ServiceCommand;
import com.example.tradingbot.domain.command.ServiceCommandExecutionResult;
import com.example.tradingbot.domain.command.ServiceCommandType;
import com.example.tradingbot.domain.command.payload.ClosePositionCommandPayload;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingbot.integration.service.IntegrationService;
import com.example.tradingbot.persistence.service.DealActionStateDataService;
import com.example.tradingbot.persistence.service.PositionDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Исполняет CLOSE_POSITION (всегда full close, no-partial-close):
 * загружает позицию, отправляет full close на биржу, фиксирует requested
 * close-причину write-once. В CLOSED по ACK не переводит — подтверждает
 * REFRESH_POSITION. Обновляет DealActionState = SUBMITTED. (settle ccy
 * для close-request — refinement.) См.
 * docs/components/ClosePositionExecutor.md.
 */
@Component
@RequiredArgsConstructor
public class ClosePositionExecutor implements CommandExecutor {

    private final PositionDataService positionDataService;
    private final DealActionStateDataService dealActionStateDataService;
    private final IntegrationService integrationService;

    @Override
    public ServiceCommandType supportedType() {
        return ServiceCommandType.CLOSE_POSITION_COMMAND;
    }

    @Override
    @Transactional
    public ServiceCommandExecutionResult execute(ServiceCommand command, DealActionState actionState,
                                                 DealContext dealContext) {
        ClosePositionCommandPayload payload = (ClosePositionCommandPayload) command.getPayload();
        Position position = positionDataService.getRequiredById(payload.getPositionId());
        ExchangeAck ack = integrationService.closePosition(dealContext.getInstrument().getExternalId(), null);
        if (isFalse(ack.getSuccess())) {
            return ServiceCommandExecutionResult.failure(RuntimeErrorCode.VALIDATION_ERROR, ack.getMessage());
        }
        if (isNull(position.getCloseReason())) {
            position.setCloseReason(payload.getRequestedCloseReason());
            positionDataService.save(position);
        }
        if (nonNull(actionState)) {
            actionState.setStatus(DealActionStateStatus.SUBMITTED);
            dealActionStateDataService.save(actionState);
        }
        return ServiceCommandExecutionResult.ok();
    }
}
