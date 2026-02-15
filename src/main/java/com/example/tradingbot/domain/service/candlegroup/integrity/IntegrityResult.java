package com.example.tradingbot.domain.service.candlegroup.integrity;

public record IntegrityResult(
    long startTs,
    long endTs,
    long expected,
    long actual,
    boolean ok
) {
}
