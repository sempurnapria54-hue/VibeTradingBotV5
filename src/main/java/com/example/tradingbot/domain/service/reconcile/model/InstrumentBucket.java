package com.example.tradingbot.domain.service.reconcile.model;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class InstrumentBucket {

    private final String instrumentName;
    private final DbInstrumentState dbState;
    private final List<ExternalPosition> positions;
    private final List<ExternalOrder> orders;
    private final List<ExternalAlgoOrder> algoOrders;

    public int getPositionsCount() {
        return positions.size();
    }

    public int getOrdersCount() {
        return orders.size();
    }

    public int getAlgoOrdersCount() {
        return algoOrders.size();
    }
}
