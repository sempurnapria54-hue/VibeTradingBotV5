package com.example.tradingbot.domain.service.reconcile;

import com.example.tradingbot.domain.model.AlgoOrder;
import com.example.tradingbot.persistence.model.AlgoOrderEntity;
import com.example.tradingbot.persistence.model.ExchangeEntity;
import com.example.tradingbot.persistence.model.InstrumentEntity;
import com.example.tradingbot.persistence.model.OrderEntity;
import com.example.tradingbot.persistence.model.PositionEntity;
import com.example.tradingbot.domain.model.Order;
import com.example.tradingbot.domain.service.reconcile.model.DatabaseInstrumentSnapshot;
import com.example.tradingbot.domain.service.reconcile.model.DatabaseSnapshot;
import com.example.tradingbot.persistence.service.AlgoOrderDataService;
import com.example.tradingbot.persistence.service.OrderDataService;
import com.example.tradingbot.persistence.service.PositionDataService;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DatabaseSnapshotBuilder {

    private static final String STATUS_CLOSED = "CLOSED";

    private final PositionDataService positionDataService;
    private final OrderDataService orderDataService;
    private final AlgoOrderDataService algoOrderDataService;

    public DatabaseSnapshot captureDatabaseSnapshot(ExchangeEntity exchange, List<InstrumentEntity> managedInstruments) {
        List<DatabaseInstrumentSnapshot> instruments = managedInstruments.stream()
            .sorted(Comparator.comparing(InstrumentEntity::getExternalId))
            .map(instrument -> {
                List<PositionEntity> positions = positionDataService.findAllByExchangeIdAndInstrumentId(exchange.getId(), instrument.getId()).stream()
                    .filter(position -> !STATUS_CLOSED.equalsIgnoreCase(position.getStatus()))
                    .toList();
                List<OrderEntity> orders = orderDataService.findAllByExchangeIdAndInstrumentId(exchange.getId(), instrument.getId()).stream()
                    .filter(order -> !STATUS_CLOSED.equalsIgnoreCase(order.getStatus()))
                    .toList();
                List<AlgoOrderEntity> algoOrders = algoOrderDataService.findAllByExchangeIdAndInstrumentId(exchange.getId(), instrument.getId()).stream()
                    .filter(order -> !STATUS_CLOSED.equalsIgnoreCase(order.getStatus()))
                    .toList();

                return DatabaseInstrumentSnapshot.builder()
                    .instId(instrument.getExternalId())
                    .instrumentMode(exchange.getStatus())
                    .instrumentStatus(instrument.getStatus())
                    .positionMode(instrument.getPositionMode())
                    .positionsCount(positions.size())
                    .ordersCount(orders.size())
                    .algoOrdersCount(algoOrders.size())
                    .orders(orders.stream()
                        .map(order -> Order.builder()
                            .externalInstrumentId(instrument.getExternalId())
                            .externalId(order.getExternalId())
                            .internalId(order.getInternalId())
                            .build())
                        .toList())
                    .algoOrders(algoOrders.stream()
                        .map(algoOrder -> AlgoOrder.builder()
                            .externalInstrumentId(instrument.getExternalId())
                            .externalId(algoOrder.getExternalId())
                            .internalOrderId(algoOrder.getInternalOrderId())
                            .build())
                        .toList())
                    .build();
            })
            .toList();

        return DatabaseSnapshot.builder()
            .exchangeName(exchange.getName())
            .capturedAtUtcMillis(Instant.now().toEpochMilli())
            .instruments(instruments)
            .build();
    }
}
