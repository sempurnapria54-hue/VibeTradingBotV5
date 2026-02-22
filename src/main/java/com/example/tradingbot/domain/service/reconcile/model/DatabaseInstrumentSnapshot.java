package com.example.tradingbot.domain.service.reconcile.model;

import com.example.tradingbot.domain.model.AlgoOrder;
import com.example.tradingbot.domain.model.Order;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DatabaseInstrumentSnapshot {

    private final String instId;
    private final String instrumentMode;
    private final String instrumentStatus;
    private final String positionMode;
    private final int positionsCount;
    private final int ordersCount;
    private final int algoOrdersCount;
    private final List<Order> orders;
    private final List<AlgoOrder> algoOrders;
}
