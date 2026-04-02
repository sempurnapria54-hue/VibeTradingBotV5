package com.example.tradingbot.domain.model.search_params;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InstrumentSearchParams {

    private Long id;
    private String internalId;
    private Long exchangeId;
    private String exchangeInternalId;
    private String externalId;
    private String externalType;
    private String status;

}