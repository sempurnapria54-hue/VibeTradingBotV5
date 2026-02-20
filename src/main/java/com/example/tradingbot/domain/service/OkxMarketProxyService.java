package com.example.tradingbot.domain.service;

import com.example.tradingbot.client.okx.OkxRestClient;
import com.example.tradingbot.client.okx.dto.CandleResponse;
import com.example.tradingbot.client.okx.dto.CandlesRequest;
import com.example.tradingbot.client.okx.dto.InstrumentResponse;
import com.example.tradingbot.client.okx.dto.InstrumentsRequest;
import com.example.tradingbot.client.okx.dto.PriceTickerResponse;
import com.example.tradingbot.client.okx.dto.TickerRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OkxMarketProxyService {

    private final OkxRestClient okxRestClient;

    public List<CandleResponse> getCandles(CandlesRequest request) { return okxRestClient.getCandles(request).getData(); }
    public List<CandleResponse> getHistoryCandles(CandlesRequest request) { return okxRestClient.getHistoryCandles(request).getData(); }
    public List<InstrumentResponse> getInstruments(InstrumentsRequest request) { return okxRestClient.getInstruments(request).getData(); }
    public List<PriceTickerResponse> getTicker(TickerRequest request) { return okxRestClient.getTicker(request).getData(); }
}
