package com.example.marketdata.box;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Загрузка свечей — группа {@code B3} документа
 * `.claude/tests/cases/market-data.md`.
 *
 * <p><b>Цикл единицы сбора наблюдается ДВУМЯ сторонами:</b> статусом и
 * границами в базе (поверхности у них, кроме чтения единиц сбора, нет) и
 * записями стаба — «чтения на этом тике нет» иначе не наблюдаемо ничем.
 *
 * <p><b>Возраст ставится в данных.</b> Бары приезжают с открытиями,
 * отсчитанными от момента прогона шагом таймфрейма ({@link #barsAgo}), а
 * часы процесса не двигаются: иначе кейс мерил бы подмену времени, а не
 * поведение цикла.
 */
class CandleLoadingBoxTest extends SharedMarketDataBox {

    /** Размер страницы загрузки: величина конфигурации сервиса. */
    private static final Integer PAGE_SIZE = 100;

    @Test
    @DisplayName("B3.1 — заведённая группа уходит в бэкфилл")
    void b3_1_aCreatedUnitGoesToBackfill() {
        String instrument = provisionInstruments(INSTRUMENT).getFirst();
        requireCandles(instrument, HOUR, 300L);
        connector.answers(ConnectorStub.HISTORY_CANDLES, page(400, PAGE_SIZE));

        tick(Tick.CANDLES);

        assertThat(status()).isEqualTo("BACKFILL");
        assertThat(connector.count(ConnectorStub.HISTORY_CANDLES)).isEqualTo(0);
        assertThat(rows.count("candles")).isEqualTo(0L);
    }

    @Test
    @DisplayName("B3.2 — бэкфилл тянет историю назад и фиксирует границы")
    void b3_2_backfillPullsHistoryBackAndFixesTheBoundaries() {
        String instrument = provisionInstruments(INSTRUMENT).getFirst();
        requireCandles(instrument, HOUR, 1000L);
        connector.answers(ConnectorStub.HISTORY_CANDLES, page(400, PAGE_SIZE));
        tick(Tick.CANDLES);

        tick(Tick.CANDLES);
        Long loadedFirst = barsAgo(HOUR_MILLIS, 400);
        connector.answersWhen(ConnectorStub.HISTORY_CANDLES, "afterMillis",
                String.valueOf(loadedFirst), page(1100, PAGE_SIZE));
        tick(Tick.CANDLES);

        assertThat(rows.count("candles")).isEqualTo(2L * PAGE_SIZE);
        Map<String, Object> group = rows.all("candle_groups").getFirst();
        assertThat(group.get("count")).isEqualTo(2L * PAGE_SIZE);
        assertThat(group.get("actual_first_utc_millis")).isEqualTo(barsAgo(HOUR_MILLIS, 1100));
        assertThat(group.get("actual_last_utc_millis")).isEqualTo(barsAgo(HOUR_MILLIS, 301));
        List<LoggedRequest> reads = connector.requests(ConnectorStub.HISTORY_CANDLES);
        assertThat(reads).hasSize(2);
        assertThat(reads.get(1).getUrl()).contains("afterMillis=" + loadedFirst);
        assertThat(reads.get(1).getUrl()).contains("limit=" + PAGE_SIZE);
    }

    @Test
    @DisplayName("B3.3 — пустой ответ площадки завершает бэкфилл")
    void b3_3_anEmptyExchangeAnswerCompletesBackfill() {
        String instrument = provisionInstruments(INSTRUMENT).getFirst();
        post(REQUIREMENTS + "/candles", Bodies.candleRequirement(instrument, HOUR));
        connector.answers(ConnectorStub.HISTORY_CANDLES, page(400, PAGE_SIZE));
        tick(Tick.CANDLES);
        tick(Tick.CANDLES);
        assertThat(status()).isEqualTo("BACKFILL");
        connector.answersWhen(ConnectorStub.HISTORY_CANDLES, "afterMillis",
                String.valueOf(barsAgo(HOUR_MILLIS, 400)), Feed.empty());
        connector.forgetRequests();

        tick(Tick.CANDLES);

        assertThat(status()).isEqualTo("CHECK");
        assertThat(rows.all("candle_groups").getFirst().get("actual_first_utc_millis"))
                .isEqualTo(barsAgo(HOUR_MILLIS, 400));
        assertThat(connector.count(ConnectorStub.HISTORY_CANDLES)).isEqualTo(1);
    }

    @Test
    @DisplayName("B3.4 — достигнутый горизонт завершает бэкфилл")
    void b3_4_aReachedHorizonCompletesBackfill() {
        String instrument = provisionInstruments(INSTRUMENT).getFirst();
        requireCandles(instrument, HOUR, 300L);
        connector.answers(ConnectorStub.HISTORY_CANDLES, page(400, PAGE_SIZE));
        tick(Tick.CANDLES);

        tick(Tick.CANDLES);

        assertThat(status()).isEqualTo("CHECK");
        Integer readsWhenComplete = connector.count(ConnectorStub.HISTORY_CANDLES);
        tick(Tick.CANDLES);
        assertThat(connector.count(ConnectorStub.HISTORY_CANDLES)).isEqualTo(readsWhenComplete);
    }

    @Test
    @DisplayName("B3.5 — плотный ряд на проверке даёт готовность")
    void b3_5_aDenseSeriesOnCheckYieldsReadiness() {
        loadDenseSeries();
        assertThat(status()).isEqualTo("CHECK");
        connector.forgetRequests();

        tick(Tick.CANDLES);

        assertThat(status()).isEqualTo("ACTIVE");
        assertThat(connector.count(ConnectorStub.HISTORY_CANDLES)).isEqualTo(0);
        assertThat(connector.count(ConnectorStub.CANDLES)).isEqualTo(0);
    }

    @Test
    @DisplayName("B3.6 — дефицит на проверке уводит в починку")
    void b3_6_aDeficitOnCheckLeadsToRepair() {
        loadDenseSeries();
        punchHole();
        connector.forgetRequests();

        tick(Tick.CANDLES);

        assertThat(status()).isEqualTo("REPAIR");
        assertThat(connector.count(ConnectorStub.HISTORY_CANDLES)).isEqualTo(0);
    }

    @Test
    @DisplayName("B3.7 — починка локализует дыру и возвращает ряд к плотности")
    void b3_7_repairLocatesTheHoleAndReturnsTheSeriesToDensity() {
        loadDenseSeries();
        punchHole();
        tick(Tick.CANDLES);
        assertThat(status()).isEqualTo("REPAIR");
        connector.forgetRequests();

        tick(Tick.CANDLES);

        List<LoggedRequest> reads = connector.requests(ConnectorStub.HISTORY_CANDLES);
        assertThat(reads).hasSize(1);
        // Окно локализовано, а не перечитано целиком: запрос уходит от
        // границы найденного окна, а не от начала ряда.
        assertThat(reads.getFirst().getUrl())
                .doesNotContain("afterMillis=" + barsAgo(HOUR_MILLIS, 400));
        assertThat(status()).isEqualTo("CHECK");

        tick(Tick.CANDLES);

        assertThat(status()).isEqualTo("ACTIVE");
        assertThat(rows.count("candles")).isEqualTo(Long.valueOf(PAGE_SIZE));
    }

    @Test
    @DisplayName("B3.10 — докачка хвоста уводит готовую группу в синхронизацию")
    void b3_10_tailSyncMovesAReadyUnitToSynchronisation() {
        loadDenseSeries();
        tick(Tick.CANDLES);
        assertThat(status()).isEqualTo("ACTIVE");
        // Два бара, закрывшихся ПОСЛЕ верхней границы ряда: хвост, ради
        // которого готовая группа и возвращается в синхронизацию.
        connector.answers(ConnectorStub.CANDLES, page(300, 2));
        connector.forgetRequests();

        tick(Tick.CANDLES);

        assertThat(connector.count(ConnectorStub.CANDLES)).isEqualTo(1);
        assertThat(status()).isEqualTo("CHECK");
        assertThat(rows.count("candles")).isEqualTo(Long.valueOf(PAGE_SIZE + 2));

        tick(Tick.CANDLES);

        assertThat(status()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("B3.11 — готовность инструмента считается по его группам")
    void b3_11_instrumentReadinessIsComputedFromItsUnits() {
        String instrument = provisionInstruments(INSTRUMENT).getFirst();
        requireCandles(instrument, HOUR, 300L);
        requireCandles(instrument, "ONE_DAY", 300L);
        rows.put("update candle_groups set status = 'ACTIVE' where timeframe = ?", HOUR);
        rows.put("update candle_groups set status = 'SYNC' where timeframe = 'ONE_DAY'");
        connector.answers(ConnectorStub.CANDLES, Feed.empty());

        tick(Tick.CANDLES);

        assertThat(instrumentStatus()).isEqualTo("CANDLES_LOADING");

        rows.put("update candle_groups set status = 'ACTIVE'");
        tick(Tick.CANDLES);

        assertThat(instrumentStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("B3.12 — инструмент без единой группы остаётся в SYNC")
    void b3_12_anInstrumentWithoutAnyUnitStaysInSync() {
        provisionInstruments(INSTRUMENT);

        tick(Tick.CANDLES);

        assertThat(instrumentStatus()).isEqualTo("SYNC");
        assertThat(rows.count("candle_groups")).isEqualTo(0L);
    }

    @Test
    @DisplayName("B3.13 — популяция пересчёта готовности двусторонняя")
    void b3_13_theReadinessRecomputePopulationIsTwoSided() {
        String instrument = provisionInstruments(INSTRUMENT).getFirst();
        requireCandles(instrument, HOUR, 300L);
        rows.put("update candle_groups set status = 'ACTIVE'");
        rows.put("update instruments set status = 'CANDLES_LOADING'");

        tick(Tick.CANDLES);

        assertThat(instrumentStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("B3.14 — отказ доступа прекращает тик, рядовой отказ — одну группу")
    void b3_14_anAccessRefusalStopsTheTickAndAnOrdinaryOneStopsASingleUnit() {
        List<String> instruments = provisionInstruments(INSTRUMENT, SECOND_INSTRUMENT, THIRD_INSTRUMENT);
        instruments.forEach(internalId -> requireCandles(internalId, HOUR, 300L));
        rows.put("update candle_groups set status = 'BACKFILL'");
        connector.answers(ConnectorStub.HISTORY_CANDLES, page(400, PAGE_SIZE));
        connector.answersWhen(ConnectorStub.HISTORY_CANDLES, "externalInstrumentId", SECOND_INSTRUMENT,
                429, Feed.refusal("RATE_LIMITED"));
        connector.forgetRequests();

        tick(Tick.CANDLES);

        assertThat(readsFor(INSTRUMENT)).isEqualTo(1);
        assertThat(readsFor(SECOND_INSTRUMENT)).isEqualTo(1);
        assertThat(readsFor(THIRD_INSTRUMENT)).isEqualTo(0);

        rows.put("update candle_groups set status = 'BACKFILL'");
        connector.answersWhen(ConnectorStub.HISTORY_CANDLES, "externalInstrumentId", SECOND_INSTRUMENT,
                500, Feed.refusal("EXCHANGE_READ_FAILED"));
        connector.forgetRequests();

        tick(Tick.CANDLES);

        assertThat(readsFor(THIRD_INSTRUMENT)).isEqualTo(1);
    }

    @Test
    @DisplayName("B3.15 — повтор страницы вторых свечей не создаёт")
    void b3_15_aRepeatedPageCreatesNoSecondCandles() {
        String instrument = provisionInstruments(INSTRUMENT).getFirst();
        requireCandles(instrument, HOUR, 300L);
        rows.put("update candle_groups set status = 'SYNC'");
        connector.answers(ConnectorStub.CANDLES, page(400, PAGE_SIZE));

        tick(Tick.CANDLES);
        Long afterFirst = rows.count("candles");
        rows.put("update candle_groups set status = 'SYNC'");
        tick(Tick.CANDLES);

        assertThat(rows.count("candles")).isEqualTo(afterFirst);
        assertThat(rows.all("candle_groups").getFirst().get("count")).isEqualTo(afterFirst);
    }

    @Test
    @DisplayName("B3.16 — незакрытый бар в ряд не попадает")
    void b3_16_anUnclosedBarDoesNotEnterTheSeries() {
        String instrument = provisionInstruments(INSTRUMENT).getFirst();
        requireCandles(instrument, HOUR, 300L);
        rows.put("update candle_groups set status = 'SYNC'");
        connector.answers(ConnectorStub.CANDLES, page(10, 8));

        tick(Tick.CANDLES);

        // Второго фильтра у сервиса нет: в ряду ровно то, что отдал
        // коннектор, — признак закрытия виден ему и наружу не едет.
        assertThat(rows.count("candles")).isEqualTo(8L);
        Long newest = ((Number) rows.all("candle_groups").getFirst()
                .get("actual_last_utc_millis")).longValue();
        assertThat(System.currentTimeMillis() - newest).isGreaterThanOrEqualTo(HOUR_MILLIS);
    }

    /** Ряд из одной плотной страницы, доведённый до проверки целостности. */
    private void loadDenseSeries() {
        String instrument = provisionInstruments(INSTRUMENT).getFirst();
        requireCandles(instrument, HOUR, 300L);
        connector.answers(ConnectorStub.HISTORY_CANDLES, page(400, PAGE_SIZE));
        tick(Tick.CANDLES);
        tick(Tick.CANDLES);
    }

    /** Вырезает бары из середины ряда: дыра, которой цикл ещё не видел. */
    private void punchHole() {
        Long from = barsAgo(HOUR_MILLIS, 350);
        Long to = barsAgo(HOUR_MILLIS, 348);
        rows.put("delete from candles where open_timestamp between ? and ?", from, to);
    }

    /** Страница закрытых баров: {@code barsBack} назад, длиной {@code size}. */
    private static String page(Integer barsBack, Integer size) {
        return Feed.candles(barsAgo(HOUR_MILLIS, barsBack), HOUR_MILLIS, size, 50000);
    }

    private Integer readsFor(String externalId) {
        return (int) connector.requests(ConnectorStub.HISTORY_CANDLES).stream()
                .filter(request -> request.getUrl().contains("externalInstrumentId=" + externalId))
                .count();
    }

    private String status() {
        return String.valueOf(rows.all("candle_groups").getFirst().get("status"));
    }

    private String instrumentStatus() {
        return String.valueOf(rows.row("instruments", "external_id", INSTRUMENT).get("status"));
    }
}
