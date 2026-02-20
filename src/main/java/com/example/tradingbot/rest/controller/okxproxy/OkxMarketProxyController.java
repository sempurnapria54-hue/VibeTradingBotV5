package com.example.tradingbot.rest.controller.okxproxy;

import com.example.tradingbot.client.okx.dto.CandleResponse;
import com.example.tradingbot.client.okx.dto.CandlesRequest;
import com.example.tradingbot.client.okx.dto.InstrumentResponse;
import com.example.tradingbot.client.okx.dto.InstrumentsRequest;
import com.example.tradingbot.client.okx.dto.PriceTickerResponse;
import com.example.tradingbot.client.okx.dto.TickerRequest;
import com.example.tradingbot.domain.service.OkxMarketProxyService;
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

    @GetMapping("/market/candles")
    public List<CandleResponse> getCandles(CandlesRequest request) {
        return service.getCandles(request);
    }

    @GetMapping("/market/history-candles")
    public List<CandleResponse> getHistoryCandles(CandlesRequest request) {
        return service.getHistoryCandles(request);
    }

    @GetMapping("/public/instruments")
    public List<InstrumentResponse> getInstruments(InstrumentsRequest request) {
        return service.getInstruments(request);
    }

    @GetMapping("/market/ticker")
    public List<PriceTickerResponse> getTicker(TickerRequest request) {
        return service.getTicker(request);
    }
}
