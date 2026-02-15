package com.example.tradingbot.domain.service.candlegroup;

public record BackfillResult(
    boolean completed,
    long newCursorTs,
    int fetched,
    int saved
) {
}
