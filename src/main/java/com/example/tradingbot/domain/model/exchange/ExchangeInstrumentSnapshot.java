package com.example.tradingbot.domain.model.exchange;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ExchangeInstrumentSnapshot {

    private final String instId;
    private final int positionsCount;
    private final int ordersCount;
    private final int algoOrdersCount;
    private final List<ExchangePosition> positions;
    private final List<ExchangeOrder> orders;
    private final List<ExchangeAlgoOrder> algoOrders;
}
