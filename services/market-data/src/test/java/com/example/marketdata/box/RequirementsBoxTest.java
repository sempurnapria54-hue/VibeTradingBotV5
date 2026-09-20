package com.example.marketdata.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Требования потребителя — группа {@code B1} документа
 * `.claude/tests/cases/market-data.md`.
 *
 * <p><b>Требование — синхронная команда, а не событие и не
 * конфигурация</b> (docs/architecture/market-data-collection.md §«Как
 * потребность доходит до сбора»): собирается то, что кому-то нужно, и
 * называет это потребитель, а не настройка сервиса.
 *
 * <p><b>Все три команды идемпотентны ПО СОДЕРЖАНИЮ.</b> Отсюда и форма
 * клеток: вторая подача того же требования проверяется не ответом, а
 * числом строк — ответ у неё тот же и при дубле.
 */
class RequirementsBoxTest extends SharedMarketDataBox {

    private static final String CANDLES = REQUIREMENTS + "/candles";

    private static final String INDICATORS = REQUIREMENTS + "/indicators";

    private static final String STRUCTURES = REQUIREMENTS + "/market-structures";

    private static final String ATR_PARAMS = "{\"period\": 14, \"warmup\": 20}";

    @Test
    @DisplayName("B1.1 — требование свечей заводит единицу сбора")
    void b1_1_aCandleRequirementCreatesACollectionUnit() {
        String instrument = provisionInstruments(INSTRUMENT).getFirst();

        Answer answer = post(CANDLES, Bodies.candleRequirement(instrument, HOUR, 500L));

        assertThat(answer.status()).isEqualTo(200);
        Map<String, Object> group = answer.asObject();
        assertThat(group.get("status")).isEqualTo("CREATED");
        assertThat(group.get("internalId")).isEqualTo(instrument + ":" + HOUR);
        assertThat(group.get("count")).isEqualTo(0);
        assertThat(rows.count("candle_groups")).isEqualTo(1L);
        Map<String, Object> stored = rows.all("candle_groups").getFirst();
        Long horizon = ((Number) stored.get("planned_first_utc_millis")).longValue();
        assertThat(System.currentTimeMillis() - horizon)
                .isBetween(500 * HOUR_MILLIS - 60_000L, 500 * HOUR_MILLIS + 60_000L);
        assertThat(rows.row("instruments", "external_id", INSTRUMENT).get("status")).isEqualTo("SYNC");
        assertThat(connector.count()).isEqualTo(0);
    }

    @Test
    @DisplayName("B1.2 — повтор того же требования второй единицы не создаёт")
    void b1_2_repeatingTheSameRequirementCreatesNoSecondUnit() {
        String instrument = provisionInstruments(INSTRUMENT).getFirst();
        Answer first = post(CANDLES, Bodies.candleRequirement(instrument, HOUR, 500L));
        Long horizonAfterFirst = horizonOf();

        Answer second = post(CANDLES, Bodies.candleRequirement(instrument, HOUR, 500L));

        assertThat(second.status()).isEqualTo(200);
        assertThat(second.asObject().get("internalId")).isEqualTo(first.asObject().get("internalId"));
        assertThat(rows.count("candle_groups")).isEqualTo(1L);
        assertThat(horizonOf()).isEqualTo(horizonAfterFirst);
    }

    @Test
    @DisplayName("B1.3 — требование мельче стоящего горизонта не сужает")
    void b1_3_aShallowerRequirementDoesNotNarrowTheStandingHorizon() {
        String instrument = provisionInstruments(INSTRUMENT).getFirst();
        requireCandles(instrument, HOUR, 500L);
        rows.put("update candle_groups set status = 'ACTIVE'");
        Long standing = horizonOf();

        Answer answer = post(CANDLES, Bodies.candleRequirement(instrument, HOUR, 50L));

        assertThat(answer.status()).isEqualTo(200);
        assertThat(horizonOf()).isEqualTo(standing);
        assertThat(statusOf()).isEqualTo("ACTIVE");
        assertThat(rows.count("candles")).isEqualTo(0L);
    }

    @Test
    @DisplayName("B1.4 — требование глубже стоящего возвращает готовую группу к бэкфиллу")
    void b1_4_aDeeperRequirementReturnsAReadyUnitToBackfill() {
        String instrument = provisionInstruments(INSTRUMENT).getFirst();
        requireCandles(instrument, HOUR, 50L);
        rows.put("update candle_groups set status = 'ACTIVE'");
        Long shallow = horizonOf();

        Answer answer = post(CANDLES, Bodies.candleRequirement(instrument, HOUR, 500L));

        assertThat(answer.status()).isEqualTo(200);
        assertThat(horizonOf()).isLessThan(shallow);
        assertThat(statusOf()).isEqualTo("BACKFILL");
    }

    @Test
    @DisplayName("B1.5 — то же ребро срабатывает из SYNC, CHECK и REPAIR")
    void b1_5_theSameEdgeFiresFromSyncCheckAndRepair() {
        String instrument = provisionInstruments(INSTRUMENT).getFirst();
        requireCandles(instrument, "ONE_MINUTE", 50L);
        requireCandles(instrument, "FIVE_MINUTES", 50L);
        requireCandles(instrument, "FIFTEEN_MINUTES", 50L);
        rows.put("update candle_groups set status = 'SYNC' where timeframe = 'ONE_MINUTE'");
        rows.put("update candle_groups set status = 'CHECK' where timeframe = 'FIVE_MINUTES'");
        rows.put("update candle_groups set status = 'REPAIR' where timeframe = 'FIFTEEN_MINUTES'");

        post(CANDLES, Bodies.candleRequirement(instrument, "ONE_MINUTE", 5000L));
        post(CANDLES, Bodies.candleRequirement(instrument, "FIVE_MINUTES", 5000L));
        post(CANDLES, Bodies.candleRequirement(instrument, "FIFTEEN_MINUTES", 5000L));

        assertThat(rows.all("candle_groups")).allSatisfy(group ->
                assertThat(group.get("status")).isEqualTo("BACKFILL"));
    }

    @Test
    @DisplayName("B1.6 — терминальная группа требованием не оживляется")
    void b1_6_aTerminalUnitIsNotRevivedByARequirement() {
        String instrument = provisionInstruments(INSTRUMENT).getFirst();
        requireCandles(instrument, "ONE_MINUTE", 50L);
        requireCandles(instrument, "FIVE_MINUTES", 50L);
        // ERROR пишет цикл докачки, DELETED не пишет НИ ОДНА тропа сервиса
        // (находка F-7): операции удаления единицы сбора поверхность не несёт.
        rows.put("update candle_groups set status = 'ERROR' where timeframe = 'ONE_MINUTE'");
        rows.put("update candle_groups set status = 'DELETED' where timeframe = 'FIVE_MINUTES'");
        Long shallowMinute = horizonOf("ONE_MINUTE");
        Long shallowFive = horizonOf("FIVE_MINUTES");

        Answer minute = post(CANDLES, Bodies.candleRequirement(instrument, "ONE_MINUTE", 5000L));
        Answer five = post(CANDLES, Bodies.candleRequirement(instrument, "FIVE_MINUTES", 5000L));

        assertThat(minute.status()).isEqualTo(200);
        assertThat(five.status()).isEqualTo(200);
        assertThat(horizonOf("ONE_MINUTE")).isLessThan(shallowMinute);
        assertThat(horizonOf("FIVE_MINUTES")).isLessThan(shallowFive);
        assertThat(statusOf("ONE_MINUTE")).isEqualTo("ERROR");
        assertThat(statusOf("FIVE_MINUTES")).isEqualTo("DELETED");

        connector.answers(ConnectorStub.HISTORY_CANDLES, Feed.empty());
        connector.answers(ConnectorStub.CANDLES, Feed.empty());
        tick(Tick.CANDLES);

        assertThat(statusOf("ONE_MINUTE")).isEqualTo("ERROR");
        assertThat(statusOf("FIVE_MINUTES")).isEqualTo("DELETED");
    }

    @Test
    @DisplayName("B1.7 — требование без глубины означает всю доступную историю")
    void b1_7_aRequirementWithoutDepthMeansTheWholeAvailableHistory() {
        String instrument = provisionInstruments(INSTRUMENT).getFirst();

        Answer answer = post(CANDLES, Bodies.candleRequirement(instrument, HOUR));

        assertThat(answer.status()).isEqualTo(200);
        assertThat(answer.asObject().get("plannedFirstUtcMillis")).isNull();
        assertThat(rows.all("candle_groups").getFirst().get("planned_first_utc_millis")).isNull();
    }

    @Test
    @DisplayName("B1.8 — требование по несуществующему инструменту")
    void b1_8_aRequirementForAnUnknownInstrument() {
        Answer answer = post(CANDLES, Bodies.candleRequirement("MD-NO-SUCH", HOUR, 500L));

        assertThat(answer.carriesErrorDto()).isTrue();
        assertThat(answer.errorCode()).isEqualTo("INVALID_REQUEST");
        assertThat(rows.count("candle_groups")).isEqualTo(0L);
        assertThat(rows.count("instruments")).isEqualTo(0L);
    }

    @Test
    @DisplayName("B1.9 — требование индикатора идемпотентно по канонической форме параметров")
    void b1_9_anIndicatorRequirementIsIdempotentByTheCanonicalFormOfParameters() {
        Answer first = post(INDICATORS,
                Bodies.indicatorRequirement("ATR", HOUR, "{\"period\": 14, \"warmup\": 20}"));
        Answer second = post(INDICATORS,
                Bodies.indicatorRequirement("ATR", HOUR, "{\"warmup\": 20, \"period\": 14}"));

        assertThat(first.status()).isEqualTo(200);
        assertThat(second.status()).isEqualTo(200);
        assertThat(second.asObject().get("internalId")).isEqualTo(first.asObject().get("internalId"));
        assertThat(rows.count("indicator_configs")).isEqualTo(1L);
    }

    /**
     * Ожидание взято из дома: точка требования объявляет {@code 400} на
     * «параметры не разбираются под заявленный тип», а подтип параметров
     * восстанавливается по типу строки-владельца
     * (docs/rules/persistence-representation.md).
     *
     * <p>Сегодня поля чужого типа принимаются МОЛЧА: Boot гасит
     * {@code FAIL_ON_UNKNOWN_PROPERTIES}, и {@code convertValue} строит
     * параметры с пустым периодом вместо отказа — идентичность заводится,
     * а расчёт по ней падать будет у другого тика. Находка {@code F-10};
     * долг — `.claude/work/backlog.md` §«Параметры чужого типа требование
     * индикатора `market-data` принимает молча».
     */
    @Test
    @Tag("debt")
    @DisplayName("B1.10 — параметры, не разбирающиеся под заявленный тип")
    void b1_10_parametersThatDoNotParseUnderTheDeclaredType() {
        Answer answer = post(INDICATORS,
                Bodies.indicatorRequirement("ATR", HOUR, "{\"fastPeriod\": 12}"));

        assertThat(answer.carriesErrorDto()).isTrue();
        assertThat(answer.errorCode()).isEqualTo("INVALID_REQUEST");
        assertThat(rows.count("indicator_configs")).isEqualTo(0L);
    }

    @Test
    @DisplayName("B1.11 — идентичность структуры включает идентичности её входов")
    void b1_11_aStructureIdentityIncludesTheIdentitiesOfItsInputs() {
        String firstAtr = requireIndicator("ATR", HOUR, "{\"period\": 14}");
        String secondAtr = requireIndicator("ATR", HOUR, "{\"period\": 21}");

        Answer first = post(STRUCTURES,
                Bodies.structureRequirement(HOUR, Bodies.structureParams(50), null, firstAtr));
        Answer second = post(STRUCTURES,
                Bodies.structureRequirement(HOUR, Bodies.structureParams(50), null, secondAtr));

        assertThat(first.status()).isEqualTo(200);
        assertThat(second.status()).isEqualTo(200);
        assertThat(second.asObject().get("internalId")).isNotEqualTo(first.asObject().get("internalId"));
        assertThat(rows.count("market_structure_configs")).isEqualTo(2L);
    }

    @Test
    @DisplayName("B1.12 — требование структуры с несуществующим входом")
    void b1_12_aStructureRequirementWithAnUnknownInput() {
        Answer answer = post(STRUCTURES,
                Bodies.structureRequirement(HOUR, Bodies.structureParams(50), "MD-NO-SUCH", null));

        assertThat(answer.carriesErrorDto()).isTrue();
        assertThat(answer.errorCode()).isEqualTo("INVALID_REQUEST");
        assertThat(rows.count("market_structure_configs")).isEqualTo(0L);
    }

    @Test
    @DisplayName("B1.13 — требование вычисления ряда свечей не влечёт")
    void b1_13_aComputationRequirementDoesNotEntailACandleSeries() {
        provisionInstruments(INSTRUMENT);

        Answer answer = post(INDICATORS, Bodies.indicatorRequirement("ATR", HOUR, ATR_PARAMS));
        tick(Tick.INDICATORS);

        assertThat(answer.status()).isEqualTo(200);
        assertThat(rows.count("indicator_configs")).isEqualTo(1L);
        assertThat(rows.count("candle_groups")).isEqualTo(0L);
        assertThat(rows.count("indicator_values")).isEqualTo(0L);
    }

    /**
     * Ожидание формы тела взято из дома: отказ, произведённый
     * контейнером, есть тот же контракт, что и отказ приложения
     * (docs/rules/error-handling-policy.md §«Отказ, произведённый
     * контейнером, — тот же контракт»). Сегодня контейнер отдаёт своё
     * тело; долг — `.claude/work/backlog.md` §«Единый error-DTO у
     * поверхностей соседних сервисов». Отсутствие операции отзыва при
     * этом ЗЕЛЕНО: границу кейс и предъявляет.
     */
    @Test
    @Tag("debt")
    @DisplayName("B1.14 — отзыва требования у поверхности нет")
    void b1_14_theSurfaceCarriesNoRequirementWithdrawal() {
        String instrument = provisionInstruments(INSTRUMENT).getFirst();
        requireCandles(instrument, HOUR, 500L);
        requireIndicator("ATR", HOUR, ATR_PARAMS);
        requireStructure(HOUR, Bodies.structureParams(50), null, null);

        Answer candles = delete(CANDLES);
        Answer indicators = delete(INDICATORS);
        Answer structures = delete(STRUCTURES);

        assertThat(candles.status()).isEqualTo(405);
        assertThat(indicators.status()).isEqualTo(405);
        assertThat(structures.status()).isEqualTo(405);
        assertThat(candles.carriesErrorDto()).isTrue();
        assertThat(indicators.carriesErrorDto()).isTrue();
        assertThat(structures.carriesErrorDto()).isTrue();
        assertThat(rows.count("candle_groups")).isEqualTo(1L);
        assertThat(rows.count("indicator_configs")).isEqualTo(1L);
        assertThat(rows.count("market_structure_configs")).isEqualTo(1L);
    }

    private Long horizonOf() {
        return ((Number) rows.all("candle_groups").getFirst().get("planned_first_utc_millis")).longValue();
    }

    private Long horizonOf(String timeframe) {
        return ((Number) rows.row("candle_groups", "timeframe", timeframe)
                .get("planned_first_utc_millis")).longValue();
    }

    private String statusOf() {
        return String.valueOf(rows.all("candle_groups").getFirst().get("status"));
    }

    private String statusOf(String timeframe) {
        return String.valueOf(rows.row("candle_groups", "timeframe", timeframe).get("status"));
    }
}
