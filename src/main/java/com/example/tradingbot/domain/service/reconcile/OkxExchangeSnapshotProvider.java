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
import com.example.tradingbot.domain.service.reconcile.model.ExchangeSnapshot;
import com.example.tradingbot.domain.service.reconcile.model.ExternalAlgoOrder;
import com.example.tradingbot.domain.service.reconcile.model.ExternalOrder;
import com.example.tradingbot.domain.service.reconcile.model.ExternalPosition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
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

        return ExchangeSnapshot.builder()
                .exchangeName(EXCHANGE_NAME)
                .capturedAtUtcMillis(Instant.now().toEpochMilli())
                .positions(positions.stream()
                        .map(position -> ExternalPosition.builder()
                                .instId(position.getInstrumentId())
                                .side(position.getPositionSide())
                                .build())
                        .toList())
                .orders(orders.stream()
                        .map(order -> ExternalOrder.builder()
                                .instId(order.getInstrumentId())
                                .ordId(order.getOrderId())
                                .clOrdId(order.getClientOrderId())
                                .build())
                        .toList())
                .algoOrders(algoOrders.stream()
                        .map(order -> ExternalAlgoOrder.builder()
                                .instId(order.getInstrumentId())
                                .algoId(order.getAlgoOrderId())
                                .algoClOrdId(order.getClientOrderId())
                                .build())
                        .toList())
                .build();
    }

    private List<Position> capturePositions() {
        try {
            return okxAccountClientService.getPositions(new PositionsRequest());
        } catch (OkxApiException exception) {
            log.error("Failed to capture OKX positions: code={}, msg={}", exception.getCode(), exception.getMessage());
            return List.of();
        }
    }

    private List<Order> captureOrdersPending() {
        try {
            return okxTradeClientService.getOrdersPending(new OrdersPendingRequest());
        } catch (OkxApiException exception) {
            log.error("Failed to capture OKX pending orders: code={}, msg={}", exception.getCode(), exception.getMessage());
            return List.of();
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
                log.error("Failed to capture OKX pending algo orders: ordType={}, code={}, msg={}", orderType, exception.getCode(), exception.getMessage());
            }
        }
        return orders;
    }
}
