package com.example.tradingbot.domain.service.okxproxy;

import com.example.tradingbot.client.model.okx.request.CandlesRequest;
import com.example.tradingbot.client.model.okx.request.InstrumentsRequest;
import com.example.tradingbot.client.model.okx.response.TickerRequest;
import com.example.tradingbot.client.service.okx.OkxRestClient;
import com.example.tradingbot.domain.model.Candle;
import com.example.tradingbot.domain.model.Instrument;
import com.example.tradingbot.domain.model.PriceTicker;
import com.example.tradingbot.mapping.CandleMapper;
import com.example.tradingbot.mapping.InstrumentMapper;
import com.example.tradingbot.mapping.PriceTickerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OkxMarketClientService {

    private final OkxRestClient okxRestClient;
    private final CandleMapper candleMapper;
    private final InstrumentMapper instrumentMapper;
    private final PriceTickerMapper priceTickerMapper;

    public List<Candle> getCandles(CandlesRequest request) {
        return okxRestClient.getCandles(request).getData().stream().map(candleMapper::clientToDomain).toList();
    }

    public List<Candle> getHistoryCandles(CandlesRequest request) {
        return okxRestClient.getHistoryCandles(request).getData().stream().map(candleMapper::clientToDomain).toList();
    }

    public List<Instrument> getInstruments(InstrumentsRequest request) {
        return okxRestClient.getInstruments(request).getData().stream().map(instrumentMapper::clientToDomain).toList();
    }

    public List<PriceTicker> getTicker(TickerRequest request) {
        return okxRestClient.getTicker(request).getData().stream().map(priceTickerMapper::clientToDomain).toList();
    }
}
