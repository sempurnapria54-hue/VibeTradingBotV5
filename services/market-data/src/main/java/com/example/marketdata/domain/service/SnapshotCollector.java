package com.example.marketdata.domain.service;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.collections4.MapUtils.emptyIfNull;

import com.example.marketdata.config.ConnectorProperties;
import com.example.marketdata.config.SnapshotCollectionProperties;
import com.example.marketdata.integration.ExchangeAccessException;
import com.example.marketdata.integration.ExchangeReadClient;
import com.example.marketdata.integration.ExchangeReadException;
import com.example.marketdata.persistence.service.InstrumentDataService;
import com.example.marketdata.persistence.service.MarketSnapshotDataService;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.trade.market_snapshot.MarketOrderBook;
import com.example.tradingbot.domain.model.trade.market_snapshot.MarketTicker;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
// MapUtils.isEmpty зовётся с именем класса: статический импорт столкнулся бы
// с одноимённым из CollectionUtils — тот же случай, что у Objects.equals.
import org.apache.commons.collections4.MapUtils;
import org.springframework.stereotype.Service;

/**
 * Один проход сбора невосполнимых срезов по действующему листингу
 * (docs/processes/snapshot-collection.md).
 *
 * <p><b>Единица работы — проход, а не инструмент.</b> Срез имеет смысл
 * как состояние рынка на момент, и пять инструментов, снятых с разбросом
 * в минуту, — не срез, а пять разных моментов.
 *
 * <p><b>Два ряда снимаются по-разному, и это не деталь реализации.</b>
 * Тикер — агрегатным чтением на весь листинг (плюс отдельные чтения
 * марк-цен и индексов, которых тикер площадки не отдаёт); стакан —
 * поинструментно, потому что агрегатного чтения книги у площадки нет.
 * Отсюда несимметричная цена: тикер-срез стоит единицы запросов,
 * стакан-срез — столько, сколько инструментов, и именно он задаёт нижнюю
 * границу интервала.
 *
 * <p><b>Отказы разведены по последствию.</b> Отказ по одному инструменту
 * проход не роняет — строки просто нет, и «не снято» отличается от
 * «снято и пусто» тем, что строки нет вовсе. Отказ доступа или лимита
 * проход ПРЕКРАЩАЕТ: продолжать обход под исчерпанным лимитом — способ
 * потерять и следующий проход.
 *
 * <p><b>Пропущенный проход не догоняется.</b> Догонять нечего: момент
 * прошёл, а срез привязан к моменту.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SnapshotCollector {

    /** Разделитель пары валют в имени индекса у площадки. */
    private static final String INDEX_KEY_SEPARATOR = "-";

    /** Статусы, при которых инструмент считается действующим листингом. */
    private static final Set<Instrument.Status> LISTED_STATUSES = Set.of(
            Instrument.Status.SYNC,
            Instrument.Status.CANDLES_LOADING,
            Instrument.Status.ACTIVE);

    private final ExchangeReadClient readClient;
    private final InstrumentDataService instrumentDataService;
    private final MarketSnapshotDataService snapshotDataService;
    private final SnapshotCollectionProperties properties;
    private final ConnectorProperties connectorProperties;

    /** Снимает срез по всему действующему листингу за один проход. */
    public void collectPass() {
        List<Instrument> listed = instrumentDataService.findListedWithin(
                connectorProperties.getExchangeCode(), LISTED_STATUSES, properties.getPassLimit());
        if (isEmpty(listed)) {
            return;
        }
        try {
            collectTickers(listed);
        } catch (ExchangeAccessException e) {
            log.error("Snapshot pass stopped on tickers: exchange refused access or limit", e);
            return;
        } catch (ExchangeReadException e) {
            log.warn("Ticker snapshot skipped for this pass", e);
        }
        collectOrderBooks(listed);
    }

    /**
     * Тикер-срез: агрегатное чтение тикеров и марк-цен по типу
     * инструмента плюс индексы по котировочной валюте.
     *
     * <p><b>Чтений три, а не одно, потому что тикер площадки марк-цену и
     * индекс не отдаёт</b> (docs/components/models/MarketPriceData.md).
     * Подстановка последней цены на их место запрещена: она молча меняет
     * ценовой домен — поэтому нерезолвившееся поле остаётся пустым.
     */
    private void collectTickers(List<Instrument> listed) {
        Map<String, BigDecimal> indexPrices = readIndexPrices();
        Map<String, MarketTicker> tickers = new HashMap<>();
        Map<String, BigDecimal> markPrices = new HashMap<>();
        for (String instrumentType : connectorProperties.getInstrumentTypes()) {
            tickers.putAll(emptyIfNull(readClient.getTickers(instrumentType)));
            markPrices.putAll(emptyIfNull(readClient.getMarkPrices(instrumentType)));
        }
        if (MapUtils.isEmpty(tickers)) {
            log.warn("Ticker snapshot is empty for the whole listing");
            return;
        }
        long observedAt = Instant.now().toEpochMilli();
        for (Instrument instrument : listed) {
            MarketTicker ticker = tickers.get(instrument.getExternalId());
            if (nonNull(ticker)) {
                persistSafely(instrument, () -> persistTicker(ticker, instrument, observedAt,
                        markPrices, indexPrices));
            }
        }
    }

    private Map<String, BigDecimal> readIndexPrices() {
        Map<String, BigDecimal> prices = new HashMap<>();
        for (String quoteCurrency : connectorProperties.getIndexQuoteCurrencies()) {
            prices.putAll(emptyIfNull(readClient.getIndexPrices(quoteCurrency)));
        }
        return prices;
    }

    private void persistTicker(MarketTicker ticker, Instrument instrument, Long observedAt,
                               Map<String, BigDecimal> markPrices, Map<String, BigDecimal> indexPrices) {
        ticker.setInstrumentId(instrument.getId());
        ticker.setObservedTimestamp(observedAt);
        ticker.setMarkPrice(markPrices.get(instrument.getExternalId()));
        ticker.setIndexPrice(indexPrices.get(indexKeyOf(instrument)));
        snapshotDataService.saveIfNew(ticker);
    }

    /**
     * Имя индекса расчётной валюты инструмента у площадки — пара
     * «базовая-котировочная». Пустая валюта означает, что индекс не
     * резолвится, и поле среза остаётся пустым: это законное «чтение не
     * дошло», а не повод подставить чужую цену.
     */
    private String indexKeyOf(Instrument instrument) {
        if (isNull(instrument.getExternalBaseCurrency()) || isNull(instrument.getExternalQuoteCurrency())) {
            return null;
        }
        return instrument.getExternalBaseCurrency() + INDEX_KEY_SEPARATOR + instrument.getExternalQuoteCurrency();
    }

    private void collectOrderBooks(List<Instrument> listed) {
        for (Instrument instrument : listed) {
            try {
                MarketOrderBook orderBook = readClient.getOrderBook(
                        instrument.getExternalId(), properties.getOrderBookDepth());
                if (nonNull(orderBook)) {
                    orderBook.setInstrumentId(instrument.getId());
                    orderBook.setObservedTimestamp(Instant.now().toEpochMilli());
                    snapshotDataService.saveIfNew(orderBook);
                }
            } catch (ExchangeAccessException e) {
                log.error("Snapshot pass stopped: exchange refused access or limit", e);
                return;
            } catch (ExchangeReadException e) {
                log.warn("Order book snapshot skipped for {}", instrument.getExternalId(), e);
            } catch (RuntimeException e) {
                log.warn("Order book snapshot not written for {}", instrument.getExternalId(), e);
            }
        }
    }

    /**
     * Пишет строку среза так, чтобы её отказ стоил одну строку, а не
     * проход.
     *
     * <p><b>Отказ письма разведён с отказом чтения намеренно.</b>
     * Отказ по одному инструменту проход не роняет — это объявленная
     * политика класса, — но до сих пор она держалась только на отказах
     * ЧТЕНИЯ: строка, которую площадка отдала неполной (нет метки
     * времени — а это половина естественного ключа), валила бы письмо
     * необъявленным исключением, и вместе с ним весь проход, включая ещё
     * не снятые книги. Момент при этом невосполним
     * (docs/processes/snapshot-collection.md).
     */
    private void persistSafely(Instrument instrument, Runnable write) {
        try {
            write.run();
        } catch (RuntimeException e) {
            log.warn("Ticker snapshot not written for {}", instrument.getExternalId(), e);
        }
    }
}
