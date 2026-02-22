package com.example.tradingbot.rest.controller;

import com.example.tradingbot.domain.service.InstrumentService;
import com.example.tradingbot.mapping.InstrumentMapper;
import com.example.tradingbot.rest.model.request.CreateInstrumentRequest;
import com.example.tradingbot.rest.model.response.InstrumentResponse;
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
@RequestMapping("/api/{exchangeId}/instruments")
public class InstrumentController {

    private final InstrumentService instrumentService;
    private final InstrumentMapper instrumentMapper;

    @PostMapping
    public InstrumentResponse createInstrument(@PathVariable(name = "exchangeId") String exchangeInternalId,
                                               @RequestBody CreateInstrumentRequest request) {
        var domainInstrument = instrumentService.createInstrument(exchangeInternalId, request);
        return instrumentMapper.domainToRest(domainInstrument);
    }

    @GetMapping
    public List<InstrumentResponse> getAllByExchange(@PathVariable(name = "exchangeId") String exchangeInternalId) {
        var domainInstruments = instrumentService.getAllByExchange(exchangeInternalId);
        return instrumentMapper.domainToRest(domainInstruments);
    }

    @GetMapping("/{instrumentId}")
    public InstrumentResponse getByName(@PathVariable(name = "exchangeId") String exchangeInternalId,
                                        @PathVariable(name = "instrumentId") String instrumentInternalId) {
        var domainInstrument = instrumentService.getRequiredByExchangeInternalIdAndInstrumentInternalId(exchangeInternalId, instrumentInternalId);
        return instrumentMapper.domainToRest(domainInstrument);
    }
}
