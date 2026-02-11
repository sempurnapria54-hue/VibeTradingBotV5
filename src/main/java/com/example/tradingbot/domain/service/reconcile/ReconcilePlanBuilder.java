package com.example.tradingbot.domain.service.reconcile;

import com.example.tradingbot.domain.service.reconcile.model.ExternalAlgoOrder;
import com.example.tradingbot.domain.service.reconcile.model.ExternalOrder;
import com.example.tradingbot.domain.service.reconcile.model.InstrumentBucket;
import com.example.tradingbot.domain.service.reconcile.model.ReconcilePlan;
import com.example.tradingbot.persistence.model.AlgoOrderEntity;
import com.example.tradingbot.persistence.model.OrderEntity;
import com.example.tradingbot.persistence.model.PositionEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class ReconcilePlanBuilder {

    public ReconcilePlan buildPlan(
        InstrumentBucket bucket,
        List<PositionEntity> activePositions,
        List<OrderEntity> activeOrders,
        List<AlgoOrderEntity> activeAlgoOrders
    ) {
        List<ReconcilePlan.ReconcileAction> createMissing = new ArrayList<>();
        List<ReconcilePlan.ReconcileAction> markUnknown = new ArrayList<>();
        List<ReconcilePlan.ReconcileAction> markClosed = new ArrayList<>();

        int exchangePositionCount = bucket.getPositionsCount();
        int exchangeOrderCount = bucket.getOrdersCount();
        int exchangeAlgoCount = bucket.getAlgoOrdersCount();

        int dbPositionCount = activePositions.size();
        int dbOrderCount = activeOrders.size();
        int dbAlgoCount = activeAlgoOrders.size();

        if (dbPositionCount > exchangePositionCount) {
            int surplus = dbPositionCount - exchangePositionCount;
            activePositions.stream()
                .limit(surplus)
                .map(PositionEntity::getId)
                .map(id -> ReconcilePlan.ReconcileAction.mark(ReconcilePlan.EntityType.POSITION, id))
                .forEach(markUnknown::add);
        } else if (dbPositionCount < exchangePositionCount) {
            int missing = exchangePositionCount - dbPositionCount;
            for (int i = 0; i < missing; i++) {
                createMissing.add(ReconcilePlan.ReconcileAction.create(ReconcilePlan.EntityType.POSITION, null, null));
            }
        }

        if (exchangeOrderCount == 0 && dbOrderCount > 0) {
            activeOrders.stream()
                .map(OrderEntity::getId)
                .map(id -> ReconcilePlan.ReconcileAction.mark(ReconcilePlan.EntityType.ORDER, id))
                .forEach(markClosed::add);
        } else if (dbOrderCount > exchangeOrderCount) {
            int surplus = dbOrderCount - exchangeOrderCount;
            activeOrders.stream()
                .limit(surplus)
                .map(OrderEntity::getId)
                .map(id -> ReconcilePlan.ReconcileAction.mark(ReconcilePlan.EntityType.ORDER, id))
                .forEach(markUnknown::add);
        } else if (dbOrderCount < exchangeOrderCount) {
            Set<String> existingClientIds = activeOrders.stream().map(OrderEntity::getClientOrderId).collect(LinkedHashSet::new, Set::add, Set::addAll);
            List<ExternalOrder> missingOrders = bucket.getOrders().stream()
                .filter(order -> !existingClientIds.contains(order.getClOrdId()))
                .toList();
            for (ExternalOrder missingOrder : missingOrders) {
                createMissing.add(ReconcilePlan.ReconcileAction.create(
                    ReconcilePlan.EntityType.ORDER,
                    missingOrder.getOrdId(),
                    missingOrder.getClOrdId()
                ));
            }
        }

        if (exchangeAlgoCount == 0 && dbAlgoCount > 0) {
            activeAlgoOrders.stream()
                .map(AlgoOrderEntity::getId)
                .map(id -> ReconcilePlan.ReconcileAction.mark(ReconcilePlan.EntityType.ALGO_ORDER, id))
                .forEach(markClosed::add);
        } else if (dbAlgoCount > exchangeAlgoCount) {
            int surplus = dbAlgoCount - exchangeAlgoCount;
            activeAlgoOrders.stream()
                .limit(surplus)
                .map(AlgoOrderEntity::getId)
                .map(id -> ReconcilePlan.ReconcileAction.mark(ReconcilePlan.EntityType.ALGO_ORDER, id))
                .forEach(markUnknown::add);
        } else if (dbAlgoCount < exchangeAlgoCount) {
            Set<String> existingClientIds = activeAlgoOrders.stream().map(AlgoOrderEntity::getClientAlgoOrderId).collect(LinkedHashSet::new, Set::add, Set::addAll);
            List<ExternalAlgoOrder> missingAlgoOrders = bucket.getAlgoOrders().stream()
                .filter(algoOrder -> !existingClientIds.contains(algoOrder.getAlgoClOrdId()))
                .toList();
            for (ExternalAlgoOrder missingAlgoOrder : missingAlgoOrders) {
                createMissing.add(ReconcilePlan.ReconcileAction.create(
                    ReconcilePlan.EntityType.ALGO_ORDER,
                    missingAlgoOrder.getAlgoId(),
                    missingAlgoOrder.getAlgoClOrdId()
                ));
            }
        }

        return ReconcilePlan.builder()
            .targetPositionsCount(exchangePositionCount)
            .targetOrdersCount(exchangeOrderCount)
            .targetAlgoOrdersCount(exchangeAlgoCount)
            .createMissing(createMissing)
            .markUnknown(markUnknown)
            .markClosed(markClosed)
            .build();
    }
}
