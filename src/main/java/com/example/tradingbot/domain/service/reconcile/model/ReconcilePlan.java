package com.example.tradingbot.domain.service.reconcile.model;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ReconcilePlan {

    private final List<CreateUnknownAction> createUnknown;
    private final List<MarkClosedAction> markClosed;
    private final List<MarkAnomalyAction> markAnomaly;
}
