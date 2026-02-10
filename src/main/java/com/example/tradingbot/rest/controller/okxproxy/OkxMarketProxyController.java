package com.example.tradingbot.rest.controller.okxproxy;

import com.example.tradingbot.domain.service.OkxMarketProxyService;
import com.example.tradingbot.mapping.okxproxy.CandleMapper;
import com.example.tradingbot.mapping.okxproxy.InstrumentMapper;
import com.example.tradingbot.mapping.okxproxy.OkxProxyRequestMapper;
import com.example.tradingbot.mapping.okxproxy.PriceTickerMapper;
import com.example.tradingbot.rest.model.okxproxy.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/okx/v5")
@RequiredArgsConstructor
public class OkxMarketProxyController {

    private final OkxMarketProxyService service;
    private final OkxProxyRequestMapper requestMapper;
    private final CandleMapper candleMapper;
    private final InstrumentMapper instrumentMapper;
    private final PriceTickerMapper priceTickerMapper;

    @GetMapping("/market/candles")
    public RestResponse<Candle> getCandles(CandlesRequest request) { return success(service.getCandles(requestMapper.restToDomain(request)).stream().map(candleMapper::domainToRest).toList()); }

    @GetMapping("/market/history-candles")
    public RestResponse<Candle> getHistoryCandles(CandlesRequest request) { return success(service.getHistoryCandles(requestMapper.restToDomain(request)).stream().map(candleMapper::domainToRest).toList()); }

    @GetMapping("/public/instruments")
    public RestResponse<Instrument> getInstruments(InstrumentsRequest request) { return success(service.getInstruments(requestMapper.restToDomain(request)).stream().map(instrumentMapper::domainToRest).toList()); }

    @GetMapping("/market/ticker")
    public RestResponse<PriceTicker> getTicker(TickerRequest request) { return success(service.getTicker(requestMapper.restToDomain(request)).stream().map(priceTickerMapper::domainToRest).toList()); }

    private <T> RestResponse<T> success(List<T> data) {
        RestResponse<T> response = new RestResponse<>();
        response.setCode("0");
        response.setMessage("success");
        response.setData(data);
        return response;
    }
}
