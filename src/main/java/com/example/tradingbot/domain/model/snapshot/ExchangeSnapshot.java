package com.example.tradingbot.domain.model.snapshot;

import java.util.List;
import java.util.Map;

import com.example.tradingbot.domain.model.*;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ExchangeSnapshot {

    /** Имя биржи, для которой сформирован снимок. */
    private final String exchangeName;
    /** Время снятия снимка в UTC миллисекундах. */
    private final long capturedAtUtcMillis;
    /** Срезы данных по инструментам биржи. */
    private final List<InstrumentSnapshot> instruments;
    /** Карта тикеров по ключу instId. */
    private final Map<String, PriceTicker> tickersByInstId;

    public List<Position> getPositions() {
        return instruments.stream().flatMap(instrument -> instrument.getPositions().stream()).toList();
    }

    public List<Order> getOrders() {
        return instruments.stream().flatMap(instrument -> instrument.getOrders().stream()).toList();
    }

    public List<AlgoOrder> getAlgoOrders() {
        return instruments.stream().flatMap(instrument -> instrument.getAlgoOrders().stream()).toList();
    }
}
