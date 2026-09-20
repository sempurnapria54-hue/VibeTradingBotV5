package com.example.marketdata.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Невосполнимые срезы — группа {@code B5} документа
 * `.claude/tests/cases/market-data.md`.
 *
 * <p><b>Единица работы — проход, а не инструмент.</b> Срез имеет смысл
 * как состояние рынка на момент, и пять инструментов, снятых с разбросом
 * в минуту, — не срез, а пять разных моментов
 * (docs/processes/snapshot-collection.md).
 *
 * <p><b>Отказы разведены ПО ПОСЛЕДСТВИЮ, и это главный предмет
 * группы.</b> Отказ по одному инструменту стои́т одну строку — «не снято»
 * отличается от «снято и пусто» тем, что строки нет вовсе; отказ доступа
 * либо лимита прекращает проход целиком, потому что продолжать обход под
 * исчерпанным лимитом — способ потерять и следующий проход. Наблюдается
 * это отсутствием ЗАПРОСОВ, а не содержимым базы.
 */
class SnapshotPassBoxTest extends SharedMarketDataBox {

    /** Метка площадки: одна на проход — момент у среза один. */
    private static final Long EXCHANGE_MOMENT = 1_758_000_000_000L;

    @Test
    @DisplayName("B5.1 — проход снимает тикер агрегатно, а книгу поинструментно")
    void b5_1_thePassTakesTheTickerInAggregateAndTheBookPerInstrument() {
        List<String> instruments = provisionInstruments(INSTRUMENT, SECOND_INSTRUMENT, THIRD_INSTRUMENT);
        stubFullMarket();

        tick(Tick.SNAPSHOTS);

        assertThat(instruments).hasSize(3);
        assertThat(connector.count(ConnectorStub.TICKERS)).isEqualTo(1);
        assertThat(connector.count(ConnectorStub.MARK_PRICES)).isEqualTo(1);
        assertThat(connector.count(ConnectorStub.INDEX_PRICES)).isEqualTo(2);
        assertThat(connector.count(ConnectorStub.orderBookOf(INSTRUMENT))).isEqualTo(1);
        assertThat(connector.count(ConnectorStub.orderBookOf(SECOND_INSTRUMENT))).isEqualTo(1);
        assertThat(connector.count(ConnectorStub.orderBookOf(THIRD_INSTRUMENT))).isEqualTo(1);
        assertThat(rows.count("ticker_snapshots")).isEqualTo(3L);
        assertThat(rows.count("order_book_snapshots")).isEqualTo(3L);
        assertThat(rows.all("ticker_snapshots")).allSatisfy(snapshot -> {
            assertThat(snapshot.get("external_timestamp")).isEqualTo(EXCHANGE_MOMENT);
            assertThat(snapshot.get("observed_timestamp")).isNotNull();
        });
    }

    @Test
    @DisplayName("B5.2 — нерезолвившиеся марк-цена и индекс остаются пустыми")
    void b5_2_unresolvedMarkAndIndexPricesStayEmpty() {
        provisionInstruments(INSTRUMENT);
        connector.answers(ConnectorStub.TICKERS, Feed.map(
                Feed.ticker(INSTRUMENT, EXCHANGE_MOMENT, "50000")));
        connector.answers(ConnectorStub.MARK_PRICES, Feed.emptyMap());
        connector.answers(ConnectorStub.INDEX_PRICES, Feed.emptyMap());
        connector.answers(ConnectorStub.orderBookOf(INSTRUMENT), Feed.orderBook(EXCHANGE_MOMENT, 3));

        tick(Tick.SNAPSHOTS);

        Map<String, Object> snapshot = rows.all("ticker_snapshots").getFirst();
        assertThat(snapshot.get("last_price")).isNotNull();
        assertThat(snapshot.get("mark_price")).isNull();
        assertThat(snapshot.get("index_price")).isNull();
    }

    @Test
    @DisplayName("B5.3 — инструмент без валют индекса не получает")
    void b5_3_anInstrumentWithoutCurrenciesGetsNoIndex() {
        connector.answers(ConnectorStub.INSTRUMENTS, Feed.array(Feed.bareInstrument(INSTRUMENT)));
        connector.answers(ConnectorStub.rulesOf(INSTRUMENT), Feed.rules(INSTRUMENT));
        tick(Tick.INSTRUMENT_SYNC);
        connector.answers(ConnectorStub.TICKERS, Feed.map(
                Feed.ticker(INSTRUMENT, EXCHANGE_MOMENT, "50000")));
        connector.answers(ConnectorStub.MARK_PRICES, Feed.map(Feed.price(INSTRUMENT, "50010")));
        connector.answers(ConnectorStub.INDEX_PRICES, Feed.map(
                Feed.price("BTC-USDT", "50020"), Feed.price("BASE0-USDT", "50030")));
        connector.answers(ConnectorStub.orderBookOf(INSTRUMENT), Feed.orderBook(EXCHANGE_MOMENT, 3));

        tick(Tick.SNAPSHOTS);

        Map<String, Object> snapshot = rows.all("ticker_snapshots").getFirst();
        assertThat(snapshot.get("mark_price")).isNotNull();
        assertThat(snapshot.get("index_price")).isNull();
    }

    @Test
    @DisplayName("B5.4 — повтор момента площадки второй строкой не ложится")
    void b5_4_aRepeatedExchangeMomentDoesNotLieDownAsASecondRow() {
        provisionInstruments(INSTRUMENT);
        stubFullMarket();
        tick(Tick.SNAPSHOTS);
        Map<String, Object> firstTicker = rows.all("ticker_snapshots").getFirst();
        Map<String, Object> firstBook = rows.all("order_book_snapshots").getFirst();

        tick(Tick.SNAPSHOTS);

        assertThat(rows.count("ticker_snapshots")).isEqualTo(1L);
        assertThat(rows.count("order_book_snapshots")).isEqualTo(1L);
        assertThat(rows.all("ticker_snapshots").getFirst().get("observed_timestamp"))
                .isEqualTo(firstTicker.get("observed_timestamp"));
        assertThat(rows.all("order_book_snapshots").getFirst().get("observed_timestamp"))
                .isEqualTo(firstBook.get("observed_timestamp"));
    }

    @Test
    @DisplayName("B5.5 — отказ чтения книги по одному инструменту проход не роняет")
    void b5_5_aBookReadFailureOnOneInstrumentDoesNotBreakThePass() {
        provisionInstruments(INSTRUMENT, SECOND_INSTRUMENT, THIRD_INSTRUMENT);
        stubFullMarket();
        connector.answers(ConnectorStub.orderBookOf(SECOND_INSTRUMENT), 500,
                Feed.refusal("EXCHANGE_READ_FAILED"));
        Integer mark = AppLog.mark();

        tick(Tick.SNAPSHOTS);

        assertThat(booksOf(INSTRUMENT)).isEqualTo(1L);
        assertThat(booksOf(SECOND_INSTRUMENT)).isEqualTo(0L);
        assertThat(booksOf(THIRD_INSTRUMENT)).isEqualTo(1L);
        assertThat(AppLog.since(mark)).contains("Order book snapshot skipped for " + SECOND_INSTRUMENT);
    }

    @Test
    @DisplayName("B5.6 — отказ ПИСЬМА одной строки — тоже отказ по одному инструменту")
    void b5_6_aWriteFailureOfOneRowIsAlsoASingleInstrumentFailure() {
        provisionInstruments(INSTRUMENT, SECOND_INSTRUMENT, THIRD_INSTRUMENT);
        stubFullMarket();
        connector.answers(ConnectorStub.TICKERS, Feed.map(
                Feed.ticker(INSTRUMENT, EXCHANGE_MOMENT, "50000"),
                Feed.tickerWithoutTimestamp(SECOND_INSTRUMENT, "50100"),
                Feed.ticker(THIRD_INSTRUMENT, EXCHANGE_MOMENT, "50200")));
        Integer mark = AppLog.mark();

        tick(Tick.SNAPSHOTS);

        assertThat(tickersOf(INSTRUMENT)).isEqualTo(1L);
        assertThat(tickersOf(SECOND_INSTRUMENT)).isEqualTo(0L);
        assertThat(tickersOf(THIRD_INSTRUMENT)).isEqualTo(1L);
        assertThat(rows.count("order_book_snapshots")).isEqualTo(3L);
        assertThat(AppLog.since(mark)).contains("Ticker snapshot not written for " + SECOND_INSTRUMENT);
    }

    @Test
    @DisplayName("B5.7 — отказ доступа на тикерах прекращает проход до книг")
    void b5_7_anAccessRefusalOnTickersStopsThePassBeforeTheBooks() {
        provisionInstruments(INSTRUMENT, SECOND_INSTRUMENT);
        stubFullMarket();
        connector.answers(ConnectorStub.TICKERS, 429, Feed.refusal("RATE_LIMITED"));
        Integer mark = AppLog.mark();

        tick(Tick.SNAPSHOTS);

        assertThat(connector.count(ConnectorStub.orderBookOf(INSTRUMENT))).isEqualTo(0);
        assertThat(connector.count(ConnectorStub.orderBookOf(SECOND_INSTRUMENT))).isEqualTo(0);
        assertThat(rows.count("ticker_snapshots")).isEqualTo(0L);
        assertThat(rows.count("order_book_snapshots")).isEqualTo(0L);
        assertThat(AppLog.since(mark)).contains("Snapshot pass stopped on tickers");
    }

    @Test
    @DisplayName("B5.8 — рядовой отказ тикеров книги не отменяет")
    void b5_8_anOrdinaryTickerFailureDoesNotCancelTheBooks() {
        provisionInstruments(INSTRUMENT, SECOND_INSTRUMENT);
        stubFullMarket();
        connector.answers(ConnectorStub.TICKERS, 500, Feed.refusal("EXCHANGE_READ_FAILED"));
        Integer mark = AppLog.mark();

        tick(Tick.SNAPSHOTS);

        assertThat(rows.count("ticker_snapshots")).isEqualTo(0L);
        assertThat(rows.count("order_book_snapshots")).isEqualTo(2L);
        assertThat(AppLog.since(mark)).contains("Ticker snapshot skipped for this pass");
    }

    @Test
    @DisplayName("B5.9 — отказ доступа на книгах прекращает обход")
    void b5_9_anAccessRefusalOnTheBooksStopsTheRound() {
        provisionInstruments(INSTRUMENT, SECOND_INSTRUMENT, THIRD_INSTRUMENT);
        stubFullMarket();
        connector.answers(ConnectorStub.orderBookOf(SECOND_INSTRUMENT), 401,
                Feed.refusal("ACCESS_DENIED"));
        Integer mark = AppLog.mark();

        tick(Tick.SNAPSHOTS);

        assertThat(booksOf(INSTRUMENT)).isEqualTo(1L);
        assertThat(connector.count(ConnectorStub.orderBookOf(THIRD_INSTRUMENT))).isEqualTo(0);
        assertThat(rows.count("ticker_snapshots")).isEqualTo(3L);
        assertThat(AppLog.since(mark)).contains("Snapshot pass stopped");
    }

    @Test
    @DisplayName("B5.11 — в проход входит только действующий листинг")
    void b5_11_onlyTheStandingListingEntersThePass() {
        provisionInstruments(INSTRUMENT, SECOND_INSTRUMENT);
        stubFullMarket();
        // CREATED не пишет ни одна тропа сервиса (находка F-3): заведение
        // из листинга ставит SYNC сразу, и состояние ставится прямо.
        rows.put("update instruments set status = 'CREATED' where external_id = ?", SECOND_INSTRUMENT);
        rows.put("update instruments set status = 'ACTIVE' where external_id = ?", INSTRUMENT);

        tick(Tick.SNAPSHOTS);

        assertThat(connector.count(ConnectorStub.orderBookOf(SECOND_INSTRUMENT))).isEqualTo(0);
        assertThat(booksOf(SECOND_INSTRUMENT)).isEqualTo(0L);
        assertThat(tickersOf(SECOND_INSTRUMENT)).isEqualTo(0L);
        assertThat(booksOf(INSTRUMENT)).isEqualTo(1L);
        assertThat(tickersOf(INSTRUMENT)).isEqualTo(1L);
    }

    @Test
    @DisplayName("B5.12 — перекрывающий проход не догоняется")
    void b5_12_anOverlappingPassIsNotCaughtUp() {
        provisionInstruments(INSTRUMENT);
        stubFullMarket();
        connector.answersSlowly(ConnectorStub.orderBookOf(INSTRUMENT),
                Feed.orderBook(EXCHANGE_MOMENT, 3), 1500);

        overlappingTicks(Tick.SNAPSHOTS);

        assertThat(connector.count(ConnectorStub.TICKERS)).isEqualTo(1);
        assertThat(connector.count(ConnectorStub.orderBookOf(INSTRUMENT))).isEqualTo(1);
        assertThat(rows.count("order_book_snapshots")).isEqualTo(1L);
    }

    @Test
    @DisplayName("B9.4 — котировочные валюты — вход чтения индексов")
    void b9_4_quoteCurrenciesAreTheInputOfIndexReads() {
        provisionInstruments(INSTRUMENT);
        stubFullMarket();

        tick(Tick.SNAPSHOTS);

        assertThat(connector.count(ConnectorStub.INDEX_PRICES)).isEqualTo(2);
        assertThat(connector.requests(ConnectorStub.INDEX_PRICES).stream()
                .map(request -> request.getUrl().replaceAll(".*quoteCurrency=", ""))
                .toList())
                .containsExactlyInAnyOrder("USDT", "USD");
    }

    /** Полная рыночная картина: тикеры, марк-цены, индексы и книги. */
    private void stubFullMarket() {
        connector.answers(ConnectorStub.TICKERS, Feed.map(
                Feed.ticker(INSTRUMENT, EXCHANGE_MOMENT, "50000"),
                Feed.ticker(SECOND_INSTRUMENT, EXCHANGE_MOMENT, "3000"),
                Feed.ticker(THIRD_INSTRUMENT, EXCHANGE_MOMENT, "150")));
        connector.answers(ConnectorStub.MARK_PRICES, Feed.map(
                Feed.price(INSTRUMENT, "50010"),
                Feed.price(SECOND_INSTRUMENT, "3001"),
                Feed.price(THIRD_INSTRUMENT, "151")));
        connector.answers(ConnectorStub.INDEX_PRICES, Feed.map(
                Feed.price("BASE0-USDT", "50020"),
                Feed.price("BASE1-USDT", "3002"),
                Feed.price("BASE2-USDT", "152")));
        connector.answers(ConnectorStub.orderBookOf(INSTRUMENT), Feed.orderBook(EXCHANGE_MOMENT, 3));
        connector.answers(ConnectorStub.orderBookOf(SECOND_INSTRUMENT), Feed.orderBook(EXCHANGE_MOMENT, 3));
        connector.answers(ConnectorStub.orderBookOf(THIRD_INSTRUMENT), Feed.orderBook(EXCHANGE_MOMENT, 3));
    }

    private Long booksOf(String externalId) {
        return rows.countWhere("order_book_snapshots", "instrument_id", instrumentId(externalId));
    }

    private Long tickersOf(String externalId) {
        return rows.countWhere("ticker_snapshots", "instrument_id", instrumentId(externalId));
    }
}
