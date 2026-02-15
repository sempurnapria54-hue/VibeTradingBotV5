package com.example.tradingbot.domain.service.candlegroup;

public record TailSyncResult(
    int fetched,
    int saved,
    long updatedLastTailSyncTs
) {
}
