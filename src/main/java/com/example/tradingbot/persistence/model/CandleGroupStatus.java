package com.example.tradingbot.persistence.model;

public enum CandleGroupStatus {
    NEW,
    BACKFILL_RUNNING,
    REPAIR_RUNNING,
    SYNC,
    READY,
    ERROR
}
