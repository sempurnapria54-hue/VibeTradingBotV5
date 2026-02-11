package com.example.tradingbot.domain.service.reconcile.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ExternalAlgoOrder {

    private final String instId;
    private final String algoId;
    private final String algoClOrdId;
}
