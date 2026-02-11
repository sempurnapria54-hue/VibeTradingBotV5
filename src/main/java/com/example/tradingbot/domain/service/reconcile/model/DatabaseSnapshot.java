package com.example.tradingbot.domain.service.reconcile.model;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DatabaseSnapshot {

    private final String exchangeName;
    private final long capturedAtUtcMillis;
    private final List<DatabaseInstrumentSnapshot> instruments;
}
