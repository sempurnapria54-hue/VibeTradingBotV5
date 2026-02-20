package com.example.tradingbot.rest.controller;

import com.example.tradingbot.domain.service.InstrumentService;
import com.example.tradingbot.mapping.okxproxy.InstrumentMapper;
import com.example.tradingbot.rest.model.request.instrument.InstrumentCreateRq;
import com.example.tradingbot.rest.model.response.instrument.InstrumentResponse;
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
@RequestMapping("/api/{exchangeName}/instruments")
public class InstrumentController {

    private final InstrumentService instrumentService;
    private final InstrumentMapper instrumentMapper;

    @PostMapping
    public InstrumentResponse createInstrument(@PathVariable(name = "exchangeName") String exchangeName,
                                       @RequestBody InstrumentCreateRq request) {
        var domainInstrument = instrumentService.createInstrument(exchangeName, instrumentMapper.restToDomain(request), request.getTimeFrames());
        return instrumentMapper.domainToRest(domainInstrument);
    }

    @GetMapping
    public List<InstrumentResponse> getAllByExchange(@PathVariable(name = "exchangeName") String exchangeName) {
        var domainInstruments = instrumentService.getAllByExchange(exchangeName);
        return instrumentMapper.domainToRest(domainInstruments);
    }

    @GetMapping("/{instrumentName}")
    public InstrumentResponse getByName(@PathVariable(name = "exchangeName") String exchangeName,
                                @PathVariable(name = "instrumentName") String instrumentName) {
        var domainInstrument = instrumentService.getRequiredByExchangeNameAndName(exchangeName, instrumentName);
        return instrumentMapper.domainToRest(domainInstrument);
    }
}
