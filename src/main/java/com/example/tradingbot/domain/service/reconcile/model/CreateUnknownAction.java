package com.example.tradingbot.domain.service.reconcile.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CreateUnknownAction {

    private final ReconcileEntityType entityType;
    private final String clientId;
    private final String exchangeId;
}
