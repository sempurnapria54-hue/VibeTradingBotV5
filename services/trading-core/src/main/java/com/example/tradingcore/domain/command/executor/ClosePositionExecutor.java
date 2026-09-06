package com.example.tradingcore.domain.command.executor;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;

import com.example.tradingbot.domain.exchange.ExchangeAck;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingcore.domain.command.DealActionState;
import com.example.tradingcore.domain.command.DealActionStateStatus;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.RuntimeErrorCode;
import com.example.tradingcore.domain.command.ServiceCommand;
import com.example.tradingcore.domain.command.ServiceCommandExecutionResult;
import com.example.tradingcore.domain.command.ServiceCommandType;
import com.example.tradingcore.domain.command.payload.ClosePositionCommandPayload;
import com.example.tradingcore.integration.exchange.ExchangeOperationsClient;
import com.example.tradingcore.persistence.service.DealActionStateDataService;
import com.example.tradingcore.persistence.service.PositionDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Закрывает позицию по рынку — <b>всегда целиком</b>
 * (docs/rules/no-partial-close.md). Фиксирует запрошенную причину
 * write-once; подтверждение факта закрытия приносит отдельная команда
 * добычи эпизода (docs/components/ClosePositionExecutor.md).
 *
 * <p><b>Закрытый объём исполнитель не возвращает и не хранит:</b> ответ
 * источника идентификатора заявки не несёт, поэтому исполнение команды
 * наблюдается нетто-размером позиции, который приносит подтверждающая
 * добыча, а разложение объёма по траншам делает правило сопоставления
 * (docs/models/domain/aggregate/DealTranche.md). Величина производная,
 * поля под неё не заводится.
 *
 * <p><b>Дочистка вокруг команды — не её дело.</b> Порядок «сперва отмена
 * живых входных заявок траншей, потом закрытие» задан инвариантом
 * teardown'а (docs/rules/exit-teardown-order.md) и держится эмитентом;
 * контракт самого исполнителя от тропы эмиссии не зависит.
 *
 * <p>Преконтроль риска эту команду не блокирует: она риск уменьшает
 * (docs/rules/risk-validator-scope.md).
 */
@Component
@RequiredArgsConstructor
public class ClosePositionExecutor implements CommandExecutor {

    private final PositionDataService positionDataService;
    private final DealActionStateDataService dealActionStateDataService;
    private final ExchangeOperationsClient exchangeOperationsClient;

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
        ExchangeAck ack = exchangeOperationsClient.closePosition(
                dealContext.getExchangeAccount().getInternalId(),
                dealContext.getInstrument().getExternalId(),
                dealContext.getInstrument().getExternalSettlementCurrency());
        if (isFalse(ack.getSuccess())) {
            return ServiceCommandExecutionResult.failure(RuntimeErrorCode.VALIDATION_ERROR, ack.getMessage());
        }
        applyIntent(position, payload.getRequestedCloseReason());
        markSubmitted(actionState);
        return ServiceCommandExecutionResult.ok();
    }

    /** Запрошенная причина — write-once: наблюдение её не перезаписывает. */
    private void applyIntent(Position position, Position.CloseReason requestedCloseReason) {
        if (isNull(position.getCloseReason())) {
            position.setCloseReason(requestedCloseReason);
            positionDataService.save(position);
        }
    }

    private void markSubmitted(DealActionState actionState) {
        if (nonNull(actionState)) {
            actionState.setStatus(DealActionStateStatus.SUBMITTED);
            dealActionStateDataService.save(actionState);
        }
    }
}
