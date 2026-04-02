package com.example.tradingbot.domain.service;

import com.example.tradingbot.domain.model.deal.Deal;
import com.example.tradingbot.persistence.service.DealDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DealService {

    private final DealDataService dealDataService;

    public Deal getRequiredByInternalId(String internalId) {
        return dealDataService.findRequiredByInternalId(internalId);
    }

    public Deal getRequiredById(Long id) {
        return dealDataService.findRequiredById(id);
    }

    public Deal save(Deal deal) {
        return dealDataService.save(deal);
    }
}
