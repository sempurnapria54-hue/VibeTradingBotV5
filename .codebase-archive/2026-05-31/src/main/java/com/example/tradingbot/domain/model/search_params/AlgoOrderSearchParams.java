package com.example.tradingbot.domain.model.search_params;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AlgoOrderSearchParams {

    private String exchangeInternalId;
    private String instrumentInternalId;
    private String instrumentExternalId;
    private String instrumentExternalType;
    private String dealInternalId;
    private String internalId;
    private String externalId;
    private String externalType;
    private String status;
    private String externalStatus;

}