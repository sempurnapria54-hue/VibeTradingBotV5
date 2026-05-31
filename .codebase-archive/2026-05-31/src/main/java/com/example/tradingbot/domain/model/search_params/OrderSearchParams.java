package com.example.tradingbot.domain.model.search_params;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderSearchParams {

    private String internalExchangeId;
    private String internalInstrumentId;
    private String externalInstrumentId;
    private String externalInstrumentType;
    private String internalDealId;
    private String internalId;
    private String externalId;
    private String status;
    private String externalStatus;
    private String type;
    private String side;
    private String externalAfter;
    private String externalBefore;
    private String externalLimit;
}
