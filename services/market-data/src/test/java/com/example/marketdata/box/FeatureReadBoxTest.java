package com.example.marketdata.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Фичи на момент решения и свежесть — группа {@code B6} документа
 * `.claude/tests/cases/market-data.md`.
 *
 * <p><b>Срок свежести — операнд ВЫЗОВА, и потому он вход дважды:</b> и
 * как параметр запроса, и как расстояние между меткой данных и моментом
 * прогона. Отсюда форма всей группы: ряд кладётся близко к моменту
 * прогона минутными барами, а различают клетки не данные, а срок, с
 * которым их спрашивают (docs/rules/market-data-freshness.md).
 *
 * <p><b>Отсутствующее и устаревшее отвечают одинаково.</b> Обе пустоты
 * означают «данным доверять нельзя» и ведут к одной реакции; различать их
 * читателю не нужно — и потому ключа в раскладке просто нет.
 */
class FeatureReadBoxTest extends SharedMarketDataBox {

    private static final String MINUTE = "ONE_MINUTE";

    private static final Long MINUTE_MILLIS = 60_000L;

    /** Срок, под который последнее значение ряда свежо. */
    private static final String WIDE = "PT10M";

    /** Срок, под который то же значение уже старо. */
    private static final String NARROW = "PT1S";

    private static final String FAST = "быстрый";

    private static final String SLOW = "медленный";

    private static final String SHAPE = "структура";

    @Test
    @DisplayName("B6.1 — связка фич снимается одним чтением по авторским именам")
    void b6_1_theFeatureBundleIsTakenByOneReadUnderAuthorNames() {
        Series series = series();

        Answer answer = post(features(series.instrument()), Bodies.featureRead(
                Bodies.array(Bodies.binding(FAST, series.atr(), WIDE),
                        Bodies.binding(SLOW, series.ema(), WIDE)),
                Bodies.array(Bodies.binding(SHAPE, series.structure(), WIDE)),
                Boolean.FALSE));

        assertThat(answer.status()).isEqualTo(200);
        assertThat(answer.nested("latestIndicators")).containsOnlyKeys(FAST, SLOW);
        assertThat(answer.nested("previousIndicators")).containsOnlyKeys(FAST, SLOW);
        assertThat(answer.nested("structures")).containsOnlyKeys(SHAPE);
        assertThat(answer.asObject().get("marketPriceData")).isNull();
        assertThat(answer.asObject().get("marketPhase")).isNull();
        assertThat(connector.count()).isEqualTo(0);
    }

    @Test
    @DisplayName("B6.2 — устаревшее значение места в раскладке не занимает")
    void b6_2_aStaleValueTakesNoPlaceInTheLayout() {
        Series series = series();

        Answer answer = post(features(series.instrument()), Bodies.featureRead(
                Bodies.array(Bodies.binding(FAST, series.atr(), NARROW)),
                Bodies.array(), Boolean.FALSE));

        assertThat(answer.status()).isEqualTo(200);
        assertThat(answer.nested("latestIndicators")).doesNotContainKey(FAST);
        assertThat(answer.nested("previousIndicators")).containsKey(FAST);
    }

    @Test
    @DisplayName("B6.3 — отсутствующее и устаревшее отвечают одинаково")
    void b6_3_theAbsentAndTheStaleAnswerAlike() {
        Series series = series();
        // Идентичность на таймфрейме, по которому не собрана ни одна
        // группа: значений у неё нет вовсе.
        String never = requireIndicator("ATR", "ONE_DAY", "{\"period\": 14}");

        Answer answer = post(features(series.instrument()), Bodies.featureRead(
                Bodies.array(Bodies.binding(FAST, series.atr(), NARROW),
                        Bodies.binding(SLOW, never, WIDE)),
                Bodies.array(), Boolean.FALSE));

        assertThat(answer.status()).isEqualTo(200);
        assertThat(answer.nested("latestIndicators")).doesNotContainKeys(FAST, SLOW);
    }

    @Test
    @DisplayName("B6.4 — один и тот же ряд двум читателям с разными сроками")
    void b6_4_theSameSeriesToTwoReadersWithDifferentTerms() {
        Series series = series();
        Map<String, Object> before = rows.all("indicator_values").getFirst();

        Answer wide = post(features(series.instrument()), Bodies.featureRead(
                Bodies.array(Bodies.binding(FAST, series.atr(), WIDE)),
                Bodies.array(), Boolean.FALSE));
        Answer narrow = post(features(series.instrument()), Bodies.featureRead(
                Bodies.array(Bodies.binding(FAST, series.atr(), "PT1M")),
                Bodies.array(), Boolean.FALSE));

        assertThat(wide.nested("latestIndicators")).containsKey(FAST);
        assertThat(narrow.nested("latestIndicators")).doesNotContainKey(FAST);
        assertThat(rows.all("indicator_values").getFirst()).isEqualTo(before);
    }

    @Test
    @DisplayName("B6.5 — цена читается у площадки, только когда её спрашивают")
    void b6_5_thePriceIsReadFromTheExchangeOnlyWhenAsked() {
        Series series = series();
        connector.answers(ConnectorStub.pricesOf(INSTRUMENT), Feed.prices(INSTRUMENT, "50500"));
        connector.forgetRequests();

        Answer silent = post(features(series.instrument()), Bodies.featureRead(
                Bodies.array(Bodies.binding(FAST, series.atr(), WIDE)),
                Bodies.array(), Boolean.FALSE));
        assertThat(silent.asObject().get("marketPriceData")).isNull();
        assertThat(connector.count(ConnectorStub.pricesOf(INSTRUMENT))).isEqualTo(0);

        Answer asking = post(features(series.instrument()), Bodies.featureRead(
                Bodies.array(Bodies.binding(FAST, series.atr(), WIDE)),
                Bodies.array(), Boolean.TRUE));

        assertThat(asking.status()).isEqualTo(200);
        assertThat(asking.nested("marketPriceData")).containsKey("externalLastPrice");
        assertThat(connector.count(ConnectorStub.pricesOf(INSTRUMENT))).isEqualTo(1);
    }

    @Test
    @DisplayName("B6.6 — отказ площадки на цене даёт пустой операнд, а не отказ чтения")
    void b6_6_anExchangeFailureOnThePriceYieldsAnEmptyOperandNotAReadFailure() {
        Series series = series();
        connector.answers(ConnectorStub.pricesOf(INSTRUMENT), 500, Feed.refusal("EXCHANGE_READ_FAILED"));
        Integer mark = AppLog.mark();

        Answer answer = post(features(series.instrument()), Bodies.featureRead(
                Bodies.array(Bodies.binding(FAST, series.atr(), WIDE)),
                Bodies.array(Bodies.binding(SHAPE, series.structure(), WIDE)),
                Boolean.TRUE));

        assertThat(answer.status()).isEqualTo(200);
        assertThat(answer.asObject().get("marketPriceData")).isNull();
        assertThat(answer.nested("latestIndicators")).containsKey(FAST);
        assertThat(answer.nested("structures")).containsKey(SHAPE);
        assertThat(AppLog.since(mark)).contains("Price operand unavailable for instrument");
    }

    @Test
    @DisplayName("B6.14 — отказ ДОСТУПА площадки на цене гасится тем же ходом")
    void b6_14_anAccessRefusalOnThePriceIsSilencedTheSameWay() {
        Series series = series();
        connector.answers(ConnectorStub.pricesOf(INSTRUMENT), 429, Feed.refusal("RATE_LIMITED"));

        Answer answer = post(features(series.instrument()), Bodies.featureRead(
                Bodies.array(Bodies.binding(FAST, series.atr(), WIDE)),
                Bodies.array(), Boolean.TRUE));

        // Исход обязан совпасть с рядовым отказом (B6.6): семантика входа
        // одна для всех, и недоступный операнд в контекст не попадает
        // независимо от того, чем именно площадка отказала.
        assertThat(answer.status()).isEqualTo(200);
        assertThat(answer.asObject().get("marketPriceData")).isNull();
        assertThat(answer.nested("latestIndicators")).containsKey(FAST);
    }

    @Test
    @DisplayName("B6.7 — клауз не передано — ответа о фазе нет")
    void b6_7_noClausesPassedMeansNoAnswerAboutThePhase() {
        Series series = series();

        Answer answer = post(features(series.instrument()), Bodies.featureRead(
                Bodies.array(Bodies.binding(FAST, series.atr(), WIDE)),
                Bodies.array(), Boolean.FALSE));

        assertThat(answer.status()).isEqualTo(200);
        assertThat(answer.asObject().get("marketPhase")).isNull();
    }

    @Test
    @DisplayName("B6.8 — недоступный вход клаузы даёт UNKNOWN")
    void b6_8_anUnavailableClauseInputYieldsUnknown() {
        Series series = series();

        Answer answer = post(features(series.instrument()), Bodies.featureReadWithPhase(
                Bodies.array(Bodies.binding(FAST, series.atr(), NARROW)),
                Bodies.array(Bodies.phaseRule("BULL_TREND", FAST, "GT", "0"))));

        assertThat(answer.status()).isEqualTo(200);
        assertThat(answer.nested("marketPhase").get("type")).isEqualTo("UNKNOWN");
        assertThat(rows.tableNames()).noneSatisfy(table ->
                assertThat(table.toLowerCase()).contains("phase"));
    }

    @Test
    @DisplayName("B6.9 — одиночное чтение значения индикатора")
    void b6_9_aSingleIndicatorValueRead() {
        Series series = series();

        Answer fresh = get(latest(series.instrument(), "indicator-values", series.atr(), WIDE));
        Answer stale = get(latest(series.instrument(), "indicator-values", series.atr(), NARROW));

        assertThat(fresh.status()).isEqualTo(200);
        assertThat(fresh.asObject().get("instrumentInternalId")).isEqualTo(series.instrument());
        assertThat(fresh.asObject().get("indicatorConfigInternalId")).isEqualTo(series.atr());
        assertThat(stale.status()).isEqualTo(204);
        assertThat(stale.body()).isEmpty();
    }

    @Test
    @DisplayName("B6.10 — одиночное чтение структуры")
    void b6_10_aSingleStructureRead() {
        Series series = series();

        Answer stale = get(latest(series.instrument(), "market-structures", series.structure(), NARROW));
        Answer fresh = get(latest(series.instrument(), "market-structures", series.structure(), WIDE));

        assertThat(stale.status()).isEqualTo(204);
        assertThat(fresh.status()).isEqualTo(200);
        assertThat(fresh.asObject().get("windowStartAt")).isNotNull();
        assertThat(fresh.asObject().get("windowEndAt")).isNotNull();
        assertThat(fresh.asObject().get("levels")).isNotNull();
    }

    @Test
    @DisplayName("B6.11 — неизвестная идентичность в привязке")
    void b6_11_anUnknownIdentityInABinding() {
        Series series = series();
        connector.forgetRequests();

        Answer answer = post(features(series.instrument()), Bodies.featureRead(
                Bodies.array(Bodies.binding(FAST, "MD-NO-SUCH", WIDE)),
                Bodies.array(), Boolean.FALSE));

        assertThat(answer.carriesErrorDto()).isTrue();
        assertThat(answer.errorCode()).isEqualTo("INVALID_REQUEST");
        assertThat(connector.count()).isEqualTo(0);
    }

    @Test
    @DisplayName("B6.12 — чтение фич состояния не меняет")
    void b6_12_readingFeaturesDoesNotChangeState() {
        Series series = series();
        connector.answers(ConnectorStub.pricesOf(INSTRUMENT), Feed.prices(INSTRUMENT, "50500"));
        Map<String, Long> before = rows.countsByTable();

        for (int index = 0; index < 10; index++) {
            post(features(series.instrument()), Bodies.featureRead(
                    Bodies.array(Bodies.binding(FAST, series.atr(), WIDE)),
                    Bodies.array(Bodies.binding(SHAPE, series.structure(), WIDE)),
                    Boolean.TRUE));
        }

        assertThat(rows.countsByTable()).isEqualTo(before);
    }

    @Test
    @DisplayName("B6.13 — пустой запрос фич законен")
    void b6_13_anEmptyFeatureRequestIsLawful() {
        Series series = series();

        Answer answer = post(features(series.instrument()), "{}");

        assertThat(answer.status()).isEqualTo(200);
        assertThat(answer.nested("latestIndicators")).isEmpty();
        assertThat(answer.nested("previousIndicators")).isEmpty();
        assertThat(answer.nested("structures")).isEmpty();
        assertThat(answer.asObject().get("marketPriceData")).isNull();
        assertThat(answer.asObject().get("marketPhase")).isNull();
    }

    /**
     * Ряд минутных баров у самого момента прогона плюс посчитанные по нему
     * идентичности индикаторов и структуры.
     *
     * <p><b>Ряд кладётся близко к моменту прогона, и это вход, а не
     * удобство:</b> расстояние между меткой значения и моментом чтения и
     * есть то, что срок читателя мерит.
     */
    private Series series() {
        String instrument = provisionInstruments(INSTRUMENT).getFirst();
        requireCandles(instrument, MINUTE, 5L);
        connector.answers(ConnectorStub.HISTORY_CANDLES,
                Feed.trendingCandles(barsAgo(MINUTE_MILLIS, 60), MINUTE_MILLIS, 59, 50000));
        tick(Tick.CANDLES);
        tick(Tick.CANDLES);
        String atr = requireIndicator("ATR", MINUTE, "{\"period\": 14}");
        String ema = requireIndicator("EMA", MINUTE, "{\"period\": 9}");
        String structure = requireStructure(MINUTE, Bodies.structureParams(50), null, null);
        tick(Tick.INDICATORS);
        tick(Tick.MARKET_STRUCTURES);
        connector.forgetRequests();
        return new Series(instrument, atr, ema, structure);
    }

    private static String features(String instrumentInternalId) {
        return INSTRUMENTS + "/" + instrumentInternalId + "/features";
    }

    private static String latest(String instrumentInternalId, String kind,
                                 String configInternalId, String tolerance) {
        return INSTRUMENTS + "/" + instrumentInternalId + "/" + kind
                + "/latest?configInternalId=" + configInternalId + "&tolerance=" + tolerance;
    }

    /** Идентичности, заведённые предусловием группы. */
    private record Series(String instrument, String atr, String ema, String structure) {
    }
}
