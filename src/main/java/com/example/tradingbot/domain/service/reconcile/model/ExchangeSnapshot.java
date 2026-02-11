package com.example.tradingbot.domain.service.reconcile.model;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ExchangeSnapshot {

    private final String exchangeName;
    private final long capturedAtUtcMillis;
    private final List<ExternalPosition> positions;
    private final List<ExternalOrder> orders;
    private final List<ExternalAlgoOrder> algoOrders;
}
