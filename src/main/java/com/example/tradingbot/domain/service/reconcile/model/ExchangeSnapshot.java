package com.example.tradingbot.domain.service.reconcile.model;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ExchangeSnapshot {

    private final String exchangeName;
    private final long capturedAtUtcMillis;
    private final List<ExchangeInstrumentSnapshot> instruments;

    public List<ExternalPosition> getPositions() {
        return instruments.stream().flatMap(instrument -> instrument.getPositions().stream()).toList();
    }

    public List<ExternalOrder> getOrders() {
        return instruments.stream().flatMap(instrument -> instrument.getOrders().stream()).toList();
    }

    public List<ExternalAlgoOrder> getAlgoOrders() {
        return instruments.stream().flatMap(instrument -> instrument.getAlgoOrders().stream()).toList();
    }
}
