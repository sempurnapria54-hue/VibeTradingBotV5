package com.example.tradingbot.domain.service.reconcile.model;

import com.example.tradingbot.domain.model.entity.AlgoOrderEntity;
import com.example.tradingbot.domain.model.entity.InstrumentEntity;
import com.example.tradingbot.domain.model.entity.OrderEntity;
import com.example.tradingbot.domain.model.entity.PositionEntity;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class DbInstrumentState {

    private final InstrumentEntity instrument;
    private final List<PositionEntity> activePositions;
    private final List<OrderEntity> activeOrders;
    private final List<AlgoOrderEntity> activeAlgoOrders;

    public int getPositionsCount() {
        return activePositions.size();
    }

    public int getOrdersCount() {
        return activeOrders.size();
    }

    public int getAlgoOrdersCount() {
        return activeAlgoOrders.size();
    }
}
