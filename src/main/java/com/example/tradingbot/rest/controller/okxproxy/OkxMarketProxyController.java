package com.example.tradingbot.rest.controller.okxproxy;

import com.example.tradingbot.client.model.okx.CandleResponse;
import com.example.tradingbot.client.model.okx.CandlesRequest;
import com.example.tradingbot.client.model.okx.InstrumentResponse;
import com.example.tradingbot.client.model.okx.InstrumentsRequest;
import com.example.tradingbot.client.model.okx.PriceTickerResponse;
import com.example.tradingbot.client.model.okx.TickerRequest;
import com.example.tradingbot.client.model.okx.OkxApiResponse;
import com.example.tradingbot.client.okx.OkxRestClient;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/okx/v5")
@RequiredArgsConstructor
public class OkxMarketProxyController {

    private final OkxRestClient okxRestClient;

    @GetMapping("/market/candles")
    public OkxApiResponse<CandleResponse> getCandles(CandlesRequest request) {
        return okxRestClient.getCandles(request);
    }

    @GetMapping("/market/history-candles")
    public OkxApiResponse<CandleResponse> getHistoryCandles(CandlesRequest request) {
        return okxRestClient.getHistoryCandles(request);
    }

    @GetMapping("/public/instruments")
    public OkxApiResponse<InstrumentResponse> getInstruments(InstrumentsRequest request) {
        return okxRestClient.getInstruments(request);
    }

    @GetMapping("/market/ticker")
    public OkxApiResponse<PriceTickerResponse> getTicker(TickerRequest request) {
        return okxRestClient.getTicker(request);
    }
}
