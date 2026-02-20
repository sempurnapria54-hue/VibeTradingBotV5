package com.example.tradingbot.domain.service.reconcile;

import com.example.tradingbot.client.okx.OkxApiException;
import com.example.tradingbot.domain.model.okxproxy.AlgoOrder;
import com.example.tradingbot.domain.model.okxproxy.Order;
import com.example.tradingbot.domain.model.okxproxy.OrdersAlgoPendingRequest;
import com.example.tradingbot.domain.model.okxproxy.OrdersPendingRequest;
import com.example.tradingbot.domain.model.okxproxy.Position;
import com.example.tradingbot.domain.model.okxproxy.PositionsRequest;
import com.example.tradingbot.domain.model.okxproxy.PriceTicker;
import com.example.tradingbot.domain.model.okxproxy.TickerRequest;
import com.example.tradingbot.domain.service.okxproxy.OkxAccountClientService;
import com.example.tradingbot.domain.service.okxproxy.OkxMarketClientService;
import com.example.tradingbot.domain.service.okxproxy.OkxTradeClientService;
import com.example.tradingbot.domain.model.exchange.ExchangeInstrumentSnapshot;
import com.example.tradingbot.domain.model.exchange.ExchangeSnapshot;
import com.example.tradingbot.domain.model.exchange.ExternalAlgoOrder;
import com.example.tradingbot.domain.model.exchange.ExternalOrder;
import com.example.tradingbot.domain.model.exchange.ExternalPosition;
import com.example.tradingbot.domain.model.exchange.ExternalTicker;
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

        List<ExternalPosition> externalPositions = positions.stream()
            .map(position -> ExternalPosition.builder()
                .instId(position.getInstrumentId())
                .side(position.getPositionSide())
                .pos(position.getPositionSize())
                .avgPx(position.getAveragePrice())
                .markPx(position.getMarkPrice())
                .liqPx(position.getLiquidationPrice())
                .lever(position.getLeverage())
                .mgnMode(position.getMarginMode())
                .upl(position.getUnrealizedProfit())
                .uTime(position.getUpdateTime())
                .build())
            .toList();
        List<ExternalOrder> externalOrders = orders.stream()
            .map(order -> ExternalOrder.builder()
                .instId(order.getInstrumentId())
                .ordId(order.getOrderId())
                .clOrdId(order.getClientOrderId())
                .state(order.getState())
                .ordType(order.getOrderType())
                .px(order.getPrice())
                .sz(order.getSize())
                .fillSz(order.getAccumulatedFillSize())
                .avgPx(order.getAveragePrice())
                .fee(order.getFee())
                .cTime(order.getCreateTime())
                .uTime(order.getUpdateTime())
                .build())
            .toList();
        List<ExternalAlgoOrder> externalAlgoOrders = algoOrders.stream()
            .map(order -> ExternalAlgoOrder.builder()
                .instId(order.getInstrumentId())
                .algoId(order.getAlgoOrderId())
                .algoClOrdId(order.getClientOrderId())
                .state(order.getState())
                .algoType(order.getOrderType())
                .sz(order.getSize())
                .triggerPx(order.getTriggerPrice())
                .ordPx(order.getOrderPrice())
                .tpTriggerPx(order.getTakeProfitTriggerPrice())
                .tpOrdPx(order.getTakeProfitOrderPrice())
                .slTriggerPx(order.getStopLossTriggerPrice())
                .slOrdPx(order.getStopLossOrderPrice())
                .callbackRatio(order.getCallbackRatio())
                .callbackSpread(order.getCallbackSpread())
                .cTime(order.getCreateTime())
                .uTime(order.getUpdateTime())
                .build())
            .toList();

        LinkedHashSet<String> instIds = new LinkedHashSet<>();
        externalPositions.stream().map(ExternalPosition::getInstId).forEach(instIds::add);
        externalOrders.stream().map(ExternalOrder::getInstId).forEach(instIds::add);
        externalAlgoOrders.stream().map(ExternalAlgoOrder::getInstId).forEach(instIds::add);
        managedInstIds.stream().filter(StringUtils::isNotBlank).forEach(instIds::add);

        List<ExchangeInstrumentSnapshot> instruments = instIds.stream()
            .filter(StringUtils::isNotBlank)
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

        Map<String, ExternalTicker> tickersByInstId = new LinkedHashMap<>();
        for (String instId : managedInstIds) {
            if (StringUtils.isBlank(instId)) {
                continue;
            }
            PriceTicker ticker = captureTicker(instId);
            if (ticker == null) {
                continue;
            }
            tickersByInstId.put(instId, ExternalTicker.builder()
                .instId(instId)
                .last(ticker.getLastPrice())
                .markPx(ticker.getMarkPrice())
                .idxPx(ticker.getIndexPrice())
                .ts(ticker.getTimestamp())
                .build());
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

    private PriceTicker captureTicker(String instId) {
        TickerRequest request = new TickerRequest();
        request.setInstrumentId(instId);
        try {
            return okxMarketClientService.getTicker(request).stream().findFirst().orElse(null);
        } catch (OkxApiException exception) {
            throw new ReconcileExchangeSnapshotException("Failed to capture OKX ticker, instId=" + instId, exception);
        }
    }
}
