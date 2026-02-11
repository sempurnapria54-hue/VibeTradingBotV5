package com.example.tradingbot.domain.service.reconcile;

import com.example.tradingbot.domain.service.reconcile.model.ExchangeSnapshot;
import com.example.tradingbot.domain.service.reconcile.model.InstrumentBucket;
import com.example.tradingbot.domain.service.reconcile.model.ReconcilePlan;
import com.example.tradingbot.persistence.model.AlgoOrderEntity;
import com.example.tradingbot.persistence.model.ExchangeEntity;
import com.example.tradingbot.persistence.model.InstrumentEntity;
import com.example.tradingbot.persistence.model.OrderEntity;
import com.example.tradingbot.persistence.model.PositionEntity;
import com.example.tradingbot.persistence.service.AlgoOrderDataService;
import com.example.tradingbot.persistence.service.ExchangeDataService;
import com.example.tradingbot.persistence.service.InstrumentDataService;
import com.example.tradingbot.persistence.service.OrderDataService;
import com.example.tradingbot.persistence.service.PositionDataService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.BooleanUtils;

@Service
@RequiredArgsConstructor
public class CountsOnlySyncEngine {

    private static final String STATUS_UNKNOWN = "UNKNOWN";
    private static final String STATUS_CLOSED = "CLOSED";
    private static final String STATUS_ANOMALY = "ANOMALY";

    private final ReconcilePlanBuilder reconcilePlanBuilder;
    private final ExchangeDataService exchangeDataService;
    private final InstrumentDataService instrumentDataService;
    private final PositionDataService positionDataService;
    private final OrderDataService orderDataService;
    private final AlgoOrderDataService algoOrderDataService;

    @Transactional
    public void syncInstrumentBucket(Long exchangeId, Long instrumentId, ExchangeSnapshot snapshot, InstrumentBucket bucket) {
        ExchangeEntity exchange = exchangeDataService.findById(exchangeId)
            .orElseThrow(() -> new EntityNotFoundException("Exchange not found: id=" + exchangeId));
        InstrumentEntity instrument = instrumentDataService.findById(instrumentId)
            .orElseThrow(() -> new EntityNotFoundException("Instrument not found: id=" + instrumentId));

        List<PositionEntity> positions = positionDataService.findAllByExchangeIdAndInstrumentId(exchangeId, instrumentId);
        List<OrderEntity> orders = orderDataService.findAllByExchangeIdAndInstrumentId(exchangeId, instrumentId);
        List<AlgoOrderEntity> algoOrders = algoOrderDataService.findAllByExchangeIdAndInstrumentId(exchangeId, instrumentId);

        List<PositionEntity> activePositions = positions.stream().filter(this::isActive).toList();
        List<OrderEntity> activeOrders = orders.stream().filter(this::isActive).toList();
        List<AlgoOrderEntity> activeAlgoOrders = algoOrders.stream().filter(this::isActive).toList();

        ReconcilePlan plan = reconcilePlanBuilder.buildPlan(bucket, activePositions, activeOrders, activeAlgoOrders);

        Map<Long, PositionEntity> positionById = positions.stream().collect(Collectors.toMap(PositionEntity::getId, Function.identity()));
        Map<Long, OrderEntity> orderById = orders.stream().collect(Collectors.toMap(OrderEntity::getId, Function.identity()));
        Map<Long, AlgoOrderEntity> algoOrderById = algoOrders.stream().collect(Collectors.toMap(AlgoOrderEntity::getId, Function.identity()));

        for (ReconcilePlan.ReconcileAction action : plan.getMarkClosed()) {
            if (action.getEntityType() == ReconcilePlan.EntityType.ORDER) {
                OrderEntity order = orderById.get(action.getEntityId());
                if (Objects.nonNull(order) && isActive(order)) {
                    order.setStatus(STATUS_CLOSED);
                    orderDataService.save(order);
                }
            }
            if (action.getEntityType() == ReconcilePlan.EntityType.ALGO_ORDER) {
                AlgoOrderEntity algoOrder = algoOrderById.get(action.getEntityId());
                if (Objects.nonNull(algoOrder) && isActive(algoOrder)) {
                    algoOrder.setStatus(STATUS_CLOSED);
                    algoOrderDataService.save(algoOrder);
                }
            }
            if (action.getEntityType() == ReconcilePlan.EntityType.POSITION) {
                PositionEntity position = positionById.get(action.getEntityId());
                if (Objects.nonNull(position) && isActive(position)) {
                    position.setStatus(STATUS_CLOSED);
                    positionDataService.save(position);
                }
            }
        }

        for (ReconcilePlan.ReconcileAction action : plan.getMarkUnknown()) {
            if (action.getEntityType() == ReconcilePlan.EntityType.POSITION) {
                PositionEntity position = positionById.get(action.getEntityId());
                if (Objects.nonNull(position) && isActive(position)) {
                    position.setStatus(STATUS_UNKNOWN);
                    positionDataService.save(position);
                }
            }
            if (action.getEntityType() == ReconcilePlan.EntityType.ORDER) {
                OrderEntity order = orderById.get(action.getEntityId());
                if (Objects.nonNull(order) && isActive(order)) {
                    order.setStatus(STATUS_ANOMALY);
                    orderDataService.save(order);
                }
            }
            if (action.getEntityType() == ReconcilePlan.EntityType.ALGO_ORDER) {
                AlgoOrderEntity algoOrder = algoOrderById.get(action.getEntityId());
                if (Objects.nonNull(algoOrder) && isActive(algoOrder)) {
                    algoOrder.setStatus(STATUS_ANOMALY);
                    algoOrderDataService.save(algoOrder);
                }
            }
        }

        int unknownPositionToCreate = Math.max(0, plan.getTargetPositionsCount() - activePositions.size());
        for (int i = 0; i < unknownPositionToCreate; i++) {
            PositionEntity position = new PositionEntity();
            position.setExchange(exchange);
            position.setInstrument(instrument);
            position.setStatus(STATUS_UNKNOWN);
            positionDataService.save(position);
        }

        plan.getCreateMissing().stream()
            .filter(action -> action.getEntityType() == ReconcilePlan.EntityType.ORDER)
            .forEach(action -> createUnknownOrderIfMissing(exchangeId, instrumentId, exchange, instrument, action));

        plan.getCreateMissing().stream()
            .filter(action -> action.getEntityType() == ReconcilePlan.EntityType.ALGO_ORDER)
            .forEach(action -> createUnknownAlgoOrderIfMissing(exchangeId, instrumentId, exchange, instrument, action));
    }

    private void createUnknownOrderIfMissing(
        Long exchangeId,
        Long instrumentId,
        ExchangeEntity exchange,
        InstrumentEntity instrument,
        ReconcilePlan.ReconcileAction action
    ) {
        String clientOrderId = resolveOrderClientId(action.getClientId(), action.getExternalId());
        if (orderDataService.findByExchangeIdAndInstrumentIdAndClientOrderId(exchangeId, instrumentId, clientOrderId).isPresent()) {
            return;
        }
        OrderEntity order = new OrderEntity();
        order.setExchange(exchange);
        order.setInstrument(instrument);
        order.setClientOrderId(clientOrderId);
        order.setExchangeOrderId(action.getExternalId());
        order.setStatus(STATUS_UNKNOWN);
        orderDataService.save(order);
    }

    private void createUnknownAlgoOrderIfMissing(
        Long exchangeId,
        Long instrumentId,
        ExchangeEntity exchange,
        InstrumentEntity instrument,
        ReconcilePlan.ReconcileAction action
    ) {
        String clientAlgoOrderId = resolveAlgoClientId(action.getClientId(), action.getExternalId());
        if (algoOrderDataService.findByExchangeIdAndInstrumentIdAndClientAlgoOrderId(exchangeId, instrumentId, clientAlgoOrderId).isPresent()) {
            return;
        }
        AlgoOrderEntity algoOrder = new AlgoOrderEntity();
        algoOrder.setExchange(exchange);
        algoOrder.setInstrument(instrument);
        algoOrder.setClientAlgoOrderId(clientAlgoOrderId);
        algoOrder.setExchangeAlgoOrderId(action.getExternalId());
        algoOrder.setStatus(STATUS_UNKNOWN);
        algoOrderDataService.save(algoOrder);
    }

    private String resolveOrderClientId(String clOrdId, String ordId) {
        if (StringUtils.isNotBlank(clOrdId)) {
            return clOrdId;
        }
        if (StringUtils.isNotBlank(ordId)) {
            return "unknown-ord-" + ordId;
        }
        return "unknown-ord-no-id";
    }

    private String resolveAlgoClientId(String algoClOrdId, String algoId) {
        if (StringUtils.isNotBlank(algoClOrdId)) {
            return algoClOrdId;
        }
        if (StringUtils.isNotBlank(algoId)) {
            return "unknown-algo-" + algoId;
        }
        return "unknown-algo-no-id";
    }

    private boolean isActive(PositionEntity entity) {
        return BooleanUtils.isFalse(isClosed(entity.getStatus()));
    }

    private boolean isActive(OrderEntity entity) {
        return BooleanUtils.isFalse(isClosed(entity.getStatus()));
    }

    private boolean isActive(AlgoOrderEntity entity) {
        return BooleanUtils.isFalse(isClosed(entity.getStatus()));
    }

    private boolean isClosed(String status) {
        return Objects.equals(STATUS_CLOSED, status);
    }
}
