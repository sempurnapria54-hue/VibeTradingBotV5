package com.example.tradingbot.domain.command.executor;

import com.example.tradingbot.domain.command.DealActionState;
import com.example.tradingbot.domain.command.DealActionStateStatus;
import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.command.RuntimeTarget;
import com.example.tradingbot.domain.command.ServiceCommand;
import com.example.tradingbot.domain.command.ServiceCommandExecutionResult;
import com.example.tradingbot.domain.command.ServiceCommandType;
import com.example.tradingbot.domain.command.TargetEntityType;
import com.example.tradingbot.domain.command.payload.CreateAlgoOrderCommandPayload;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.persistence.service.AlgoOrderDataService;
import com.example.tradingbot.persistence.service.DealActionStateDataService;
import com.example.tradingbot.util.ClientIdGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Исполняет CREATE_ALGO_ORDER: создаёт локальный AlgoOrder (CREATED) с
 * internalId, condition (trigger/trailing) и параметрами, валидирует
 * conditionType ↔ condition.type, обновляет DealActionState (target =
 * ALGO_ORDER, status = CREATED). На биржу не ходит. См.
 * docs/components/CreateAlgoOrderExecutor.md.
 */
@Component
@RequiredArgsConstructor
public class CreateAlgoOrderExecutor implements CommandExecutor {

    private final AlgoOrderDataService algoOrderDataService;
    private final DealActionStateDataService dealActionStateDataService;

    @Override
    public ServiceCommandType supportedType() {
        return ServiceCommandType.CREATE_ALGO_ORDER;
    }

    @Override
    @Transactional
    public ServiceCommandExecutionResult execute(ServiceCommand command, DealActionState actionState,
                                                 DealContext dealContext) {
        CreateAlgoOrderCommandPayload payload = (CreateAlgoOrderCommandPayload) command.getPayload();
        AlgoOrder saved = algoOrderDataService.save(buildAlgoOrder(payload, dealContext.getDeal().getId()));
        actionState.setTarget(new RuntimeTarget(TargetEntityType.ALGO_ORDER, saved.getId()));
        actionState.setStatus(DealActionStateStatus.CREATED);
        dealActionStateDataService.save(actionState);
        return ServiceCommandExecutionResult.ok();
    }

    private AlgoOrder buildAlgoOrder(CreateAlgoOrderCommandPayload payload, Long dealId) {
        AlgoOrder algoOrder = new AlgoOrder();
        algoOrder.setDealId(dealId);
        algoOrder.setInternalId(ClientIdGenerator.generate());
        algoOrder.setStatus(AlgoOrder.Status.CREATED);
        algoOrder.setConditionType(payload.getConditionType());
        algoOrder.setDirection(payload.getDirection());
        algoOrder.setSize(payload.getSizeContracts());
        algoOrder.setPositionReducingOnly(payload.getPositionReducingOnly());
        algoOrder.setCondition(payload.getCondition());
        algoOrder.validateConditionProjection();
        return algoOrder;
    }
}
