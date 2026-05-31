package com.example.tradingbot.domain.service.deal.orchestrator;

import com.example.tradingbot.domain.model.core.deal.Deal;

public interface DealAggregateService {

    Deal getRequired(Long dealId);

    void save(Deal deal);
}
