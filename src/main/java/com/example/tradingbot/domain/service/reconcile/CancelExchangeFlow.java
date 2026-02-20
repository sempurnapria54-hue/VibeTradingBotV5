package com.example.tradingbot.domain.service.reconcile;

import com.example.tradingbot.config.ReconcileProperties;
import com.example.tradingbot.domain.model.okxproxy.CancelAlgoOrderRequest;
import com.example.tradingbot.domain.model.okxproxy.CancelOrderRequest;
import com.example.tradingbot.domain.model.okxproxy.ClosePositionRequest;
import com.example.tradingbot.domain.service.okxproxy.OkxTradeClientService;
import com.example.tradingbot.domain.service.reconcile.model.AnomalyDecision;
import com.example.tradingbot.domain.service.reconcile.model.CancelFlowResult;
import com.example.tradingbot.domain.service.reconcile.model.ExchangeInstrumentSnapshot;
import com.example.tradingbot.domain.service.reconcile.model.ExternalAlgoOrder;
import com.example.tradingbot.domain.service.reconcile.model.ExternalOrder;
import com.example.tradingbot.domain.service.reconcile.model.ExternalPosition;
import com.example.tradingbot.domain.service.reconcile.model.InstrumentBucket;
import com.example.tradingbot.domain.model.entity.AlgoOrderEntity;
import com.example.tradingbot.domain.model.entity.InstrumentEntity;
import com.example.tradingbot.domain.model.entity.OrderEntity;
import com.example.tradingbot.domain.model.entity.PositionEntity;
import com.example.tradingbot.persistence.service.AlgoOrderDataService;
import com.example.tradingbot.persistence.service.InstrumentDataService;
import com.example.tradingbot.persistence.service.OrderDataService;
import com.example.tradingbot.persistence.service.PositionDataService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CancelExchangeFlow {

    private static final String STATUS_HOLD = "HOLD";
    private static final String STATUS_OPEN = "OPEN";
    private static final String POSITION_MODE_NONE = "NONE";
    private static final String POSITION_MODE_OPEN = "OPEN";
    private static final String STATUS_ANOMALY = "ANOMALY";

    private final ReconcileProperties reconcileProperties;
    private final OkxTradeClientService okxTradeClientService;
    private final OkxExchangeSnapshotProvider snapshotProvider;
    private final InstrumentDataService instrumentDataService;
    private final PositionDataService positionDataService;
    private final OrderDataService orderDataService;
    private final AlgoOrderDataService algoOrderDataService;

    @Transactional
    public CancelFlowResult execute(InstrumentBucket bucket, AnomalyDecision decision) {
        if (!reconcileProperties.getCancelFlow().isEnabled() || !decision.isShouldCancelFlow()) {
            return CancelFlowResult.builder()
                .closedPositions(0)
                .canceledOrders(0)
                .canceledAlgoOrders(0)
                .unknownCreated(0)
                .emptyAfterFlow(false)
                .flowExecuted(false)
                .currentExchangeState(toExchangeState(bucket))
                .build();
        }

        if (Objects.isNull(bucket.getDbState()) || Objects.isNull(bucket.getDbState().getInstrument())) {
            return CancelFlowResult.builder()
                .closedPositions(0)
                .canceledOrders(0)
                .canceledAlgoOrders(0)
                .unknownCreated(0)
                .emptyAfterFlow(false)
                .flowExecuted(false)
                .currentExchangeState(toExchangeState(bucket))
                .build();
        }

        InstrumentEntity instrument = instrumentDataService.findById(bucket.getDbState().getInstrument().getId())
            .orElseThrow(() -> new EntityNotFoundException("Instrument not found: id=" + bucket.getDbState().getInstrument().getId()));

        instrument.setStatus(STATUS_HOLD);
        instrumentDataService.save(instrument);

        int unknownCreated = 0;
        int closedPositions = 0;
        int canceledOrders = 0;
        int canceledAlgoOrders = 0;

        List<PositionEntity> dbPositions = positionDataService.findAllByExchangeIdAndInstrumentId(instrument.getExchangeId(), instrument.getId());

        for (ExternalPosition externalPosition : bucket.getPositions()) {
            ClosePositionRequest request = new ClosePositionRequest();
            request.setInstrumentId(externalPosition.getInstId());
            request.setPositionSide(externalPosition.getSide());
            try {
                okxTradeClientService.closePosition(request);
                closedPositions++;
            } catch (Exception exception) {
                log.warn("CancelFlow closePosition failed: instId={}, reason={}", externalPosition.getInstId(), exception.getMessage());
            }

            PositionEntity entity = dbPositions.stream()
                .filter(item -> StringUtils.equalsIgnoreCase(item.getSide(), externalPosition.getSide()))
                .findFirst()
                .orElse(null);
            if (Objects.isNull(entity)) {
                entity = new PositionEntity();
                entity.setExchange(instrument.getExchange());
                entity.setInstrument(instrument);
                entity.setSide(externalPosition.getSide());
                unknownCreated++;
                dbPositions.add(entity);
            }
            entity.setStatus(STATUS_ANOMALY);
            positionDataService.save(entity);
        }

        for (ExternalOrder externalOrder : bucket.getOrders()) {
            CancelOrderRequest request = new CancelOrderRequest();
            request.setInstrumentId(externalOrder.getInstId());
            request.setOrderId(externalOrder.getOrdId());
            request.setClientOrderId(externalOrder.getClOrdId());
            try {
                okxTradeClientService.cancelOrder(request);
                canceledOrders++;
            } catch (Exception exception) {
                log.warn("CancelFlow cancelOrder failed: instId={}, ordId={}, reason={}", externalOrder.getInstId(), externalOrder.getOrdId(), exception.getMessage());
            }

            String clientOrderId = resolveOrderClientId(externalOrder.getClOrdId(), externalOrder.getOrdId());
            OrderEntity entity = orderDataService.findByExchangeIdAndInstrumentIdAndClientOrderId(instrument.getExchangeId(), instrument.getId(), clientOrderId).orElse(null);
            if (Objects.isNull(entity)) {
                entity = new OrderEntity();
                entity.setInstrument(instrument);
                entity.setClientOrderId(clientOrderId);
                entity.setExchangeOrderId(externalOrder.getOrdId());
                unknownCreated++;
            }
            entity.setStatus(STATUS_ANOMALY);
            orderDataService.save(entity);
        }

        for (ExternalAlgoOrder externalAlgoOrder : bucket.getAlgoOrders()) {
            CancelAlgoOrderRequest request = new CancelAlgoOrderRequest();
            request.setInstrumentId(externalAlgoOrder.getInstId());
            request.setAlgoOrderId(externalAlgoOrder.getAlgoId());
            try {
                okxTradeClientService.cancelAlgoOrder(request);
                canceledAlgoOrders++;
            } catch (Exception exception) {
                log.warn("CancelFlow cancelAlgoOrder failed: instId={}, algoId={}, reason={}", externalAlgoOrder.getInstId(), externalAlgoOrder.getAlgoId(), exception.getMessage());
            }

            String clientAlgoOrderId = resolveAlgoClientId(externalAlgoOrder.getAlgoClOrdId(), externalAlgoOrder.getAlgoId());
            AlgoOrderEntity entity = algoOrderDataService.findByExchangeIdAndInstrumentIdAndClientAlgoOrderId(instrument.getExchangeId(), instrument.getId(), clientAlgoOrderId).orElse(null);
            if (Objects.isNull(entity)) {
                entity = new AlgoOrderEntity();
                entity.setInstrument(instrument);
                entity.setClientAlgoOrderId(clientAlgoOrderId);
                entity.setExchangeAlgoOrderId(externalAlgoOrder.getAlgoId());
                unknownCreated++;
            }
            entity.setStatus(STATUS_ANOMALY);
            algoOrderDataService.save(entity);
        }

        ExchangeInstrumentSnapshot refreshed = snapshotProvider.refreshInstrumentSnapshot(bucket.getInstrumentName());
        boolean emptyAfterFlow = refreshed.getPositionsCount() == 0
            && refreshed.getOrdersCount() == 0
            && refreshed.getAlgoOrdersCount() == 0;

        instrument.setPositionMode(refreshed.getPositionsCount() > 0 ? POSITION_MODE_OPEN : POSITION_MODE_NONE);
        if ("NON_CRITICAL".equalsIgnoreCase(decision.getSeverity())) {
            instrument.setStatus(STATUS_OPEN);
        }
        instrumentDataService.save(instrument);

        return CancelFlowResult.builder()
            .closedPositions(closedPositions)
            .canceledOrders(canceledOrders)
            .canceledAlgoOrders(canceledAlgoOrders)
            .unknownCreated(unknownCreated)
            .emptyAfterFlow(emptyAfterFlow)
            .flowExecuted(true)
            .currentExchangeState(refreshed)
            .build();
    }

    private ExchangeInstrumentSnapshot toExchangeState(InstrumentBucket bucket) {
        return ExchangeInstrumentSnapshot.builder()
            .instId(bucket.getInstrumentName())
            .positionsCount(bucket.getPositionsCount())
            .ordersCount(bucket.getOrdersCount())
            .algoOrdersCount(bucket.getAlgoOrdersCount())
            .positions(bucket.getPositions())
            .orders(bucket.getOrders())
            .algoOrders(bucket.getAlgoOrders())
            .build();
    }

    private String resolveOrderClientId(String clOrdId, String ordId) {
        if (StringUtils.isNotBlank(clOrdId)) {
            return clOrdId;
        }
        if (StringUtils.isNotBlank(ordId)) {
            return "unknown-ord-" + ordId;
        }
        return "unknown-ord-" + UUID.randomUUID();
    }

    private String resolveAlgoClientId(String algoClOrdId, String algoId) {
        if (StringUtils.isNotBlank(algoClOrdId)) {
            return algoClOrdId;
        }
        if (StringUtils.isNotBlank(algoId)) {
            return "unknown-algo-" + algoId;
        }
        return "unknown-algo-" + UUID.randomUUID();
    }
}
