package com.example.tradingbot.rest.controller;

import com.example.tradingbot.domain.service.CandleGroupService;
import com.example.tradingbot.mapping.okxproxy.CandleGroupMapper;
import com.example.tradingbot.rest.model.request.candlegroup.CreateCandleGroupRequest;
import com.example.tradingbot.rest.model.response.candlegroup.CandleGroupResponse;
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
    public List<CandleGroupResponse> getByInstrument(@PathVariable(name = "exchangeId") String exchangeId,
                                             @PathVariable(name = "instrumentId") String instrumentId) {
        var candleGroups = candleGroupService.listByInstrument(exchangeId, instrumentId);
        return candleGroupMapper.domainToRest(candleGroups);
    }

    @PostMapping
    public void createGroup(@PathVariable(name = "exchangeId") String exchangeId,
                            @PathVariable(name = "instrumentId") String instrumentId,
                            @RequestBody CreateCandleGroupRequest candleGroup) {
        candleGroupService.create(exchangeId, instrumentId, candleGroupMapper.restToDomain(candleGroup));
    }
}
