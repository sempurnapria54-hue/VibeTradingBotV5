package com.example.tradingbot.domain.service.reconcile.model;

import com.example.tradingbot.domain.model.exchange.ExchangeAlgoOrder;
import com.example.tradingbot.domain.model.exchange.ExchangeOrder;
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
    private final List<ExchangeOrder> orders;
    private final List<ExchangeAlgoOrder> algoOrders;
}
