package com.example.marketdata.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Отсутствие выходов — группа {@code B10} документа
 * `.claude/tests/cases/market-data.md`.
 *
 * <p><b>Отрицание наблюдается ТЕМ, ЧЕГО В СУБСТРАТЕ НЕТ.</b> Брокера
 * прогон не поднимает вовсе, и «событий сервис не публикует» стои́т ровно
 * на том, что весь набор мутирующих троп проходит при отсутствующем
 * брокере; чужой базы в прогоне нет, и «в чужие базы не ходит» стои́т на
 * том же. Подними прогон брокер «на всякий случай» — обе клетки
 * перестали бы утверждать что-либо.
 *
 * <p><b>Стабы соседей, наоборот, ПОДНЯТЫ и отвечают</b>: отрицание «их не
 * зовут» иначе не наблюдаемо — вызов на мёртвый адрес и вызов, которого
 * не было, снаружи неразличимы.
 */
class AbsentOutputsBoxTest extends SharedMarketDataBox {

    private static final Long EXCHANGE_MOMENT = 1_758_000_000_000L;

    private static final Integer PAGE_SIZE = 100;

    @Test
    @DisplayName("B10.1 — сервис не публикует событий")
    void b10_1_theServicePublishesNoEvents() {
        fullTropeSet();

        // Брокера в субстрате нет: ни одна из мутирующих троп его не
        // искала, иначе прогон падал бы на недоступности.
        assertThat(rows.tableNames()).noneSatisfy(table ->
                assertThat(table.toLowerCase()).contains("outbox"));
        assertThat(rows.tableNames()).noneSatisfy(table ->
                assertThat(table.toLowerCase()).contains("event"));
    }

    @Test
    @DisplayName("B10.2 — сервис не потребляет событий")
    void b10_2_theServiceConsumesNoEvents() {
        // Контекст поднят и отвечает при отсутствующем брокере: слушателя,
        // которому нужна группа потребления, в нём нет ни одного.
        Answer health = getAnonymously("/actuator/health");

        assertThat(health.status()).isEqualTo(200);
        assertThat(health.asObject().get("status")).isEqualTo("UP");
        assertThat(rows.tableNames()).noneSatisfy(table ->
                assertThat(table.toLowerCase()).contains("offset"));
    }

    @Test
    @DisplayName("B10.3 — исходящие обращения ограничены двумя адресами")
    void b10_3_outgoingCallsAreLimitedToTwoAddresses() {
        fullTropeSet();

        assertThat(neighbours).allSatisfy(neighbour ->
                assertThat(neighbour.count())
                        .describedAs("сосед %s получил запрос", neighbour.name())
                        .isEqualTo(0));
        assertThat(connector.count()).isGreaterThan(0);
    }

    @Test
    @DisplayName("B10.4 — сервис не ходит в чужие базы")
    void b10_4_theServiceDoesNotGoIntoForeignDatabases() {
        fullTropeSet();

        // Единственная доступная база — своя: весь набор троп прошёл, и
        // второго источника данных контекст не объявляет.
        assertThat(rows.appliedMigrations()).contains("1");
        assertThat(rows.count("instruments")).isGreaterThan(0L);
        assertThat(rows.tableNames()).noneSatisfy(table ->
                assertThat(table.toLowerCase()).contains("strateg"));
    }

    @Test
    @DisplayName("B10.5 — ряды не чистятся ни одним тиком")
    void b10_5_noTickCleansTheSeries() {
        fullTropeSet();
        // Строка много старше любой объявленной глубины: её возраст —
        // годы, а не часы.
        rows.put("insert into ticker_snapshots (instrument_id, external_timestamp, observed_timestamp, "
                + "last_price) values (?, ?, ?, 1)", instrumentId(INSTRUMENT), 1L, 1L);
        rows.put("insert into candles (candle_group_id, open_timestamp, open, high, low, close) "
                + "values (?, ?, 1, 1, 1, 1)", groupId(), 1L);
        Map<String, Long> before = rows.countsByTable();

        ticks();

        Map<String, Long> after = rows.countsByTable();
        assertThat(after.get("ticker_snapshots")).isGreaterThanOrEqualTo(before.get("ticker_snapshots"));
        assertThat(after.get("candles")).isGreaterThanOrEqualTo(before.get("candles"));
        assertThat(after.get("order_book_snapshots"))
                .isGreaterThanOrEqualTo(before.get("order_book_snapshots"));
        assertThat(after.get("indicator_values")).isGreaterThanOrEqualTo(before.get("indicator_values"));
        assertThat(rows.countWhere("candles", "open_timestamp", 1L)).isEqualTo(1L);
        assertThat(rows.countWhere("ticker_snapshots", "external_timestamp", 1L)).isEqualTo(1L);
    }

    @Test
    @DisplayName("B10.6 — торговых решений сервис не принимает")
    void b10_6_theServiceTakesNoTradingDecisions() {
        fullTropeSet();

        List<String> asked = connector.paths();
        assertThat(asked).isNotEmpty();
        assertThat(asked).allSatisfy(path ->
                assertThat(path).startsWith("/api/v1/market/"));
        assertThat(asked).noneSatisfy(path -> assertThat(path).contains("/orders"));
        assertThat(asked).noneSatisfy(path -> assertThat(path).contains("/accounts"));
        assertThat(asked).noneSatisfy(path -> assertThat(path).contains("/positions"));
    }

    /**
     * Весь набор троп прогона: каталог, требования, пять тиков и чтения.
     *
     * <p>Отрицания группы абсолютны по предмету, и потому вход у них один
     * и тот же — ПОЛНЫЙ: отрицание, проверенное на одной тропе, о
     * соседней не говорит ничего.
     */
    private void fullTropeSet() {
        String instrument = provisionInstruments(INSTRUMENT).getFirst();
        requireCandles(instrument, HOUR, 300L);
        requireIndicator("ATR", HOUR, "{\"period\": 14}");
        requireStructure(HOUR, Bodies.structureParams(50), null, null);
        connector.answers(ConnectorStub.HISTORY_CANDLES,
                Feed.trendingCandles(barsAgo(HOUR_MILLIS, 400), HOUR_MILLIS, PAGE_SIZE, 50000));
        connector.answers(ConnectorStub.CANDLES, Feed.empty());
        connector.answers(ConnectorStub.TICKERS, Feed.map(
                Feed.ticker(INSTRUMENT, EXCHANGE_MOMENT, "50000")));
        connector.answers(ConnectorStub.MARK_PRICES, Feed.emptyMap());
        connector.answers(ConnectorStub.INDEX_PRICES, Feed.emptyMap());
        connector.answers(ConnectorStub.orderBookOf(INSTRUMENT), Feed.orderBook(EXCHANGE_MOMENT, 3));
        connector.answers(ConnectorStub.pricesOf(INSTRUMENT), Feed.prices(INSTRUMENT, "50500"));
        ticks();
        get(INSTRUMENTS);
        get(INSTRUMENTS + "/" + instrument + "/candle-groups");
        get(INSTRUMENTS + "/" + instrument + "/prices");
        post(INSTRUMENTS + "/" + instrument + "/features",
                Bodies.featureRead(Bodies.array(), Bodies.array(), Boolean.TRUE));
    }

    private void ticks() {
        tick(Tick.INSTRUMENT_SYNC);
        tick(Tick.CANDLES);
        tick(Tick.CANDLES);
        tick(Tick.INDICATORS);
        tick(Tick.MARKET_STRUCTURES);
        tick(Tick.SNAPSHOTS);
    }

    private Long groupId() {
        return ((Number) rows.all("candle_groups").getFirst().get("id")).longValue();
    }
}
