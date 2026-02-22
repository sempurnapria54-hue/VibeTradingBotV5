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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
public class ReconcilePlanBuilder {

    public ReconcilePlan buildPlan(InstrumentBucket bucket, InstrumentSnapshot exchangeState) {
        DbInstrumentState dbState = Objects.requireNonNull(bucket.getDbState(), "bucket.dbState is required for SYNC planning");

        List<CreateUnknownAction> createUnknown = new ArrayList<>();
        List<MarkClosedAction> markClosed = new ArrayList<>();
        List<MarkAnomalyAction> markAnomaly = new ArrayList<>();

        int dbPositions = dbState.getActivePositions().size();
        int exPositions = exchangeState.getPositions().size();
        if (dbPositions < exPositions) {
            for (int i = 0; i < exPositions - dbPositions; i++) {
                createUnknown.add(CreateUnknownAction.builder().entityType(ReconcileEntityType.POSITION).build());
            }
        } else if (dbPositions > exPositions) {
            dbState.getActivePositions().forEach(position -> markClosed.add(markClosed(position)));
            dbState.getActiveOrders().forEach(order -> markClosed.add(markClosed(order)));
            dbState.getActiveAlgoOrders().forEach(algoOrder -> markClosed.add(markClosed(algoOrder)));
        }

        Set<String> dbOrderClientIds = new HashSet<>(
            dbState.getActiveOrders().stream().map(OrderEntity::getExternalId).filter(StringUtils::isNotBlank).toList()
        );
        Set<String> exOrderClientIds = new HashSet<>(
            exchangeState.getOrders().stream().map(item -> item.getInternalId()).filter(StringUtils::isNotBlank).toList()
        );

        for (OrderEntity dbOrder : dbState.getActiveOrders()) {
            if (StringUtils.isNotBlank(dbOrder.getInternalId()) && !exOrderClientIds.contains(dbOrder.getInternalId())) {
                markClosed.add(markClosed(dbOrder));
            }
        }
        exchangeState.getOrders().stream()
            .filter(item -> StringUtils.isBlank(item.getInternalId()) || !dbOrderClientIds.contains(item.getInternalId()))
            .forEach(item -> createUnknown.add(CreateUnknownAction.builder()
                .entityType(ReconcileEntityType.ORDER)
                .clientId(item.getInternalId())
                .exchangeId(item.getExternalId())
                .build()));

        Set<String> dbAlgoClientIds = new HashSet<>(
            dbState.getActiveAlgoOrders().stream().map(AlgoOrderEntity::getExternalId).filter(StringUtils::isNotBlank).toList()
        );
        Set<String> exAlgoClientIds = new HashSet<>(
            exchangeState.getAlgoOrders().stream().map(item -> item.getInternalOrderId()).filter(StringUtils::isNotBlank).toList()
        );

        for (AlgoOrderEntity dbAlgoOrder : dbState.getActiveAlgoOrders()) {
            if (StringUtils.isNotBlank(dbAlgoOrder.getInternalOrderId())
                && !exAlgoClientIds.contains(dbAlgoOrder.getInternalOrderId())) {
                markClosed.add(markClosed(dbAlgoOrder));
            }
        }
        exchangeState.getAlgoOrders().stream()
            .filter(item -> StringUtils.isBlank(item.getInternalOrderId()) || !dbAlgoClientIds.contains(item.getInternalOrderId()))
            .forEach(item -> createUnknown.add(CreateUnknownAction.builder()
                .entityType(ReconcileEntityType.ALGO_ORDER)
                .clientId(item.getInternalOrderId())
                .exchangeId(item.getExternalId())
                .build()));

        if (dbState.getActivePositions().size() > exchangeState.getPositions().size()
            && (exchangeState.getOrders().size() != dbState.getActiveOrders().size()
            || exchangeState.getAlgoOrders().size() != dbState.getActiveAlgoOrders().size())) {
            dbState.getActiveOrders().stream().map(this::markAnomaly).forEach(markAnomaly::add);
            dbState.getActiveAlgoOrders().stream().map(this::markAnomaly).forEach(markAnomaly::add);
        }

        return ReconcilePlan.builder()
            .createUnknown(createUnknown)
            .markClosed(markClosed)
            .markAnomaly(markAnomaly)
            .build();
    }

    private MarkClosedAction markClosed(PositionEntity entity) {
        return MarkClosedAction.builder().entityType(ReconcileEntityType.POSITION).entityId(entity.getId()).build();
    }

    private MarkClosedAction markClosed(OrderEntity entity) {
        return MarkClosedAction.builder().entityType(ReconcileEntityType.ORDER).entityId(entity.getId()).build();
    }

    private MarkClosedAction markClosed(AlgoOrderEntity entity) {
        return MarkClosedAction.builder().entityType(ReconcileEntityType.ALGO_ORDER).entityId(entity.getId()).build();
    }

    private MarkAnomalyAction markAnomaly(OrderEntity entity) {
        return MarkAnomalyAction.builder().entityType(ReconcileEntityType.ORDER).entityId(entity.getId()).build();
    }

    private MarkAnomalyAction markAnomaly(AlgoOrderEntity entity) {
        return MarkAnomalyAction.builder().entityType(ReconcileEntityType.ALGO_ORDER).entityId(entity.getId()).build();
    }
}
