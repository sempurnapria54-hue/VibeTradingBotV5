package com.example.tradingbot.domain.service.reconcile.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ExternalPosition {

    private final String instId;
    private final String side;
}
