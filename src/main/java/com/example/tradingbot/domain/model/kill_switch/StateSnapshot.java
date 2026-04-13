package com.example.tradingbot.domain.model.kill_switch;

import com.example.tradingbot.domain.model.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.algo_order.external_snapshot.AlgoOrderExternalSnapshot;
import com.example.tradingbot.domain.model.deal.Deal;
import com.example.tradingbot.domain.model.order.Order;
import com.example.tradingbot.domain.model.order.external_snapshot.OrderExternalSnapshot;
import com.example.tradingbot.domain.model.position.Position;
import com.example.tradingbot.domain.model.position.external_snapshot.PositionExternalSnapshot;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class StateSnapshot {

    private List<Position> internalPositions;
    private List<Order> internalOrders;
    private List<AlgoOrder> internalAlgoOrders;
    private List<Deal> internalDeals;

    private List<Position> internalRelatedInactivePositions;
    private List<Order> internalRelatedInactiveOrders;
    private List<AlgoOrder> internalRelatedInactiveAlgoOrders;
    private List<Deal> internalRelatedInactiveDeals;

    private List<PositionExternalSnapshot> externalPositions;
    private List<OrderExternalSnapshot> externalOrders;
    private List<AlgoOrderExternalSnapshot> externalAlgoOrders;

    private List<OrderExternalSnapshot> externalRelatedInactiveOrders;
    private List<AlgoOrderExternalSnapshot> externalRelatedInactiveAlgoOrders;
}
