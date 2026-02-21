package com.example.tradingbot.rest.model.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CandleGroupResponse extends Auditable {

    private String instrumentInternalId;
    private String timeframe;
    private String status;
    private Long coverageStartTs;
    private Long backfillCursorTs;
    private Long lastTailSyncTs;
    private Integer attemptCount;
    private String leaseOwner;
    private Long leaseUntil;
}
