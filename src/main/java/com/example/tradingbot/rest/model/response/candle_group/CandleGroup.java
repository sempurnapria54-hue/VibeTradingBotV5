package com.example.tradingbot.rest.model.response.candle_group;

import com.example.tradingbot.rest.model.response.Auditable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CandleGroup extends Auditable {

    private Long instrumentId;
    private String timeframe;
    private String status;
    private Long coverageStartUtcMillis;
    private Long coverageEndUtcMillis;
}
