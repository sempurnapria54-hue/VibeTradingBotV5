package com.example.marketdata.integration;

import com.example.marketdata.config.ConnectorProperties;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.core.instrument.InstrumentExternalRules;
import com.example.tradingbot.domain.model.trade.candle.Candle;
import com.example.tradingbot.domain.model.trade.candle.TimeFrame;
import com.example.tradingbot.domain.model.trade.market_price.MarketPriceData;
import com.example.tradingbot.domain.model.trade.market_snapshot.MarketOrderBook;
import com.example.tradingbot.domain.model.trade.market_snapshot.MarketTicker;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Публичные чтения площадки через коннектор.
 *
 * <p><b>Все чтения здесь — без ключей, и это не совпадение.</b> Таблица
 * синхронных вызовов отдаёт market-data ровно публичную поверхность
 * площадки: листинг и правила инструментов, свечи, срезы стакана, тикер
 * ({@code docs/architecture/contracts.md} §«Синхронные вызовы»).
 * Ключей биржевых счетов у market-data нет ни в каком виде, и операций,
 * которые их требуют, он не зовёт.
 *
 * <p><b>Ходит под сервисной идентичностью кластера</b>
 * ({@link ServiceTokenProvider}): пользователя в этом вызове нет.
 *
 * <p><b>Свечи приходят уже отфильтрованными по закрытию:</b> признак
 * закрытия виден коннектору и дальше не едет — незакрытый бар в ответ не
 * попадает ({@code docs/models/domain/other/Candle.md}). Поэтому здесь
 * фильтра нет и быть не должно: второй фильтр по признаку, которого в
 * ответе уже нет, был бы фикцией.
 */
@Component
public class ExchangeReadClient {

    private static final ParameterizedTypeReference<List<Instrument>> INSTRUMENT_LIST =
            new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<List<Candle>> CANDLE_LIST =
            new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<Map<String, MarketTicker>> TICKER_MAP =
            new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<Map<String, BigDecimal>> PRICE_MAP =
            new ParameterizedTypeReference<>() { };

    private final RestClient restClient;
    private final ServiceTokenProvider tokenProvider;

    public ExchangeReadClient(RestClient.Builder restClientBuilder,
                              ServiceTokenProvider tokenProvider,
                              ConnectorProperties properties) {
        this.restClient = restClientBuilder.baseUrl(properties.getBaseUrl()).build();
        this.tokenProvider = tokenProvider;
    }

    /** Листинг инструментов площадки по типу инструмента. */
    public List<Instrument> getInstruments(String externalInstrumentType) {
        return call("instruments", () -> restClient.get()
                .uri(builder -> builder.path("/api/v1/market/instruments")
                        .queryParam("externalInstrumentType", externalInstrumentType)
                        .build())
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .body(INSTRUMENT_LIST));
    }

    /** Справочные правила торговли инструментом. */
    public InstrumentExternalRules getInstrumentRules(String externalInstrumentId, String externalInstrumentType) {
        return call("instrument-rules", () -> restClient.get()
                .uri(builder -> builder.path("/api/v1/market/instruments/{externalInstrumentId}/rules")
                        .queryParam("externalInstrumentType", externalInstrumentType)
                        .build(externalInstrumentId))
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .body(InstrumentExternalRules.class));
    }

    /** Последние закрытые свечи инструмента. */
    public List<Candle> getLatestCandles(String externalInstrumentId, TimeFrame timeframe, Integer limit) {
        return call("candles", () -> restClient.get()
                .uri(builder -> builder.path("/api/v1/market/candles")
                        .queryParam("externalInstrumentId", externalInstrumentId)
                        .queryParam("timeframe", timeframe)
                        .queryParam("limit", limit)
                        .build())
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .body(CANDLE_LIST));
    }

    /** Исторические закрытые свечи инструмента окном назад от момента. */
    public List<Candle> getHistoryCandles(String externalInstrumentId, TimeFrame timeframe, Long afterMillis,
                                          Integer limit) {
        return call("history-candles", () -> restClient.get()
                .uri(builder -> builder.path("/api/v1/market/candles/history")
                        .queryParam("externalInstrumentId", externalInstrumentId)
                        .queryParam("timeframe", timeframe)
                        .queryParam("afterMillis", afterMillis)
                        .queryParam("limit", limit)
                        .build())
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .body(CANDLE_LIST));
    }

    /** Тикеры всего листинга одним чтением: инструмент площадки → тикер. */
    public Map<String, MarketTicker> getTickers(String externalInstrumentType) {
        return call("tickers", () -> restClient.get()
                .uri(builder -> builder.path("/api/v1/market/tickers")
                        .queryParam("externalInstrumentType", externalInstrumentType)
                        .build())
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .body(TICKER_MAP));
    }

    /** Книга заявок инструмента на заданную глубину каждой стороны. */
    public MarketOrderBook getOrderBook(String externalInstrumentId, Integer depth) {
        return call("order-book", () -> restClient.get()
                .uri(builder -> builder.path("/api/v1/market/order-book/{externalInstrumentId}")
                        .queryParam("depth", depth)
                        .build(externalInstrumentId))
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .body(MarketOrderBook.class));
    }

    /** Марк-цены листинга: инструмент площадки → цена. */
    public Map<String, BigDecimal> getMarkPrices(String externalInstrumentType) {
        return call("mark-prices", () -> restClient.get()
                .uri(builder -> builder.path("/api/v1/market/mark-prices")
                        .queryParam("externalInstrumentType", externalInstrumentType)
                        .build())
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .body(PRICE_MAP));
    }

    /** Цены индексов одной котировочной валюты: индекс → цена. */
    public Map<String, BigDecimal> getIndexPrices(String quoteCurrency) {
        return call("index-prices", () -> restClient.get()
                .uri(builder -> builder.path("/api/v1/market/index-prices")
                        .queryParam("quoteCurrency", quoteCurrency)
                        .build())
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .body(PRICE_MAP));
    }

    /** Цены момента инструмента: last, mark, index. */
    public MarketPriceData getMarketPriceData(String externalInstrumentId) {
        return call("prices", () -> restClient.get()
                .uri(builder -> builder.path("/api/v1/market/prices/{externalInstrumentId}")
                        .build(externalInstrumentId))
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .body(MarketPriceData.class));
    }

    private String bearer() {
        return "Bearer " + tokenProvider.getTokenValue();
    }

    /**
     * Разводит отказ доступа и лимита от рядового отказа чтения: первый
     * прекращает проход, второй — пропускает инструмент
     * (docs/processes/snapshot-collection.md §«Отказ на проходе»).
     */
    private <T> T call(String endpoint, Supplier<T> read) {
        try {
            return read.get();
        } catch (RestClientResponseException e) {
            HttpStatusCode status = e.getStatusCode();
            if (status.isSameCodeAs(HttpStatus.UNAUTHORIZED)
                    || status.isSameCodeAs(HttpStatus.FORBIDDEN)
                    || status.isSameCodeAs(HttpStatus.TOO_MANY_REQUESTS)) {
                throw new ExchangeAccessException("Connector refused read [" + endpoint + "]: " + status, e);
            }
            throw new ExchangeReadException("Connector read failed [" + endpoint + "]: " + status, e);
        } catch (RestClientException e) {
            throw new ExchangeReadException("Connector transport error [" + endpoint + "]", e);
        }
    }
}
