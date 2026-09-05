package com.example.connector.okx.api.controller;

import com.example.connector.okx.api.model.CandleWindowApiQuery;
import com.example.connector.okx.api.model.HistoryCandleApiQuery;
import com.example.connector.okx.api.model.IndexCandleApiQuery;
import com.example.connector.okx.gateway.ExchangeGateway;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.core.instrument.InstrumentExternalRules;
import com.example.tradingbot.domain.model.trade.candle.Candle;
import com.example.tradingbot.domain.model.trade.market_price.MarketPriceData;
import com.example.tradingbot.domain.model.trade.market_snapshot.MarketOrderBook;
import com.example.tradingbot.domain.model.trade.market_snapshot.MarketTicker;
import io.swagger.v3.oas.annotations.Operation;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Публичные чтения площадки: то, для чего ключи не нужны.
 *
 * <p><b>Счёта в пути нет, и это выведено, а не срисовано.</b> Класс
 * операции определяется вопросом «нужны ли ключи», а не тем, кто её
 * зовёт: требовать счёт у чтения листинга значило бы связать сбор
 * рыночных данных с наличием зарегистрированного счёта
 * ({@code docs/components/IntegrationService.md} §«Входной контракт»).
 *
 * <p>Штатный потребитель — {@code market-data}
 * ({@code docs/architecture/contracts.md} §«Синхронные вызовы»); ядру эти
 * чтения тоже открыты, отдельного контура для него нет.
 */
@RestController
@RequestMapping("/api/v1/market")
@RequiredArgsConstructor
public class MarketDataController {

    private final ExchangeGateway gateway;

    @Operation(summary = "Листинг инструментов площадки")
    @GetMapping("/instruments")
    public List<Instrument> getInstruments(@RequestParam String externalInstrumentType) {
        return gateway.getInstruments(externalInstrumentType);
    }

    @Operation(summary = "Инструмент площадки")
    @GetMapping("/instruments/{externalInstrumentId}")
    public Instrument getInstrument(@PathVariable String externalInstrumentId,
                                    @RequestParam String externalInstrumentType) {
        return gateway.getInstrument(externalInstrumentId, externalInstrumentType);
    }

    @Operation(summary = "Правила торговли инструментом")
    @GetMapping("/instruments/{externalInstrumentId}/rules")
    public InstrumentExternalRules getInstrumentRules(@PathVariable String externalInstrumentId,
                                                      @RequestParam String externalInstrumentType) {
        return gateway.getInstrumentRules(externalInstrumentId, externalInstrumentType);
    }

    @Operation(summary = "Последние свечи инструмента")
    @GetMapping("/candles")
    public List<Candle> getLatestCandles(@ParameterObject CandleWindowApiQuery window) {
        return gateway.getLatestCandles(window.getExternalInstrumentId(), window.getExternalBar(),
                window.getLimit());
    }

    @Operation(summary = "Исторические свечи инструмента окном")
    @GetMapping("/candles/history")
    public List<Candle> getHistoryCandles(@ParameterObject HistoryCandleApiQuery window) {
        return gateway.getHistoryCandles(window.getExternalInstrumentId(), window.getExternalBar(),
                window.getAfterMillis(), window.getLimit());
    }

    @Operation(summary = "Свеча индекса на момент")
    @GetMapping("/candles/index")
    public Candle getIndexCandleAt(@ParameterObject IndexCandleApiQuery query) {
        return gateway.getIndexCandleAt(query.getIndexInstrumentId(), query.getExternalBar(),
                query.getAt());
    }

    @Operation(summary = "Тикеры всего листинга одним чтением")
    @GetMapping("/tickers")
    public List<MarketTicker> getTickers(@RequestParam String externalInstrumentType) {
        return gateway.getTickers(externalInstrumentType);
    }

    @Operation(summary = "Книга заявок инструмента")
    @GetMapping("/order-book/{externalInstrumentId}")
    public MarketOrderBook getOrderBook(@PathVariable String externalInstrumentId,
                                        @RequestParam Integer depth) {
        return gateway.getOrderBook(externalInstrumentId, depth);
    }

    @Operation(summary = "Марк-цены листинга: инструмент площадки → цена")
    @GetMapping("/mark-prices")
    public Map<String, BigDecimal> getMarkPrices(@RequestParam String externalInstrumentType) {
        return gateway.getMarkPrices(externalInstrumentType);
    }

    @Operation(summary = "Цены индексов расчётной валюты: индекс → цена")
    @GetMapping("/index-prices")
    public Map<String, BigDecimal> getIndexPrices(@RequestParam String quoteCurrency) {
        return gateway.getIndexPrices(quoteCurrency);
    }

    @Operation(summary = "Цены момента: last, mark, index")
    @GetMapping("/prices/{externalInstrumentId}")
    public MarketPriceData getMarketPriceData(@PathVariable String externalInstrumentId) {
        return gateway.getMarketPriceData(externalInstrumentId);
    }

    @Operation(summary = "Время площадки")
    @GetMapping("/time")
    public OffsetDateTime getServerTime() {
        return gateway.getServerTime();
    }
}
