package com.example.tradingbot.domain.model.search_params;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CandleSearchParams {

    private String externalInstrumentId;
    private String externalTimeframe;
    private String after;
    private String before;
    private String limit;
}
