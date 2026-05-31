package com.example.tradingbot.rest.model.request.instrument.search_params;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InstrumentSearchParams {

    private String exchangeInternalId;
    private String internalId;
    private String externalId;
    private String externalType;
    private String status;
}
