package com.example.tradingbot.domain.service.reconcile;

import com.example.tradingbot.domain.model.okxproxy.CancelAlgoOrderRequest;
import com.example.tradingbot.domain.model.okxproxy.CancelOrderRequest;
import com.example.tradingbot.domain.model.okxproxy.ClosePositionRequest;
import com.example.tradingbot.domain.service.okxproxy.OkxTradeClientService;
import com.example.tradingbot.domain.service.reconcile.model.AnomalyDecision;
import com.example.tradingbot.domain.service.reconcile.model.CancelFlowResult;
import com.example.tradingbot.domain.service.reconcile.model.ExchangeSnapshot;
import com.example.tradingbot.domain.service.reconcile.model.ExternalAlgoOrder;
import com.example.tradingbot.domain.service.reconcile.model.ExternalOrder;
import com.example.tradingbot.domain.service.reconcile.model.ExternalPosition;
import com.example.tradingbot.domain.service.reconcile.model.InstrumentBucket;
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
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class CancelExchangeFlow {

    private static final String STATUS_HOLD = "HOLD";
    private static final String STATUS_OPEN = "OPEN";
    private static final String POSITION_MODE_NONE = "NONE";
    private static final String STATUS_ANOMALY = "ANOMALY";
    private static final String STATUS_UNKNOWN = "UNKNOWN";

    private final OkxTradeClientService okxTradeClientService;
    private final OkxExchangeSnapshotProvider snapshotProvider;
    private final InstrumentBucketBuilder instrumentBucketBuilder;
    private final ExchangeDataService exchangeDataService;
    private final InstrumentDataService instrumentDataService;
    private final PositionDataService positionDataService;
    private final OrderDataService orderDataService;
    private final AlgoOrderDataService algoOrderDataService;

    @Transactional
    public CancelFlowResult execute(
        Long exchangeId,
        Long instrumentId,
        ExchangeSnapshot snapshot,
        InstrumentBucket bucket,
        AnomalyDecision decision
    ) {
        ExchangeEntity exchange = exchangeDataService.findById(exchangeId)
            .orElseThrow(() -> new EntityNotFoundException("Exchange not found: id=" + exchangeId));
        InstrumentEntity instrument = instrumentDataService.findById(instrumentId)
            .orElseThrow(() -> new EntityNotFoundException("Instrument not found: id=" + instrumentId));

        instrument.setStatus(STATUS_HOLD);
        instrumentDataService.save(instrument);

        int unknownCreated = 0;
        int closedPositions = 0;
        int canceledOrders = 0;
        int canceledAlgoOrders = 0;

        List<PositionEntity> dbPositions = positionDataService.findAllByExchangeIdAndInstrumentId(exchangeId, instrumentId);
        List<OrderEntity> dbOrders = orderDataService.findAllByExchangeIdAndInstrumentId(exchangeId, instrumentId);
        List<AlgoOrderEntity> dbAlgoOrders = algoOrderDataService.findAllByExchangeIdAndInstrumentId(exchangeId, instrumentId);

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

            PositionEntity entity = dbPositions.stream().findFirst().orElse(null);
            if (Objects.isNull(entity)) {
                entity = new PositionEntity();
                entity.setExchange(exchange);
                entity.setInstrument(instrument);
                entity.setStatus(STATUS_UNKNOWN);
                entity.setSide(externalPosition.getSide());
                unknownCreated++;
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
            OrderEntity entity = orderDataService.findByExchangeIdAndInstrumentIdAndClientOrderId(exchangeId, instrumentId, clientOrderId).orElse(null);
            if (Objects.isNull(entity)) {
                entity = new OrderEntity();
                entity.setExchange(exchange);
                entity.setInstrument(instrument);
                entity.setClientOrderId(clientOrderId);
                entity.setExchangeOrderId(externalOrder.getOrdId());
                entity.setStatus(STATUS_UNKNOWN);
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
            AlgoOrderEntity entity = algoOrderDataService.findByExchangeIdAndInstrumentIdAndClientAlgoOrderId(exchangeId, instrumentId, clientAlgoOrderId).orElse(null);
            if (Objects.isNull(entity)) {
                entity = new AlgoOrderEntity();
                entity.setExchange(exchange);
                entity.setInstrument(instrument);
                entity.setClientAlgoOrderId(clientAlgoOrderId);
                entity.setExchangeAlgoOrderId(externalAlgoOrder.getAlgoId());
                entity.setStatus(STATUS_UNKNOWN);
                unknownCreated++;
            }
            entity.setStatus(STATUS_ANOMALY);
            algoOrderDataService.save(entity);
        }

        ExchangeSnapshot refreshed = snapshotProvider.captureSnapshot();
        InstrumentBucket refreshedBucket = instrumentBucketBuilder.buildBuckets(refreshed).stream()
            .filter(item -> Objects.equals(item.getInstrumentName(), bucket.getInstrumentName()))
            .findFirst()
            .orElse(InstrumentBucket.builder()
                .instrumentName(bucket.getInstrumentName())
                .positions(List.of())
                .orders(List.of())
                .algoOrders(List.of())
                .build());
        boolean emptyAfterFlow = refreshedBucket.getPositionsCount() == 0
            && refreshedBucket.getOrdersCount() == 0
            && refreshedBucket.getAlgoOrdersCount() == 0;

        instrument.setPositionMode(emptyAfterFlow ? POSITION_MODE_NONE : instrument.getPositionMode());
        if ("WARN".equalsIgnoreCase(decision.getSeverity()) || "INFO".equalsIgnoreCase(decision.getSeverity())) {
            instrument.setStatus(STATUS_OPEN);
        }
        instrumentDataService.save(instrument);

        return CancelFlowResult.builder()
            .closedPositions(closedPositions)
            .canceledOrders(canceledOrders)
            .canceledAlgoOrders(canceledAlgoOrders)
            .unknownCreated(unknownCreated)
            .emptyAfterFlow(emptyAfterFlow)
            .build();
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
}
