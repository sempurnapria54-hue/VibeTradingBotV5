package com.example.tradingbot.domain.model.admin;

import com.example.tradingbot.persistence.model.CandleGroupStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CandleGroupView {

    private Long id;
    private Long instrumentId;
    private String timeframe;
    private CandleGroupStatus status;
    private Long coverageStartTs;
    private Long backfillCursorTs;
    private Long lastTailSyncTs;
    private Integer attemptCount;
    private String leaseOwner;
    private Long leaseUntil;
}
