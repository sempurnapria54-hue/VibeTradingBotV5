package com.example.tradingbot.rest.controller;

import com.example.tradingbot.domain.model.deal.Deal;
import com.example.tradingbot.domain.service.core.DealService;
import com.example.tradingbot.mapping.DealMapper;
import com.example.tradingbot.rest.model.response.DealResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/deals")
public class DealController {

    private final DealService dealService;
    private final DealMapper mapper;

    @GetMapping("/{dealId}")
    public DealResponse getById(@PathVariable(name = "dealId") String dealInternalId) {
        Deal deal = dealService.getRequiredByInternalId(dealInternalId);
        return mapper.domainToRest(deal);
    }
}
