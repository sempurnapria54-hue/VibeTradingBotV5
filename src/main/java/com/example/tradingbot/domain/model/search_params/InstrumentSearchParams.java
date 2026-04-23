package com.example.tradingbot.domain.model.search_params;

import com.example.tradingbot.domain.model.instrument.Instrument;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InstrumentSearchParams {

    private String externalType;
    private String externalId;

    private Long id;
    private Long exchangeId;
    private String exchangeInternalId;
    private String internalId;
    private Instrument.Status internalStatus;
}