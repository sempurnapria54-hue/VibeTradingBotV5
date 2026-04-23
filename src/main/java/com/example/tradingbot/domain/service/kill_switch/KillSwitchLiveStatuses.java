package com.example.tradingbot.domain.service.kill_switch;

import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.deal.Deal;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.position.Position;

import java.util.Set;

public final class KillSwitchLiveStatuses {

    public static final Set<String> LIVE_POSITION_STATUSES = Set.of(Position.Status.ACTIVE.name());

    public static final Set<String> LIVE_ORDER_STATUSES = Set.of(
            Order.Status.CREATED.name(),
            Order.Status.PENDING.name(),
            Order.Status.ACTIVE.name(),
            Order.Status.PARTIALLY_COMPLETED.name()
    );

    public static final Set<String> LIVE_ALGO_ORDER_STATUSES = Set.of(
            AlgoOrder.Status.PENDING.name(),
            AlgoOrder.Status.ACTIVE.name()
    );

    public static final Set<String> LIVE_DEAL_STATUSES = Set.of(
            Deal.Status.PRECHECK.name(),
            Deal.Status.ENTRY_SUBMITTED.name(),
            Deal.Status.ENTRY_FINALIZED.name(),
            Deal.Status.PROTECTION_SWITCHED.name(),
            Deal.Status.MANAGING.name(),
            Deal.Status.EXIT_PENDING.name()
    );

    private KillSwitchLiveStatuses() {
    }
}
