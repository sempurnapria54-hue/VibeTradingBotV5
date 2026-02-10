package com.example.tradingbot.domain.service;

import com.example.tradingbot.domain.model.okxproxy.*;
import com.example.tradingbot.domain.service.okxproxy.OkxMarketClientService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OkxMarketProxyService {

    private final OkxMarketClientService okxMarketClientService;

    public List<Candle> getCandles(CandlesRequest request) { return okxMarketClientService.getCandles(request); }
    public List<Candle> getHistoryCandles(CandlesRequest request) { return okxMarketClientService.getHistoryCandles(request); }
    public List<Instrument> getInstruments(InstrumentsRequest request) { return okxMarketClientService.getInstruments(request); }
    public List<PriceTicker> getTicker(TickerRequest request) { return okxMarketClientService.getTicker(request); }
}
