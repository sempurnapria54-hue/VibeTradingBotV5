package com.example.tradingbot.domain.command.executor;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import com.example.tradingbot.domain.command.DealActionState;
import com.example.tradingbot.domain.command.DealActionStateStatus;
import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.command.ServiceCommand;
import com.example.tradingbot.domain.command.ServiceCommandExecutionResult;
import com.example.tradingbot.domain.command.ServiceCommandType;
import com.example.tradingbot.domain.command.resolve.PositionStatusResolver;
import com.example.tradingbot.domain.command.resolve.StatusResolveResult;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingbot.domain.model.core.position.external_snapshot.PositionExternalSnapshot;
import com.example.tradingbot.integration.service.IntegrationService;
import com.example.tradingbot.mapping.PositionMapper;
import com.example.tradingbot.persistence.service.DealActionStateDataService;
import com.example.tradingbot.persistence.service.PositionDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Исполняет REFRESH_POSITION: читает позицию по инструменту, прогоняет
 * через PositionStatusResolver (null → CLOSED+EXTERNAL_CLOSE, snapshot →
 * ACTIVE). Найдена + локальной нет в active flow → создаёт и привязывает
 * к Deal; не найдена → существующую в CLOSED, новой не создаёт.
 * closeReason write-once. См. docs/components/RefreshPositionExecutor.md.
 */
@Component
@RequiredArgsConstructor
public class RefreshPositionExecutor implements CommandExecutor {

    private final PositionDataService positionDataService;
    private final DealActionStateDataService dealActionStateDataService;
    private final IntegrationService integrationService;
    private final PositionMapper positionMapper;
    private final PositionStatusResolver positionStatusResolver;

    @Override
    public ServiceCommandType supportedType() {
        return ServiceCommandType.REFRESH_POSITION;
    }

    @Override
    @Transactional
    public ServiceCommandExecutionResult execute(ServiceCommand command, DealActionState actionState,
                                                 DealContext dealContext) {
        Long dealId = dealContext.getDeal().getId();
        PositionExternalSnapshot snapshot = integrationService.getPosition(
                dealContext.getInstrument().getExternalId());
        Position position = positionDataService.findByDealId(dealId).orElse(null);
        if (isNull(position) && isNull(snapshot)) {
            completeAction(actionState);
            return ServiceCommandExecutionResult.ok();
        }
        if (isNull(position)) {
            position = new Position();
            position.setDealId(dealId);
        }
        if (nonNull(snapshot)) {
            positionMapper.updateFromSnapshot(snapshot, position);
        }
        StatusResolveResult<Position.Status, Position.CloseReason> result = positionStatusResolver.resolve(snapshot);
        position.setStatus(result.getStatus());
        if (isNull(position.getCloseReason()) && nonNull(result.getCloseReason())) {
            position.setCloseReason(result.getCloseReason());
        }
        positionDataService.save(position);
        completeAction(actionState);
        return ServiceCommandExecutionResult.ok();
    }

    private void completeAction(DealActionState actionState) {
        if (nonNull(actionState)) {
            actionState.setStatus(DealActionStateStatus.COMPLETED);
            dealActionStateDataService.save(actionState);
        }
    }
}
