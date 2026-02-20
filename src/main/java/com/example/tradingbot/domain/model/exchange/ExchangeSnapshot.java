package com.example.tradingbot.domain.model.exchange;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ExchangeSnapshot {

    private final String exchangeName;
    private final long capturedAtUtcMillis;
    private final List<ExchangeInstrumentSnapshot> instruments;
    private final Map<String, ExternalTicker> tickersByInstId;

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
