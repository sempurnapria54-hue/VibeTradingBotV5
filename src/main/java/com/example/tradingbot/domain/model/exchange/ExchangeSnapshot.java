package com.example.tradingbot.domain.model.exchange;

import java.util.List;
import java.util.Map;
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
    private final List<ExchangeInstrumentSnapshot> instruments;
    /** Карта тикеров по ключу instId. */
    private final Map<String, ExchangePriceTicker> tickersByInstId;

    public List<ExchangePosition> getPositions() {
        return instruments.stream().flatMap(instrument -> instrument.getPositions().stream()).toList();
    }

    public List<ExchangeOrder> getOrders() {
        return instruments.stream().flatMap(instrument -> instrument.getOrders().stream()).toList();
    }

    public List<ExchangeAlgoOrder> getAlgoOrders() {
        return instruments.stream().flatMap(instrument -> instrument.getAlgoOrders().stream()).toList();
    }
}
