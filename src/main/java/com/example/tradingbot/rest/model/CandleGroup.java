package com.example.tradingbot.rest.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CandleGroup {

    private Long id;
    private Long instrumentId;
    private String timeframe;
    private String status;
    private Long coverageStartTs;
    private Long backfillCursorTs;
    private Long lastTailSyncTs;
    private Integer attemptCount;
    private String leaseOwner;
    private Long leaseUntil;
}
