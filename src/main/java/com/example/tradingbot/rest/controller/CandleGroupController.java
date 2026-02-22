package com.example.tradingbot.rest.controller;

import com.example.tradingbot.domain.service.CandleGroupService;
import com.example.tradingbot.mapping.CandleGroupMapper;
import com.example.tradingbot.rest.model.request.CreateCandleGroupRequest;
import com.example.tradingbot.rest.model.response.CandleGroupResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/{exchangeId}/instruments/{instrumentId}/candle-groups")
public class CandleGroupController {

    private final CandleGroupService candleGroupService;
    private final CandleGroupMapper candleGroupMapper;

    @GetMapping
    public List<CandleGroupResponse> getByInstrument(@PathVariable(name = "exchangeId") String exchangeInternalId,
                                                     @PathVariable(name = "instrumentId") String instrumentInternalId) {
        var candleGroups = candleGroupService.getByInstrument(exchangeInternalId, instrumentInternalId);
        return candleGroupMapper.domainToRest(candleGroups);
    }

    @PostMapping
    public CandleGroupResponse createGroup(@PathVariable(name = "exchangeId") String exchangeInternalId,
                                           @PathVariable(name = "instrumentId") String instrumentInternalId,
                                           @RequestBody CreateCandleGroupRequest request) {
        var candleGroupEntity = candleGroupService.create(exchangeInternalId, instrumentInternalId, request);
        return candleGroupMapper.domainToRest(candleGroupEntity);
    }
}
