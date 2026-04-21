package com.example.tradingbot.rest.controller;

import com.example.tradingbot.domain.service.core.InstrumentService;
import com.example.tradingbot.mapping.InstrumentMapper;
import com.example.tradingbot.rest.model.request.instrument.CreateInstrumentRequest;
import com.example.tradingbot.rest.model.request.instrument.search_params.InstrumentSearchParams;
import com.example.tradingbot.rest.model.response.instrument.InstrumentPageResponse;
import com.example.tradingbot.rest.model.response.instrument.InstrumentResponse;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/instruments")
public class InstrumentController {

    private final InstrumentService instrumentService;
    private final InstrumentMapper instrumentMapper;

    @GetMapping("/{instrumentId}")
    public InstrumentResponse getById(@PathVariable(name = "instrumentId") String instrumentInternalId) {
        var instrument = instrumentService.getByInternalId(instrumentInternalId);
        return instrumentMapper.domainToRest(instrument);
    }

    @GetMapping
    public InstrumentPageResponse getByParams(@ParameterObject InstrumentSearchParams request,
                                              @ParameterObject
                                              @PageableDefault(page = 0,
                                                      size = 20,
                                                      sort = "id",
                                                      direction = Sort.Direction.DESC)
                                              Pageable pageable) {
        var domainSearchParams = instrumentMapper.restToDomainSearchParams(request);
        var result = instrumentService.getByParams(domainSearchParams, pageable);
        return instrumentMapper.domainToRest(result);
    }

    @PostMapping
    public InstrumentResponse createInstrument(@RequestBody CreateInstrumentRequest request) {
        var domainRq = instrumentMapper.restToDomain(request);
        var domainInstrument = instrumentService.createInstrument(request.getExchangeInternalId(), domainRq);
        return instrumentMapper.domainToRest(domainInstrument);
    }
}
