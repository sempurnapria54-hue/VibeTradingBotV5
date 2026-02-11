package com.example.tradingbot.domain.service.reconcile.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CancelFlowResult {

    private final int closedPositions;
    private final int canceledOrders;
    private final int canceledAlgoOrders;
    private final int unknownCreated;
    private final boolean emptyAfterFlow;
}
