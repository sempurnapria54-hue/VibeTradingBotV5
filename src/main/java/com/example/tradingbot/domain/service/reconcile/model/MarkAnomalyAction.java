package com.example.tradingbot.domain.service.reconcile.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MarkAnomalyAction {

    private final ReconcileEntityType entityType;
    private final Long entityId;
}
