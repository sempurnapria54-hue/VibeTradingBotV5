package com.example.marketdata.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Чтения каталога и истории — группа {@code B7} документа
 * `.claude/tests/cases/market-data.md`.
 *
 * <p><b>Пустота и отказ здесь РАЗНЫЕ исходы, и это главный предмет
 * группы.</b> «Навеса ещё нет» отвечает {@code 204}, «инструмента нет»
 * — отказом негодного входа, «свечей нет» — пустым перечнем, а «единицы
 * сбора нет» — снова отказом: читатель, получивший пустой список вместо
 * отказа, заключил бы, что данных просто не собрано.
 *
 * <p><b>Наружу уходит идентичность, а не ключ базы</b>
 * (.claude/rules/codestyle.md §«Идентичность наружу»), и {@code B7.9}
 * мерит это по ВСЕМ читающим точкам сразу: одна забытая точка достаточна,
 * чтобы ключ утёк.
 */
class CatalogReadsBoxTest extends SharedMarketDataBox {

    private static final Integer PAGE_SIZE = 100;

    @Test
    @DisplayName("B7.1 — листинг отдаёт только действующее")
    void b7_1_theListingGivesOutOnlyTheStandingOnes() {
        provisionInstruments(INSTRUMENT, SECOND_INSTRUMENT, THIRD_INSTRUMENT, "XRP-USDT-SWAP");
        // CREATED писателя в сервисе не имеет (находка F-3): заведение из
        // листинга ставит SYNC сразу.
        rows.put("update instruments set status = 'CREATED' where external_id = ?", "XRP-USDT-SWAP");
        rows.put("update instruments set status = 'CANDLES_LOADING' where external_id = ?", SECOND_INSTRUMENT);
        rows.put("update instruments set status = 'ACTIVE' where external_id = ?", THIRD_INSTRUMENT);

        Answer answer = get(INSTRUMENTS);

        assertThat(answer.status()).isEqualTo(200);
        assertThat(answer.asList()).hasSize(3);
        assertThat(answer.asList().stream().map(instrument -> instrument.get("externalId")).toList())
                .containsExactlyInAnyOrder(INSTRUMENT, SECOND_INSTRUMENT, THIRD_INSTRUMENT);
    }

    @Test
    @DisplayName("B7.2 — навес правил: «правил нет» отличается от «правила пусты»")
    void b7_2_theRulesOverlayDistinguishesAbsenceFromEmptiness() {
        List<String> instruments = provisionInstruments(INSTRUMENT, SECOND_INSTRUMENT);
        rows.put("update instruments set external_rules = null where external_id = ?", SECOND_INSTRUMENT);

        Answer materialised = get(INSTRUMENTS + "/" + instruments.get(0) + "/rules");
        Answer absent = get(INSTRUMENTS + "/" + instruments.get(1) + "/rules");

        assertThat(materialised.status()).isEqualTo(200);
        assertThat(materialised.asObject()).containsKeys("externalTickSize", "externalLotSize");
        assertThat(absent.status()).isEqualTo(204);
        assertThat(absent.body()).isEmpty();
    }

    @Test
    @DisplayName("B7.3 — единицы сбора инструмента")
    void b7_3_theCollectionUnitsOfAnInstrument() {
        String instrument = provisionInstruments(INSTRUMENT).getFirst();
        requireCandles(instrument, HOUR, 300L);
        requireCandles(instrument, "ONE_DAY", 300L);
        loadSeries();

        Answer answer = get(INSTRUMENTS + "/" + instrument + "/candle-groups");

        assertThat(answer.status()).isEqualTo(200);
        assertThat(answer.asList()).hasSize(2);
        assertThat(answer.asList()).allSatisfy(group -> {
            assertThat(group).containsKeys("internalId", "timeframe", "status", "count",
                    "actualFirstUtcMillis", "actualLastUtcMillis");
            assertThat(group).doesNotContainKeys("id", "instrumentId");
        });
    }

    @Test
    @DisplayName("B7.4 — история читается окном, и окно обязательно")
    void b7_4_historyIsReadByAWindowAndTheWindowIsMandatory() {
        String instrument = provisionInstruments(INSTRUMENT).getFirst();
        requireCandles(instrument, HOUR, 300L);
        loadSeries();

        Answer unbounded = get(INSTRUMENTS + "/" + instrument + "/candles?timeframe=" + HOUR
                + "&fromMillis=0");
        Answer windowed = get(INSTRUMENTS + "/" + instrument + "/candles?timeframe=" + HOUR
                + "&fromMillis=0&limit=60");

        assertThat(unbounded.status()).isEqualTo(400);
        assertThat(windowed.status()).isEqualTo(200);
        assertThat(windowed.asList()).hasSize(60);
        List<Long> opens = windowed.asList().stream()
                .map(candle -> ((Number) candle.get("openTimestamp")).longValue())
                .toList();
        assertThat(opens).isSorted();
    }

    /**
     * Ожидание формы тела взято из дома: отказ, произведённый
     * контейнером, есть тот же контракт, что и отказ приложения
     * (docs/rules/error-handling-policy.md §«Отказ, произведённый
     * контейнером, — тот же контракт»). Предел объявлен полем запроса, и
     * нарушивший его вызов до тела контроллера не доходит.
     *
     * <p><b>Того, что выборки к базе не производится, клетка не
     * наблюдает</b>, и это названо: снаружи процесса видно отсутствие
     * ОТВЕТА, а не отсутствие запроса к своей же базе.
     */
    @Test
    @DisplayName("B7.5 — у окна есть потолок")
    void b7_5_theWindowHasACeiling() {
        String instrument = provisionInstruments(INSTRUMENT).getFirst();
        requireCandles(instrument, HOUR, 300L);
        loadSeries();

        Answer answer = get(INSTRUMENTS + "/" + instrument + "/candles?timeframe=" + HOUR
                + "&fromMillis=0&limit=50000");

        assertThat(answer.status()).isEqualTo(400);
        assertThat(answer.carriesErrorDto()).isTrue();
    }

    @Test
    @DisplayName("B7.6 — несуществующая единица сбора у существующего инструмента")
    void b7_6_anAbsentCollectionUnitOnAnExistingInstrument() {
        String instrument = provisionInstruments(INSTRUMENT).getFirst();
        requireCandles(instrument, HOUR, 300L);
        loadSeries();

        Answer answer = get(INSTRUMENTS + "/" + instrument
                + "/candles?timeframe=ONE_MINUTE&fromMillis=0&limit=10");

        assertThat(answer.carriesErrorDto()).isTrue();
        assertThat(answer.errorCode()).isEqualTo("INVALID_REQUEST");
    }

    @Test
    @DisplayName("B7.7 — несуществующий инструмент на любой тропе чтения")
    void b7_7_anAbsentInstrumentOnEveryReadPath() {
        List<Answer> answers = List.of(
                get(INSTRUMENTS + "/MD-NO-SUCH"),
                get(INSTRUMENTS + "/MD-NO-SUCH/rules"),
                get(INSTRUMENTS + "/MD-NO-SUCH/candle-groups"),
                get(INSTRUMENTS + "/MD-NO-SUCH/candles?timeframe=" + HOUR + "&fromMillis=0&limit=10"),
                get(INSTRUMENTS + "/MD-NO-SUCH/order-book/latest"),
                get(INSTRUMENTS + "/MD-NO-SUCH/ticker/latest"),
                get(INSTRUMENTS + "/MD-NO-SUCH/prices"),
                post(INSTRUMENTS + "/MD-NO-SUCH/features", "{}"));

        assertThat(answers).allSatisfy(answer -> {
            assertThat(answer.carriesErrorDto()).isTrue();
            assertThat(answer.errorCode()).isEqualTo("INVALID_REQUEST");
        });
    }

    @Test
    @DisplayName("B7.8 — последние срезы отдаются по одному правилу")
    void b7_8_theLatestSnapshotsAreGivenByOneRule() {
        List<String> instruments = provisionInstruments(INSTRUMENT, SECOND_INSTRUMENT);
        stubSnapshotsOf(INSTRUMENT, 1_758_000_000_000L, "50000");
        tick(Tick.SNAPSHOTS);
        stubSnapshotsOf(INSTRUMENT, 1_758_000_060_000L, "50500");
        tick(Tick.SNAPSHOTS);

        Answer book = get(INSTRUMENTS + "/" + instruments.get(0) + "/order-book/latest");
        Answer ticker = get(INSTRUMENTS + "/" + instruments.get(0) + "/ticker/latest");
        Answer emptyBook = get(INSTRUMENTS + "/" + instruments.get(1) + "/order-book/latest");
        Answer emptyTicker = get(INSTRUMENTS + "/" + instruments.get(1) + "/ticker/latest");

        assertThat(book.status()).isEqualTo(200);
        assertThat(ticker.status()).isEqualTo(200);
        assertThat(book.asObject().get("externalTimestamp")).isEqualTo(1_758_000_060_000L);
        assertThat(ticker.asObject().get("externalTimestamp")).isEqualTo(1_758_000_060_000L);
        assertThat(book.asObject().get("instrumentInternalId")).isEqualTo(instruments.get(0));
        assertThat(ticker.asObject().get("instrumentInternalId")).isEqualTo(instruments.get(0));
        assertThat(emptyBook.status()).isEqualTo(204);
        assertThat(emptyTicker.status()).isEqualTo(204);
    }

    @Test
    @DisplayName("B7.9 — наружу уходит идентичность, а не ключ базы")
    void b7_9_identityTravelsOutNotTheDatabaseKey() {
        String instrument = provisionInstruments(INSTRUMENT).getFirst();
        requireCandles(instrument, HOUR, 300L);
        loadSeries();
        String atr = requireIndicator("ATR", HOUR, "{\"period\": 14}");
        tick(Tick.INDICATORS);
        stubSnapshotsOf(INSTRUMENT, 1_758_000_000_000L, "50000");
        tick(Tick.SNAPSHOTS);

        List<Answer> answers = List.of(
                get(INSTRUMENTS),
                get(INSTRUMENTS + "/" + instrument),
                get(INSTRUMENTS + "/" + instrument + "/candle-groups"),
                get(INSTRUMENTS + "/" + instrument + "/candles?timeframe=" + HOUR
                        + "&fromMillis=0&limit=10"),
                get(INSTRUMENTS + "/" + instrument + "/order-book/latest"),
                get(INSTRUMENTS + "/" + instrument + "/ticker/latest"),
                get(INSTRUMENTS + "/" + instrument + "/indicator-values/latest?configInternalId="
                        + atr + "&tolerance=PT10000H"),
                post(INSTRUMENTS + "/" + instrument + "/features", "{}"));

        assertThat(answers).allSatisfy(answer -> {
            assertThat(answer.status()).isEqualTo(200);
            assertThat(answer.body()).doesNotContain("\"id\"");
            assertThat(answer.body()).doesNotContain("\"instrumentId\"");
            assertThat(answer.body()).doesNotContain("\"candleGroupId\"");
            assertThat(answer.body()).doesNotContain("\"indicatorConfigId\"");
        });
    }

    @Test
    @DisplayName("B7.10 — инструмент каталога читается по идентичности")
    void b7_10_aCatalogInstrumentIsReadByItsIdentity() {
        List<String> instruments = provisionInstruments(INSTRUMENT, SECOND_INSTRUMENT);
        // Отбор у одиночного чтения СВОЙ — по идентичности, а не по
        // действующим статусам: инструмент вне листинга читается им тоже.
        rows.put("update instruments set status = 'CLOSED' where external_id = ?", SECOND_INSTRUMENT);

        Answer standing = get(INSTRUMENTS + "/" + instruments.get(0));
        Answer closed = get(INSTRUMENTS + "/" + instruments.get(1));

        assertThat(standing.status()).isEqualTo(200);
        assertThat(standing.asObject().get("internalId")).isEqualTo(instruments.get(0));
        assertThat(standing.asObject().get("externalId")).isEqualTo(INSTRUMENT);
        assertThat(closed.status()).isEqualTo(200);
        assertThat(closed.asObject().get("status")).isEqualTo("CLOSED");
        assertThat(get(INSTRUMENTS).asList()).hasSize(1);
    }

    @Test
    @DisplayName("B7.11 — цены момента отдаются составом «последняя, марк, индекс»")
    void b7_11_momentPricesAreGivenAsLastMarkAndIndex() {
        String instrument = provisionInstruments(INSTRUMENT).getFirst();
        connector.answers(ConnectorStub.pricesOf(INSTRUMENT), Feed.prices(INSTRUMENT, "50500"));

        Answer answer = get(INSTRUMENTS + "/" + instrument + "/prices");

        assertThat(answer.status()).isEqualTo(200);
        assertThat(answer.asObject().get("externalLastPrice")).isNotNull();
        assertThat(answer.asObject().get("externalAskPrice")).isNotNull();
        assertThat(answer.asObject().get("externalBidPrice")).isNotNull();
        assertThat(answer.asObject().get("externalInstrumentId")).isEqualTo(INSTRUMENT);
        assertThat(connector.count(ConnectorStub.pricesOf(INSTRUMENT))).isEqualTo(1);
    }

    private void loadSeries() {
        connector.answers(ConnectorStub.HISTORY_CANDLES,
                Feed.trendingCandles(barsAgo(HOUR_MILLIS, 400), HOUR_MILLIS, PAGE_SIZE, 50000));
        tick(Tick.CANDLES);
        tick(Tick.CANDLES);
    }

    private void stubSnapshotsOf(String externalId, Long moment, String lastPrice) {
        connector.answers(ConnectorStub.TICKERS, Feed.map(Feed.ticker(externalId, moment, lastPrice)));
        connector.answers(ConnectorStub.MARK_PRICES, Feed.emptyMap());
        connector.answers(ConnectorStub.INDEX_PRICES, Feed.emptyMap());
        connector.answers(ConnectorStub.orderBookOf(externalId), Feed.orderBook(moment, 3));
        connector.answers(ConnectorStub.orderBookOf(SECOND_INSTRUMENT), 500,
                Feed.refusal("EXCHANGE_READ_FAILED"));
    }
}
