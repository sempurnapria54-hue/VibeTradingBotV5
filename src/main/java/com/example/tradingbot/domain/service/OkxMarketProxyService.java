package com.example.tradingbot.domain.service;

import com.example.tradingbot.client.model.okx.*;
import com.example.tradingbot.client.okx.OkxRestClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OkxMarketProxyService {

    private final OkxRestClient okxRestClient;

    public List<CandleResponse> getCandles(CandlesRequest request) {
        return okxRestClient.getCandles(request).getData();
    }

    public List<CandleResponse> getHistoryCandles(CandlesRequest request) {
        return okxRestClient.getHistoryCandles(request).getData();
    }

    public List<InstrumentResponse> getInstruments(InstrumentsRequest request) {
        return okxRestClient.getInstruments(request).getData();
    }

    public List<PriceTickerResponse> getTicker(TickerRequest request) {
        return okxRestClient.getTicker(request).getData();
    }
}
