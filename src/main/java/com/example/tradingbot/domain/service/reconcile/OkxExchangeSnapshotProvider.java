package com.example.tradingbot.domain.service.reconcile;

import com.example.tradingbot.client.model.okx.request.OrdersAlgoPendingRequest;
import com.example.tradingbot.client.model.okx.request.OrdersPendingRequest;
import com.example.tradingbot.client.model.okx.request.PositionsRequest;
import com.example.tradingbot.client.model.okx.response.TickerRequest;
import com.example.tradingbot.client.exception.ExternalApiException;
import com.example.tradingbot.domain.model.*;
import com.example.tradingbot.domain.model.AlgoOrder;
import com.example.tradingbot.domain.model.snapshot.InstrumentSnapshot;
import com.example.tradingbot.domain.model.snapshot.ExchangeSnapshot;
import com.example.tradingbot.domain.service.okxproxy.OkxAccountClientService;
import com.example.tradingbot.domain.service.okxproxy.OkxMarketClientService;
import com.example.tradingbot.domain.service.okxproxy.OkxTradeClientService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OkxExchangeSnapshotProvider {

    private static final String EXCHANGE_NAME = "OKX";
    private static final List<String> ALGO_ORDER_TYPES = List.of("conditional", "oco", "move_order_stop");

    private final OkxAccountClientService okxAccountClientService;
    private final OkxTradeClientService okxTradeClientService;
    private final OkxMarketClientService okxMarketClientService;

    public ExchangeSnapshot captureExchangeSnapshot(List<String> managedInstIds) {
        List<Position> positions = capturePositions();
        List<Order> orders = captureOrdersPending();
        List<AlgoOrder> algoOrders = captureAlgoOrdersPending();

        LinkedHashSet<String> instIds = new LinkedHashSet<>();
        positions.stream().map(Position::getExternalInstrumentId).forEach(instIds::add);
        orders.stream().map(Order::getExternalInstrumentId).forEach(instIds::add);
        algoOrders.stream().map(AlgoOrder::getExternalInstrumentId).forEach(instIds::add);
        managedInstIds.stream().filter(StringUtils::isNotBlank).forEach(instIds::add);

        List<InstrumentSnapshot> instruments = instIds.stream()
            .filter(StringUtils::isNotBlank)
            .map(instId -> {
                List<Position> instrumentPositions = positions.stream().filter(position -> instId.equals(position.getExternalInstrumentId())).toList();
                List<Order> instrumentOrders = orders.stream().filter(order -> instId.equals(order.getExternalInstrumentId())).toList();
                List<AlgoOrder> instrumentAlgoOrders = algoOrders.stream().filter(order -> instId.equals(order.getExternalInstrumentId())).toList();
                return InstrumentSnapshot.builder()
                    .externalId(instId)
                    .positionsCount(instrumentPositions.size())
                    .ordersCount(instrumentOrders.size())
                    .algoOrdersCount(instrumentAlgoOrders.size())
                    .positions(instrumentPositions)
                    .orders(instrumentOrders)
                    .algoOrders(instrumentAlgoOrders)
                    .build();
            })
            .sorted(Comparator.comparing(InstrumentSnapshot::getExternalId))
            .toList();

        Map<String, PriceTicker> tickersByInstId = new LinkedHashMap<>();
        for (String instId : managedInstIds) {
            if (StringUtils.isBlank(instId)) {
                continue;
            }
            PriceTicker ticker = captureTicker(instId);
            if (ticker == null) {
                continue;
            }
            tickersByInstId.put(instId, ticker);
        }

        return ExchangeSnapshot.builder()
            .exchangeName(EXCHANGE_NAME)
            .capturedAtUtcMillis(Instant.now().toEpochMilli())
            .instruments(instruments)
            .tickersByInstId(tickersByInstId)
            .build();
    }

    public ExchangeSnapshot captureSnapshot() {
        return captureExchangeSnapshot(List.of());
    }

    public InstrumentSnapshot refreshInstrumentSnapshot(String instId) {
        ExchangeSnapshot snapshot = captureExchangeSnapshot(List.of(instId));
        return snapshot.getInstruments().stream()
            .filter(item -> instId.equals(item.getExternalId()))
            .findFirst()
            .orElse(InstrumentSnapshot.builder()
                .externalId(instId)
                .positionsCount(0)
                .ordersCount(0)
                .algoOrdersCount(0)
                .positions(List.of())
                .orders(List.of())
                .algoOrders(List.of())
                .build());
    }

    private List<Position> capturePositions() {
        try {
            return okxAccountClientService.getPositions(new PositionsRequest());
        } catch (ExternalApiException exception) {
            throw new ReconcileExchangeSnapshotException("Failed to capture OKX positions", exception);
        }
    }

    private List<Order> captureOrdersPending() {
        try {
            return okxTradeClientService.getOrdersPending(new OrdersPendingRequest());
        } catch (ExternalApiException exception) {
            throw new ReconcileExchangeSnapshotException("Failed to capture OKX pending orders", exception);
        }
    }

    private List<AlgoOrder> captureAlgoOrdersPending() {
        List<AlgoOrder> orders = new ArrayList<>();
        for (String orderType : ALGO_ORDER_TYPES) {
            OrdersAlgoPendingRequest request = new OrdersAlgoPendingRequest();
            request.setOrderType(orderType);
            try {
                orders.addAll(okxTradeClientService.getOrdersAlgoPending(request));
            } catch (ExternalApiException exception) {
                throw new ReconcileExchangeSnapshotException("Failed to capture OKX pending algo orders", exception);
            }
        }
        return orders;
    }

    private PriceTicker captureTicker(String instId) {
        TickerRequest request = new TickerRequest();
        request.setInstrumentId(instId);
        try {
            return okxMarketClientService.getTicker(request).stream().findFirst().orElse(null);
        } catch (ExternalApiException exception) {
            throw new ReconcileExchangeSnapshotException("Failed to capture OKX ticker, instId=" + instId, exception);
        }
    }
}
