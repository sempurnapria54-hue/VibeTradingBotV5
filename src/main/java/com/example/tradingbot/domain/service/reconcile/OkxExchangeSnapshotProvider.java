package com.example.tradingbot.domain.service.reconcile;

import com.example.tradingbot.client.okx.OkxApiException;
import com.example.tradingbot.domain.model.okxproxy.AlgoOrder;
import com.example.tradingbot.domain.model.okxproxy.Order;
import com.example.tradingbot.domain.model.okxproxy.OrdersAlgoPendingRequest;
import com.example.tradingbot.domain.model.okxproxy.OrdersPendingRequest;
import com.example.tradingbot.domain.model.okxproxy.Position;
import com.example.tradingbot.domain.model.okxproxy.PositionsRequest;
import com.example.tradingbot.domain.service.okxproxy.OkxAccountClientService;
import com.example.tradingbot.domain.service.okxproxy.OkxTradeClientService;
import com.example.tradingbot.domain.service.reconcile.model.ExchangeInstrumentSnapshot;
import com.example.tradingbot.domain.service.reconcile.model.ExchangeSnapshot;
import com.example.tradingbot.domain.service.reconcile.model.ExternalAlgoOrder;
import com.example.tradingbot.domain.service.reconcile.model.ExternalOrder;
import com.example.tradingbot.domain.service.reconcile.model.ExternalPosition;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OkxExchangeSnapshotProvider {

    private static final String EXCHANGE_NAME = "OKX";
    private static final List<String> ALGO_ORDER_TYPES = List.of("conditional", "oco", "move_order_stop");

    private final OkxAccountClientService okxAccountClientService;
    private final OkxTradeClientService okxTradeClientService;

    public ExchangeSnapshot captureSnapshot() {
        List<Position> positions = capturePositions();
        List<Order> orders = captureOrdersPending();
        List<AlgoOrder> algoOrders = captureAlgoOrdersPending();

        List<ExternalPosition> externalPositions = positions.stream()
            .map(position -> ExternalPosition.builder()
                .instId(position.getInstrumentId())
                .side(position.getPositionSide())
                .build())
            .toList();
        List<ExternalOrder> externalOrders = orders.stream()
            .map(order -> ExternalOrder.builder()
                .instId(order.getInstrumentId())
                .ordId(order.getOrderId())
                .clOrdId(order.getClientOrderId())
                .build())
            .toList();
        List<ExternalAlgoOrder> externalAlgoOrders = algoOrders.stream()
            .map(order -> ExternalAlgoOrder.builder()
                .instId(order.getInstrumentId())
                .algoId(order.getAlgoOrderId())
                .algoClOrdId(order.getClientOrderId())
                .build())
            .toList();

        LinkedHashSet<String> instIds = new LinkedHashSet<>();
        externalPositions.stream().map(ExternalPosition::getInstId).forEach(instIds::add);
        externalOrders.stream().map(ExternalOrder::getInstId).forEach(instIds::add);
        externalAlgoOrders.stream().map(ExternalAlgoOrder::getInstId).forEach(instIds::add);

        List<ExchangeInstrumentSnapshot> instruments = instIds.stream()
            .filter(instId -> instId != null && !instId.isBlank())
            .map(instId -> {
                List<ExternalPosition> instrumentPositions = externalPositions.stream().filter(position -> instId.equals(position.getInstId())).toList();
                List<ExternalOrder> instrumentOrders = externalOrders.stream().filter(order -> instId.equals(order.getInstId())).toList();
                List<ExternalAlgoOrder> instrumentAlgoOrders = externalAlgoOrders.stream().filter(order -> instId.equals(order.getInstId())).toList();
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

        return ExchangeSnapshot.builder()
            .exchangeName(EXCHANGE_NAME)
            .capturedAtUtcMillis(Instant.now().toEpochMilli())
            .instruments(instruments)
            .build();
    }


    public ExchangeInstrumentSnapshot refreshInstrumentSnapshot(String instId) {
        ExchangeSnapshot snapshot = captureSnapshot();
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
    private List<Position> capturePositions() {
        try {
            return okxAccountClientService.getPositions(new PositionsRequest());
        } catch (OkxApiException exception) {
            throw new ReconcileExchangeSnapshotException("Failed to capture OKX positions", exception);
        }
    }

    private List<Order> captureOrdersPending() {
        try {
            return okxTradeClientService.getOrdersPending(new OrdersPendingRequest());
        } catch (OkxApiException exception) {
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
            } catch (OkxApiException exception) {
                throw new ReconcileExchangeSnapshotException("Failed to capture OKX pending algo orders", exception);
            }
        }
        return orders;
    }
}
