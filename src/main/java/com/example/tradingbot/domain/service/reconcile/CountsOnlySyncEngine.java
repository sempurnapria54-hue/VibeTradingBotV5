package com.example.tradingbot.domain.service.reconcile;

import com.example.tradingbot.domain.service.reconcile.model.CreateUnknownAction;
import com.example.tradingbot.domain.service.reconcile.model.DbInstrumentState;
import com.example.tradingbot.domain.model.snapshot.InstrumentSnapshot;
import com.example.tradingbot.domain.service.reconcile.model.InstrumentBucket;
import com.example.tradingbot.domain.service.reconcile.model.MarkAnomalyAction;
import com.example.tradingbot.domain.service.reconcile.model.MarkClosedAction;
import com.example.tradingbot.domain.service.reconcile.model.ReconcileEntityType;
import com.example.tradingbot.domain.service.reconcile.model.ReconcilePlan;
import com.example.tradingbot.persistence.model.AlgoOrderEntity;
import com.example.tradingbot.persistence.model.OrderEntity;
import com.example.tradingbot.persistence.model.PositionEntity;
import com.example.tradingbot.persistence.service.AlgoOrderDataService;
import com.example.tradingbot.persistence.service.OrderDataService;
import com.example.tradingbot.persistence.service.PositionDataService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CountsOnlySyncEngine {

    private static final String STATUS_UNKNOWN = "UNKNOWN";
    private static final String STATUS_CLOSED = "CLOSED";
    private static final String STATUS_ANOMALY = "ANOMALY";

    private final ReconcilePlanBuilder reconcilePlanBuilder;
    private final PositionDataService positionDataService;
    private final OrderDataService orderDataService;
    private final AlgoOrderDataService algoOrderDataService;

    @Transactional
    public void syncPresence(InstrumentBucket bucket, InstrumentSnapshot exchangeState) {
        if (bucket.getDbState() == null || bucket.getDbState().getInstrument() == null) {
            return;
        }

        DbInstrumentState dbState = bucket.getDbState();
        ReconcilePlan plan = reconcilePlanBuilder.buildPlan(bucket, exchangeState);

        Map<Long, PositionEntity> positionsById = dbState.getActivePositions().stream()
            .collect(Collectors.toMap(PositionEntity::getId, Function.identity()));
        Map<Long, OrderEntity> ordersById = dbState.getActiveOrders().stream()
            .collect(Collectors.toMap(OrderEntity::getId, Function.identity()));
        Map<Long, AlgoOrderEntity> algoOrdersById = dbState.getActiveAlgoOrders().stream()
            .collect(Collectors.toMap(AlgoOrderEntity::getId, Function.identity()));

        for (MarkClosedAction action : plan.getMarkClosed()) {
            if (action.getEntityType() == ReconcileEntityType.POSITION && positionsById.containsKey(action.getEntityId())) {
                PositionEntity entity = positionsById.get(action.getEntityId());
                entity.setStatus(STATUS_CLOSED);
                positionDataService.save(entity);
            }
            if (action.getEntityType() == ReconcileEntityType.ORDER && ordersById.containsKey(action.getEntityId())) {
                OrderEntity entity = ordersById.get(action.getEntityId());
                entity.setStatus(STATUS_CLOSED);
                orderDataService.save(entity);
            }
            if (action.getEntityType() == ReconcileEntityType.ALGO_ORDER && algoOrdersById.containsKey(action.getEntityId())) {
                AlgoOrderEntity entity = algoOrdersById.get(action.getEntityId());
                entity.setStatus(STATUS_CLOSED);
                algoOrderDataService.save(entity);
            }
        }

        for (MarkAnomalyAction action : plan.getMarkAnomaly()) {
            if (action.getEntityType() == ReconcileEntityType.ORDER && ordersById.containsKey(action.getEntityId())) {
                OrderEntity entity = ordersById.get(action.getEntityId());
                entity.setStatus(STATUS_ANOMALY);
                orderDataService.save(entity);
            }
            if (action.getEntityType() == ReconcileEntityType.ALGO_ORDER && algoOrdersById.containsKey(action.getEntityId())) {
                AlgoOrderEntity entity = algoOrdersById.get(action.getEntityId());
                entity.setStatus(STATUS_ANOMALY);
                algoOrderDataService.save(entity);
            }
        }

        for (CreateUnknownAction action : plan.getCreateUnknown()) {
            if (action.getEntityType() == ReconcileEntityType.POSITION) {
                PositionEntity entity = new PositionEntity();
                entity.setExchangeId(dbState.getInstrument().getExchangeId());
                entity.setInstrumentId(dbState.getInstrument().getId());
                entity.setStatus(STATUS_UNKNOWN);
                positionDataService.save(entity);
            }
            if (action.getEntityType() == ReconcileEntityType.ORDER) {
                upsertUnknownOrder(dbState, action);
            }
            if (action.getEntityType() == ReconcileEntityType.ALGO_ORDER) {
                upsertUnknownAlgoOrder(dbState, action);
            }
        }
    }

    private void upsertUnknownOrder(DbInstrumentState dbState, CreateUnknownAction action) {
        String clientOrderId = resolveClientId(action.getClientId(), action.getExchangeId(), "unknown-order");
        if (orderDataService.findByExchangeIdAndInstrumentIdAndClientOrderId(
            dbState.getInstrument().getExchangeId(),
            dbState.getInstrument().getId(),
            clientOrderId
        ).isPresent()) {
            return;
        }

        OrderEntity entity = new OrderEntity();
        entity.setInstrumentId(dbState.getInstrument().getId());
        entity.setInternalId(clientOrderId);
        entity.setExternalId(action.getExchangeId());
        entity.setStatus(STATUS_UNKNOWN);
        orderDataService.save(entity);
    }

    private void upsertUnknownAlgoOrder(DbInstrumentState dbState, CreateUnknownAction action) {
        String clientAlgoOrderId = resolveClientId(action.getClientId(), action.getExchangeId(), "unknown-algo");
        if (algoOrderDataService.findByExchangeIdAndInstrumentIdAndClientAlgoOrderId(
            dbState.getInstrument().getExchangeId(),
            dbState.getInstrument().getId(),
            clientAlgoOrderId
        ).isPresent()) {
            return;
        }

        AlgoOrderEntity entity = new AlgoOrderEntity();
        entity.setInstrumentId(dbState.getInstrument().getId());
        entity.setInternalOrderId(clientAlgoOrderId);
        entity.setExternalId(action.getExchangeId());
        entity.setStatus(STATUS_UNKNOWN);
        algoOrderDataService.save(entity);
    }

    private String resolveClientId(String clientId, String exchangeId, String prefix) {
        if (StringUtils.isNotBlank(clientId)) {
            return clientId;
        }
        if (StringUtils.isNotBlank(exchangeId)) {
            return prefix + "-" + exchangeId;
        }
        return prefix + "-" + UUID.randomUUID();
    }
}
