package com.example.tradingbot.domain.service.reconcile.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ExternalOrder {

    private final String instId;
    private final String ordId;
    private final String clOrdId;
}
