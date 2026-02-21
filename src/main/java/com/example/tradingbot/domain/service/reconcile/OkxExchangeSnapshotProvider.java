package com.example.tradingbot.domain.service.reconcile;

import com.example.tradingbot.client.model.okx.OrdersAlgoPendingRequest;
import com.example.tradingbot.client.model.okx.OrdersPendingRequest;
import com.example.tradingbot.client.model.okx.PositionsRequest;
import com.example.tradingbot.client.model.okx.TickerRequest;
import com.example.tradingbot.client.okx.OkxApiException;
import com.example.tradingbot.domain.model.exchange.ExchangeAlgoOrder;
import com.example.tradingbot.domain.model.exchange.ExchangeInstrumentSnapshot;
import com.example.tradingbot.domain.model.exchange.ExchangeOrder;
import com.example.tradingbot.domain.model.exchange.ExchangePosition;
import com.example.tradingbot.domain.model.exchange.ExchangePriceTicker;
import com.example.tradingbot.domain.model.exchange.ExchangeSnapshot;
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
        List<ExchangePosition> positions = capturePositions();
        List<ExchangeOrder> orders = captureOrdersPending();
        List<ExchangeAlgoOrder> algoOrders = captureAlgoOrdersPending();

        LinkedHashSet<String> instIds = new LinkedHashSet<>();
        positions.stream().map(ExchangePosition::getInstrumentId).forEach(instIds::add);
        orders.stream().map(ExchangeOrder::getInstrumentId).forEach(instIds::add);
        algoOrders.stream().map(ExchangeAlgoOrder::getInstrumentId).forEach(instIds::add);
        managedInstIds.stream().filter(StringUtils::isNotBlank).forEach(instIds::add);

        List<ExchangeInstrumentSnapshot> instruments = instIds.stream()
            .filter(StringUtils::isNotBlank)
            .map(instId -> {
                List<ExchangePosition> instrumentPositions = positions.stream().filter(position -> instId.equals(position.getInstrumentId())).toList();
                List<ExchangeOrder> instrumentOrders = orders.stream().filter(order -> instId.equals(order.getInstrumentId())).toList();
                List<ExchangeAlgoOrder> instrumentAlgoOrders = algoOrders.stream().filter(order -> instId.equals(order.getInstrumentId())).toList();
                return ExchangeInstrumentSnapshot.builder()
                    .instId(instId)
                    .positionsCount(instrumentPositions.size())
                    .ordersCount(instrumentOrders.size())
                    .algoOrdersCount(instrumentAlgoOrders.size())
                    .positions(instrumentPositions)
                    .orders(instrumentOrders)
                    .algoOrders(instrumentAlgoOrders)
                    .build();
            })
            .sorted(Comparator.comparing(ExchangeInstrumentSnapshot::getInstId))
            .toList();

        Map<String, ExchangePriceTicker> tickersByInstId = new LinkedHashMap<>();
        for (String instId : managedInstIds) {
            if (StringUtils.isBlank(instId)) {
                continue;
            }
            ExchangePriceTicker ticker = captureTicker(instId);
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

    public ExchangeInstrumentSnapshot refreshInstrumentSnapshot(String instId) {
        ExchangeSnapshot snapshot = captureExchangeSnapshot(List.of(instId));
        return snapshot.getInstruments().stream()
            .filter(item -> instId.equals(item.getInstId()))
            .findFirst()
            .orElse(ExchangeInstrumentSnapshot.builder()
                .instId(instId)
                .positionsCount(0)
                .ordersCount(0)
                .algoOrdersCount(0)
                .positions(List.of())
                .orders(List.of())
                .algoOrders(List.of())
                .build());
    }

    private List<ExchangePosition> capturePositions() {
        try {
            return okxAccountClientService.getPositions(new PositionsRequest());
        } catch (OkxApiException exception) {
            throw new ReconcileExchangeSnapshotException("Failed to capture OKX positions", exception);
        }
    }

    private List<ExchangeOrder> captureOrdersPending() {
        try {
            return okxTradeClientService.getOrdersPending(new OrdersPendingRequest());
        } catch (OkxApiException exception) {
            throw new ReconcileExchangeSnapshotException("Failed to capture OKX pending orders", exception);
        }
    }

    private List<ExchangeAlgoOrder> captureAlgoOrdersPending() {
        List<ExchangeAlgoOrder> orders = new ArrayList<>();
        for (String orderType : ALGO_ORDER_TYPES) {
            OrdersAlgoPendingRequest request = new OrdersAlgoPendingRequest();
            request.setOrderType(orderType);
            try {
                orders.addAll(okxTradeClientService.getOrdersAlgoPending(request));
            } catch (OkxApiException exception) {
                throw new ReconcileExchangeSnapshotException("Failed to capture OKX pending algo orders", exception);
            }
        }
        return orders;
    }

    private ExchangePriceTicker captureTicker(String instId) {
        TickerRequest request = new TickerRequest();
        request.setInstrumentId(instId);
        try {
            return okxMarketClientService.getTicker(request).stream().findFirst().orElse(null);
        } catch (OkxApiException exception) {
            throw new ReconcileExchangeSnapshotException("Failed to capture OKX ticker, instId=" + instId, exception);
        }
    }
}
