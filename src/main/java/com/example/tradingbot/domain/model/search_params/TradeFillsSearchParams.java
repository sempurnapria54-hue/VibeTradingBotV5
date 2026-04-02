package com.example.tradingbot.domain.model.search_params;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TradeFillsSearchParams {

    private String externalInstrumentId;
    private String externalInstrumentType;
    private String externalOrderId;
    private String after;
    private String before;
    private String limit;

}
