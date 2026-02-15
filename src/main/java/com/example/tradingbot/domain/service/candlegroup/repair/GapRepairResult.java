package com.example.tradingbot.domain.service.candlegroup.repair;

public record GapRepairResult(
    TimeWindow gap,
    long expected,
    long actual,
    boolean repaired
) {
}
