package com.example.marketdata.domain.service;

import static java.util.Objects.isNull;

import com.example.marketdata.integration.ExchangeReadClient;
import com.example.tradingbot.domain.model.trade.market_price.MarketPriceData;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Отдаёт {@link MarketPriceData} — runtime-цены инструмента на момент
 * (last, mark, index). Не персистится, истории не ведёт, кэша не держит:
 * цена нужна прямо перед расчётом и берётся чтением у площадки. См.
 * docs/components/MarketPriceDataService.md.
 *
 * <p><b>Это не ряд тикер-срезов.</b> Тот — история состояния рынка со
 * своими метками времени и своим правилом хранения; этот — вход расчёта
 * «прямо сейчас» (docs/models/domain/other/MarketTicker.md).
 */
@Service
@RequiredArgsConstructor
public class MarketPriceDataService {

    private final ExchangeReadClient readClient;

    /** Текущие цены инструмента; пусто — тикера на площадке нет. */
    public MarketPriceData getMarketPriceData(Long instrumentId, String externalInstrumentId) {
        MarketPriceData priceData = readClient.getMarketPriceData(externalInstrumentId);
        if (isNull(priceData)) {
            return null;
        }
        priceData.setInstrumentId(instrumentId);
        return priceData;
    }
}
