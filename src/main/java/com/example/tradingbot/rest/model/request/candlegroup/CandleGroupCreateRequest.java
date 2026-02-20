package com.example.tradingbot.rest.model.request.candlegroup;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CandleGroupCreateRequest {

    private String timeframe;
    private Long coverageStartTs;
}
