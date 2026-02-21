package com.example.tradingbot.domain.service.reconcile.model;

import com.example.tradingbot.domain.model.exchange.ExchangeAlgoOrder;
import com.example.tradingbot.domain.model.exchange.ExchangeOrder;
import com.example.tradingbot.domain.model.exchange.ExchangePosition;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class InstrumentBucket {

    private final String instrumentName;
    private final DbInstrumentState dbState;
    private final List<ExchangePosition> positions;
    private final List<ExchangeOrder> orders;
    private final List<ExchangeAlgoOrder> algoOrders;

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
