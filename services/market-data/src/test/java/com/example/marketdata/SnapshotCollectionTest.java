package com.example.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.marketdata.config.ConnectorProperties;
import com.example.marketdata.config.SnapshotCollectionProperties;
import com.example.marketdata.domain.service.SnapshotCollector;
import com.example.marketdata.integration.ExchangeAccessException;
import com.example.marketdata.integration.ExchangeReadClient;
import com.example.marketdata.integration.ExchangeReadException;
import com.example.marketdata.persistence.service.InstrumentDataService;
import com.example.marketdata.persistence.service.MarketSnapshotDataService;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.trade.market_snapshot.MarketOrderBook;
import com.example.tradingbot.domain.model.trade.market_snapshot.MarketTicker;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Проход сбора невосполнимых срезов: чего он не подставляет и когда он
 * прекращается.
 *
 * <p>Оба свойства несущие. Подстановка последней цены на место марк-цены
 * молча меняет ценовой домен (docs/components/models/MarketPriceData.md);
 * продолжение обхода под исчерпанным лимитом теряет и следующий проход
 * (docs/processes/snapshot-collection.md §«Отказ на проходе»).
 */
class SnapshotCollectionTest {

    private static final String EXCHANGE = "OKX";
    private static final String FIRST = "BTC-USDT-SWAP";
    private static final String SECOND = "ETH-USDT-SWAP";

    private final ExchangeReadClient readClient = mock(ExchangeReadClient.class);
    private final InstrumentDataService instrumentDataService = mock(InstrumentDataService.class);
    private final MarketSnapshotDataService snapshotDataService = mock(MarketSnapshotDataService.class);
    private final SnapshotCollectionProperties properties = new SnapshotCollectionProperties();
    private final ConnectorProperties connectorProperties = new ConnectorProperties();

    private final SnapshotCollector collector = new SnapshotCollector(
            readClient, instrumentDataService, snapshotDataService, properties, connectorProperties);

    SnapshotCollectionTest() {
        connectorProperties.setExchangeCode(EXCHANGE);
        connectorProperties.setInstrumentTypes(List.of("SWAP"));
        connectorProperties.setIndexQuoteCurrencies(List.of("USDT"));
    }

    /**
     * Марк-цена не дошла — поле среза остаётся пустым, а не заполняется
     * последней ценой.
     */
    @Test
    void absentMarkPriceStaysAbsent() {
        givenListing(instrument(1L, FIRST));
        when(readClient.getTickers("SWAP")).thenReturn(Map.of(FIRST, ticker()));
        when(readClient.getMarkPrices("SWAP")).thenReturn(Map.of());
        when(readClient.getIndexPrices("USDT")).thenReturn(Map.of());

        collector.collectPass();

        ArgumentCaptor<MarketTicker> saved = ArgumentCaptor.forClass(MarketTicker.class);
        verify(snapshotDataService).saveIfNew(saved.capture());
        assertThat(saved.getValue().getMarkPrice()).isNull();
        assertThat(saved.getValue().getIndexPrice()).isNull();
        assertThat(saved.getValue().getLastPrice()).isNotNull();
        assertThat(saved.getValue().getObservedTimestamp()).isNotNull();
    }

    /** Индексная цена находится по паре валют инструмента, а не по его имени. */
    @Test
    void indexPriceIsResolvedByCurrencyPair() {
        givenListing(instrument(1L, FIRST));
        when(readClient.getTickers("SWAP")).thenReturn(Map.of(FIRST, ticker()));
        when(readClient.getMarkPrices("SWAP")).thenReturn(Map.of(FIRST, new BigDecimal("101")));
        when(readClient.getIndexPrices("USDT")).thenReturn(Map.of("BTC-USDT", new BigDecimal("102")));

        collector.collectPass();

        ArgumentCaptor<MarketTicker> saved = ArgumentCaptor.forClass(MarketTicker.class);
        verify(snapshotDataService).saveIfNew(saved.capture());
        assertThat(saved.getValue().getMarkPrice()).isEqualByComparingTo("101");
        assertThat(saved.getValue().getIndexPrice()).isEqualByComparingTo("102");
    }

    /** Отказ по одному инструменту проход не роняет: остальные снимаются. */
    @Test
    void singleInstrumentFailureDoesNotStopThePass() {
        givenListing(instrument(1L, FIRST), instrument(2L, SECOND));
        givenNoTickers();
        when(readClient.getOrderBook(eq(FIRST), anyInt())).thenThrow(new ExchangeReadException("boom"));
        when(readClient.getOrderBook(eq(SECOND), anyInt())).thenReturn(orderBook());

        collector.collectPass();

        verify(snapshotDataService, times(1)).saveIfNew(any(MarketOrderBook.class));
    }

    /**
     * Отказ доступа или лимита прекращает проход: второй инструмент не
     * запрашивается вовсе.
     */
    @Test
    void accessFailureStopsThePass() {
        givenListing(instrument(1L, FIRST), instrument(2L, SECOND));
        givenNoTickers();
        when(readClient.getOrderBook(eq(FIRST), anyInt()))
                .thenThrow(new ExchangeAccessException("limit", null));

        collector.collectPass();

        verify(readClient, times(1)).getOrderBook(anyString(), anyInt());
        verify(snapshotDataService, times(0)).saveIfNew(any(MarketOrderBook.class));
    }

    /**
     * Отказ ПИСЬМА одной строки проход тоже не роняет — и не уносит с
     * собой ещё не снятые книги.
     *
     * <p>Отказ по одному инструменту стоит одну строку — это объявленная
     * политика прохода, но держалась она только на отказах ЧТЕНИЯ.
     * Неполная строка от площадки (нет метки времени — половины
     * естественного ключа) валит письмо, а вместе с ним валила и весь
     * проход: момент невосполним, догонять нечего.
     */
    @Test
    void tickerWriteFailureDoesNotStopThePass() {
        givenListing(instrument(1L, FIRST), instrument(2L, SECOND));
        when(readClient.getTickers("SWAP")).thenReturn(Map.of(FIRST, ticker(), SECOND, ticker()));
        when(readClient.getMarkPrices("SWAP")).thenReturn(Map.of());
        when(readClient.getIndexPrices("USDT")).thenReturn(Map.of());
        when(readClient.getOrderBook(anyString(), anyInt())).thenReturn(orderBook());
        doThrow(new IllegalArgumentException("no external timestamp"))
                .doNothing()
                .when(snapshotDataService).saveIfNew(any(MarketTicker.class));

        collector.collectPass();

        verify(snapshotDataService, times(2)).saveIfNew(any(MarketTicker.class));
        verify(snapshotDataService, times(2)).saveIfNew(any(MarketOrderBook.class));
    }

    private void givenListing(Instrument... instruments) {
        when(instrumentDataService.findListedWithin(eq(EXCHANGE), any(), anyInt()))
                .thenReturn(List.of(instruments));
    }

    private void givenNoTickers() {
        when(readClient.getTickers("SWAP")).thenReturn(Map.of());
        when(readClient.getMarkPrices("SWAP")).thenReturn(Map.of());
        when(readClient.getIndexPrices("USDT")).thenReturn(Map.of());
    }

    private Instrument instrument(Long id, String externalId) {
        Instrument instrument = new Instrument();
        instrument.setId(id);
        instrument.setExchangeCode(EXCHANGE);
        instrument.setExternalId(externalId);
        instrument.setExternalBaseCurrency("BTC");
        instrument.setExternalQuoteCurrency("USDT");
        instrument.setStatus(Instrument.Status.ACTIVE);
        return instrument;
    }

    private MarketTicker ticker() {
        MarketTicker ticker = new MarketTicker();
        ticker.setExternalTimestamp(1_700_000_000_000L);
        ticker.setLastPrice(new BigDecimal("100"));
        return ticker;
    }

    private MarketOrderBook orderBook() {
        MarketOrderBook orderBook = new MarketOrderBook();
        orderBook.setExternalTimestamp(1_700_000_000_000L);
        orderBook.setBids(List.of());
        orderBook.setAsks(List.of());
        return orderBook;
    }
}
