package com.example.tradingbot.domain.service.reconcile.model;

import com.example.tradingbot.domain.model.AlgoOrder;
import com.example.tradingbot.domain.model.Order;
import com.example.tradingbot.domain.model.Position;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class InstrumentBucket {

    private final String instrumentName;
    private final DbInstrumentState dbState;
    private final List<Position> positions;
    private final List<Order> orders;
    private final List<AlgoOrder> algoOrders;

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
