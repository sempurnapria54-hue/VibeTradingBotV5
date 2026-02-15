package com.example.tradingbot.domain.service.candlegroup.model;

public record CandleGroupRunContext(
    long runNowMillis,
    long tfMillis,
    long nowClosedTs,
    String instanceId
) {
}
