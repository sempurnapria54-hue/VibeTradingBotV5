package com.example.tradingbot.rest.controller;

import com.example.tradingbot.domain.service.core.CandleGroupService;
import com.example.tradingbot.mapping.CandleGroupMapper;
import com.example.tradingbot.rest.model.request.candle_group.CreateCandleGroupRequest;
import com.example.tradingbot.rest.model.response.candle_group.CandleGroupContainerResponse;
import com.example.tradingbot.rest.model.response.candle_group.CandleGroupResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/candle-groups")
public class CandleGroupController {

    private final CandleGroupService candleGroupService;
    private final CandleGroupMapper candleGroupMapper;

    @GetMapping("/{instrumentId}")
    public CandleGroupContainerResponse getByInstrument(
            @PathVariable(name = "instrumentId") String instrumentInternalId) {
        var candleGroups = candleGroupService.getByInstrument(instrumentInternalId);
        return candleGroupMapper.domainListToRestContainer(candleGroups);
    }

    @PostMapping
    public CandleGroupResponse createGroup(@RequestBody CreateCandleGroupRequest request) {
        var domainRq = candleGroupMapper.restToDomain(request);
        var candleGroup = candleGroupService.create(request.getExchangeInternalId(),
                                                    request.getInstrumentInternalId(),
                                                    domainRq);
        return candleGroupMapper.domainToRest(candleGroup);
    }
}
