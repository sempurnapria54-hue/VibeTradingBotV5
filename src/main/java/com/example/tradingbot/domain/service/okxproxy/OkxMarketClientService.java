package com.example.tradingbot.domain.service.okxproxy;

import com.example.tradingbot.client.model.okx.CandlesRequest;
import com.example.tradingbot.client.model.okx.InstrumentsRequest;
import com.example.tradingbot.client.model.okx.TickerRequest;
import com.example.tradingbot.client.okx.OkxRestClient;
import com.example.tradingbot.domain.model.exchange.ExchangeCandle;
import com.example.tradingbot.domain.model.exchange.ExchangeInstrument;
import com.example.tradingbot.domain.model.exchange.ExchangePriceTicker;
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

    public List<ExchangeCandle> getCandles(CandlesRequest request) {
        return okxRestClient.getCandles(request).getData().stream().map(candleMapper::clientToDomain).toList();
    }

    public List<ExchangeCandle> getHistoryCandles(CandlesRequest request) {
        return okxRestClient.getHistoryCandles(request).getData().stream().map(candleMapper::clientToDomain).toList();
    }

    public List<ExchangeInstrument> getInstruments(InstrumentsRequest request) {
        return okxRestClient.getInstruments(request).getData().stream().map(instrumentMapper::clientToDomain).toList();
    }

    public List<ExchangePriceTicker> getTicker(TickerRequest request) {
        return okxRestClient.getTicker(request).getData().stream().map(priceTickerMapper::clientToDomain).toList();
    }
}
