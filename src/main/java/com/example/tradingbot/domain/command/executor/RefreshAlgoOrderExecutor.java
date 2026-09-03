package com.example.tradingbot.domain.command.executor;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import com.example.tradingbot.domain.command.DealActionState;
import com.example.tradingbot.domain.command.DealActionStateStatus;
import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.command.ServiceCommand;
import com.example.tradingbot.domain.command.ServiceCommandExecutionResult;
import com.example.tradingbot.domain.command.ServiceCommandType;
import com.example.tradingbot.domain.command.risk.DealRiskNumbersWriter;
import com.example.tradingbot.domain.command.payload.RefreshAlgoOrderCommandPayload;
import com.example.tradingbot.domain.command.resolve.AlgoOrderExternalStatusResolver;
import com.example.tradingbot.domain.command.resolve.StatusResolveResult;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.algo_order.external_snapshot.AlgoOrderExternalSnapshot;
import com.example.tradingbot.integration.service.ExternalStatusException;
import com.example.tradingbot.integration.service.ExternalStatusReason;
import com.example.tradingbot.integration.service.IntegrationService;
import com.example.tradingbot.mapping.AlgoOrderMapper;
import com.example.tradingbot.persistence.service.AlgoOrderDataService;
import com.example.tradingbot.persistence.service.DealActionStateDataService;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Исполняет REFRESH_ALGO_ORDER, обходя algo evidence-cycle внутри
 * команды: GET /trade/order-algo → orders-algo-pending →
 * orders-algo-history (ordType из conditionType), обрыв на первом
 * найденном (матч по internalId). Не найден после полного цикла →
 * AlgoOrder.ERROR + MISSING_AFTER_REFRESH. Найден → обновляет факты,
 * применяет статус через AlgoOrderExternalStatusResolver
 * (order_failed/partially_failed/unknown → ERROR). Только AlgoOrder. См.
 * docs/components/RefreshAlgoOrderExecutor.md,
 * docs/decisions/refresh-evidence-cycle-ownership.md.
 */
@Component
@RequiredArgsConstructor
public class RefreshAlgoOrderExecutor implements CommandExecutor {

    private final AlgoOrderDataService algoOrderDataService;
    private final DealActionStateDataService dealActionStateDataService;
    private final IntegrationService integrationService;
    private final AlgoOrderMapper algoOrderMapper;
    private final AlgoOrderExternalStatusResolver algoOrderStatusResolver;
    private final DealRiskNumbersWriter riskNumbersWriter;

    @Override
    public ServiceCommandType supportedType() {
        return ServiceCommandType.REFRESH_ALGO_ORDER;
    }

    @Override
    @Transactional
    public ServiceCommandExecutionResult execute(ServiceCommand command, DealActionState actionState,
                                                 DealContext dealContext) {
        RefreshAlgoOrderCommandPayload payload = (RefreshAlgoOrderCommandPayload) command.getPayload();
        AlgoOrder algoOrder = algoOrderDataService.getRequiredById(payload.getAlgoOrderId());
        AlgoOrderExternalSnapshot snapshot = findSnapshot(algoOrder, dealContext.getInstrument().getExternalId());
        if (isNull(snapshot)) {
            setError(algoOrder, AlgoOrder.CloseReason.MISSING_AFTER_REFRESH);
        } else {
            applyOrUnknown(algoOrder, snapshot);
        }
        algoOrderDataService.save(algoOrder);
        riskNumbersWriter.recompute(dealContext);
        completeAction(actionState);
        return ServiceCommandExecutionResult.ok();
    }

    /** Algo evidence-cycle: single → pending → history; обрыв на первом найденном. */
    private AlgoOrderExternalSnapshot findSnapshot(AlgoOrder algoOrder, String instId) {
        AlgoOrderExternalSnapshot single = integrationService.getAlgoOrder(
                instId, algoOrder.getExternalId(), algoOrder.getInternalId());
        if (nonNull(single)) {
            return single;
        }
        AlgoOrder.ConditionType conditionType = algoOrder.getConditionType();
        AlgoOrderExternalSnapshot pending = matchByInternalId(
                integrationService.getPendingAlgoOrders(instId, conditionType), algoOrder.getInternalId());
        if (nonNull(pending)) {
            return pending;
        }
        // Обязательный операнд истории закрывается идентификатором записи,
        // если он известен, иначе — терминальными состояниями (adapter);
        // без обоих эндпоинт отвечает отказом, а не пустой историей.
        return matchByInternalId(
                integrationService.getAlgoOrderHistory(instId, conditionType, algoOrder.getExternalId()),
                algoOrder.getInternalId());
    }

    private AlgoOrderExternalSnapshot matchByInternalId(List<AlgoOrderExternalSnapshot> snapshots, String internalId) {
        if (isEmpty(snapshots)) {
            return null;
        }
        return snapshots.stream()
                .filter(snapshot -> isNotBlank(snapshot.getInternalId())
                        && Objects.equals(internalId, snapshot.getInternalId()))
                .findFirst()
                .orElse(null);
    }

    private void applyOrUnknown(AlgoOrder algoOrder, AlgoOrderExternalSnapshot snapshot) {
        algoOrderMapper.updateFromSnapshot(snapshot, algoOrder);
        try {
            applyStatus(algoOrder, snapshot);
        } catch (ExternalStatusException e) {
            setError(algoOrder, toCloseReason(e.getReasonCode()));
        }
    }

    private void applyStatus(AlgoOrder algoOrder, AlgoOrderExternalSnapshot snapshot) {
        StatusResolveResult<AlgoOrder.Status, AlgoOrder.CloseReason> result =
                algoOrderStatusResolver.resolve(snapshot.getExternalStatus());
        algoOrder.setStatus(result.getStatus());
        if (isNull(algoOrder.getCloseReason()) && nonNull(result.getCloseReason())) {
            algoOrder.setCloseReason(result.getCloseReason());
        }
    }

    private void setError(AlgoOrder algoOrder, AlgoOrder.CloseReason closeReason) {
        algoOrder.setStatus(AlgoOrder.Status.ERROR);
        if (isNull(algoOrder.getCloseReason())) {
            algoOrder.setCloseReason(closeReason);
        }
    }

    private AlgoOrder.CloseReason toCloseReason(ExternalStatusReason reason) {
        return switch (reason) {
            case ORDER_FAILED -> AlgoOrder.CloseReason.ORDER_FAILED;
            case PARTIALLY_FAILED -> AlgoOrder.CloseReason.PARTIALLY_FAILED;
            case UNKNOWN_EXTERNAL_STATUS -> AlgoOrder.CloseReason.UNKNOWN_EXTERNAL_STATUS;
        };
    }

    private void completeAction(DealActionState actionState) {
        if (nonNull(actionState)) {
            actionState.setStatus(DealActionStateStatus.COMPLETED);
            dealActionStateDataService.save(actionState);
        }
    }
}
