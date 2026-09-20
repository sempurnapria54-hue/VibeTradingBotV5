package com.example.testsupport;

import static java.util.Objects.isNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Ряды экспорта операндов алерта на лаг пары — группа `U14` и клетки
 * `U15.7`, `U16.8` документа `.claude/tests/cases/durable-reception.md`.
 *
 * <p><b>Ожидание объявлено один раз и прогоняется каждым деревом своей
 * копии</b> (`.claude/rules/carrier-levels.md`): класс рядов лежит двумя
 * экземплярами, и объявленное их расхождение — только ИМЕНА рядов
 * (у каждого имени метка своего сервиса).
 *
 * <p><b>Наблюдение идёт выдачей реестра, а не полями класса:</b> предмет
 * ряда есть то, что увидит наблюдатель, снимая экспозицию.
 *
 * <p><b>Возраст читает часы процесса</b>, и ожидание у его клеток
 * выражено границей и допуском, а не точным значением.
 */
public abstract class ReceptionMetricsContract {

    /** Тема пары и её соседка: состав рядов у них законно разный. */
    protected static final String TOPIC = "trading-core.facts";
    protected static final String NEIGHBOUR_TOPIC = "strategies.facts";

    /** Половина недели в миллисекундах — порог алерта одной пары. */
    protected static final Long HALF_WEEK_MS = 302_400_000L;

    private static final String TOPIC_TAG = "topic";

    private MeterRegistry registry;
    private Series series;

    /** Операнды одной пары, как их измерил такт тика. */
    public record LagOperands(String topic, OffsetDateTime lastEventMoment,
                              Long lagAlertThresholdMs, Long unconsumedRecords) { }

    /** Ряды экспорта своего дерева. */
    public interface Series {

        void replaceWith(List<LagOperands> operands);

        void forget();
    }

    // --- порты к своей копии ---------------------------------------------

    /** Ряды своего дерева поверх переданного реестра. */
    protected abstract Series series(MeterRegistry registry);

    /** Имя ряда возраста своего сервиса. */
    protected abstract String ageSeriesName();

    /** Имя ряда порога своего сервиса. */
    protected abstract String thresholdSeriesName();

    /** Имя ряда остатка своего сервиса. */
    protected abstract String unconsumedSeriesName();

    /** Класс рядов своего дерева. */
    protected abstract Class<?> receptionMetricsType();

    @BeforeEach
    void freshRegistry() {
        registry = new SimpleMeterRegistry();
        series = series(registry);
    }

    // --- U14: состав, забывание, возраст в момент съёма --------------------

    @Test
    @DisplayName("U14.1 — измеренная пара отдаёт три ряда, и у каждого метка своей темы")
    void u14_1_aFullyMeasuredPairExportsThreeSeries() {
        series.replaceWith(List.of(fullyMeasured(TOPIC)));

        assertThat(value(ageSeriesName(), TOPIC)).isNotNull();
        assertThat(value(thresholdSeriesName(), TOPIC)).isEqualTo(HALF_WEEK_MS.doubleValue());
        assertThat(value(unconsumedSeriesName(), TOPIC)).isEqualTo(17.0);
    }

    @Test
    @DisplayName("U14.2 — порог не добыт: рядов два, нуля на его месте не появляется")
    void u14_2_anUnderivedThresholdRemovesOnlyItsOwnSeries() {
        series.replaceWith(List.of(new LagOperands(TOPIC, minutesAgo(1L), null, 17L)));

        assertThat(value(thresholdSeriesName(), TOPIC))
                .as("нулевой порог кричал бы всегда, а `NaN` гасил бы алерт тише всех")
                .isNull();
        assertThat(value(ageSeriesName(), TOPIC)).isNotNull();
        assertThat(value(unconsumedSeriesName(), TOPIC)).isNotNull();
    }

    @Test
    @DisplayName("U14.3 — остаток не отдан клиентом: рядов два")
    void u14_3_anUnknownRemainderRemovesOnlyItsOwnSeries() {
        series.replaceWith(List.of(new LagOperands(TOPIC, minutesAgo(1L), HALF_WEEK_MS, null)));

        assertThat(value(unconsumedSeriesName(), TOPIC)).isNull();
        assertThat(value(ageSeriesName(), TOPIC)).isNotNull();
        assertThat(value(thresholdSeriesName(), TOPIC)).isNotNull();
    }

    @Test
    @DisplayName("U14.4 — остаток равен нулю: ряд ЕСТЬ со значением 0")
    void u14_4_aKnownZeroKeepsItsSeries() {
        series.replaceWith(List.of(new LagOperands(TOPIC, minutesAgo(1L), HALF_WEEK_MS, 0L)));

        assertThat(value(unconsumedSeriesName(), TOPIC))
                .as("известный ноль от неизвестности отличается: он гасит алерт законно")
                .isEqualTo(0.0);
    }

    @Test
    @DisplayName("U14.5 — перечень операндов пуст: рядов не остаётся ни одного")
    void u14_5_anEmptyTickLeavesNoSeries() {
        series.replaceWith(List.of(fullyMeasured(TOPIC)));

        series.replaceWith(List.of());

        assertThat(exportedSeries()).isEmpty();
    }

    @Test
    @DisplayName("U14.6 — снятие рядов: исход тот же, что у пустого такта")
    void u14_6_forgettingIsTheSameAsAnEmptyTick() {
        series.replaceWith(List.of(fullyMeasured(TOPIC), fullyMeasured(NEIGHBOUR_TOPIC)));

        series.forget();

        assertThat(exportedSeries())
                .as("ряд от прошлого такта утверждал бы измеренное там, где ничего не измерялось")
                .isEmpty();
    }

    @Test
    @DisplayName("U14.7 — ушедшая пара уносит свои ряды, а не остаётся прежней")
    void u14_7_aPairGoneFromTheTickTakesItsSeriesAway() {
        series.replaceWith(List.of(fullyMeasured(TOPIC), fullyMeasured(NEIGHBOUR_TOPIC)));

        series.replaceWith(List.of(fullyMeasured(TOPIC)));

        assertThat(value(ageSeriesName(), NEIGHBOUR_TOPIC))
                .as("иначе снятая тема отвечала бы возрастом, который никто не мерит")
                .isNull();
        assertThat(value(ageSeriesName(), TOPIC)).isNotNull();
    }

    @Test
    @DisplayName("U14.8 — откалиброванный порог переписывает значение ряда")
    void u14_8_aRecalibratedThresholdOverwritesTheRow() {
        series.replaceWith(List.of(new LagOperands(TOPIC, minutesAgo(1L), HALF_WEEK_MS, 17L)));

        series.replaceWith(List.of(new LagOperands(TOPIC, minutesAgo(1L), HALF_WEEK_MS / 2, 17L)));

        assertThat(value(thresholdSeriesName(), TOPIC))
                .as("срок, откалиброванный владельцем темы вниз, обязан опустить и порог")
                .isEqualTo(HALF_WEEK_MS.doubleValue() / 2);
    }

    @Test
    @DisplayName("U14.9 — момент на минуту в прошлом: возраст не меньше 60000")
    void u14_9_theAgeGrowsFromTheDurableMoment() {
        series.replaceWith(List.of(new LagOperands(TOPIC, minutesAgo(1L), HALF_WEEK_MS, 17L)));

        assertThat(value(ageSeriesName(), TOPIC)).isGreaterThanOrEqualTo(60_000.0);
    }

    @Test
    @DisplayName("U14.10 — тот же ряд снят дважды: второе значение больше первого")
    void u14_10_theAgeIsMeasuredAtScrapeTime() {
        series.replaceWith(List.of(new LagOperands(TOPIC, minutesAgo(5L), HALF_WEEK_MS, 17L)));
        Double first = value(ageSeriesName(), TOPIC);

        await().atMost(Duration.ofSeconds(2L))
                .alias("возраст растёт между съёмами сам")
                .until(() -> value(ageSeriesName(), TOPIC) > first);

        assertThat(value(ageSeriesName(), TOPIC))
                .as("замороженный тактом, возраст перестал бы расти вместе с умершим тиком")
                .isGreaterThan(first);
    }

    @Test
    @DisplayName("U14.12 — у одной пары порог есть, у другой нет: составы рядов законно разные")
    void u14_12_theSeriesCompositionMayDifferBetweenPairs() {
        series.replaceWith(List.of(
                fullyMeasured(TOPIC),
                new LagOperands(NEIGHBOUR_TOPIC, minutesAgo(1L), null, 3L)));

        assertThat(value(ageSeriesName(), TOPIC)).isNotNull();
        assertThat(value(ageSeriesName(), NEIGHBOUR_TOPIC)).isNotNull();
        assertThat(value(thresholdSeriesName(), TOPIC)).isNotNull();
        assertThat(value(thresholdSeriesName(), NEIGHBOUR_TOPIC))
                .as("пара с возрастом и без порога есть третий исход предиката — «измеритель не мерит»")
                .isNull();
    }

    @Test
    @DisplayName("U14.13 — ряды несут ТОЛЬКО метку темы: метки группы на них нет")
    void u14_13_theOnlyTagIsTheTopic() {
        series.replaceWith(List.of(fullyMeasured(TOPIC)));

        Gauge gauge = registry.find(ageSeriesName()).tag(TOPIC_TAG, TOPIC).gauge();

        assertThat(gauge).isNotNull();
        assertThat(gauge.getId().getTags())
                .as("группа у сервиса одна, и метка её ничего не различала бы")
                .hasSize(1);
    }

    @Test
    @DisplayName("U14.14 — имена трёх рядов несут метку своего сервиса")
    void u14_14_theSeriesNamesCarryTheServiceMarker() {
        series.replaceWith(List.of(fullyMeasured(TOPIC)));

        assertThat(exportedSeries())
                .containsExactlyInAnyOrder(ageSeriesName(), thresholdSeriesName(), unconsumedSeriesName());
        assertThat(List.of(ageSeriesName(), thresholdSeriesName(), unconsumedSeriesName()))
                .as("правило алерта сравнивает два экспортируемых ряда, и находит их по имени")
                .allMatch(name -> name.contains("reception"))
                .doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("U14.15 — durable-следа ряды не оставляют: в базу не пишется ничего")
    void u14_15_theSeriesLeaveNoDurableTrace() {
        assertThat(constructorParameterTypes())
                .as("хранимая копия срока и остатка вернула бы второй носитель числа")
                .allMatch(name -> name.contains("MeterRegistry"));
    }

    @Test
    @DisplayName("U14.16 — у копий рядов различаются ТОЛЬКО имена: поверхность и состав одни")
    void u14_16_theMetricsCopiesDifferOnlyByName() {
        series.replaceWith(List.of(fullyMeasured(TOPIC)));

        assertThat(publicMethodNames(receptionMetricsType()))
                .as("поверхность у копий одна: замена состава такта и его снятие")
                .containsExactlyInAnyOrder("replaceWith", "forget");
        assertThat(exportedSeries()).hasSize(3);
        assertThat(value(thresholdSeriesName(), TOPIC)).isEqualTo(HALF_WEEK_MS.doubleValue());
        assertThat(value(unconsumedSeriesName(), TOPIC)).isEqualTo(17.0);
    }

    // --- U15.7, U16.8 -----------------------------------------------------

    @Test
    @DisplayName("U15.7 — расходятся только ИМЕНА рядов, и метка сервиса у них своя")
    void u15_7_onlyTheSeriesNamesDifferBetweenCopies() {
        assertThat(ageSeriesName()).endsWith("reception.last.event.age.ms");
        assertThat(thresholdSeriesName()).endsWith("reception.lag.alert.threshold.ms");
        assertThat(unconsumedSeriesName()).endsWith("reception.unconsumed.records");
    }

    @Test
    @DisplayName("U16.8 — ни порог, ни остаток строкой состояния не хранятся")
    void u16_8_neitherThresholdNorRemainderIsPersisted() {
        assertThat(receptionMetricsType().getDeclaredFields())
                .as("три поля рядов и ни одного писателя в базу")
                .hasSize(3);
        assertThat(constructorParameterTypes()).hasSize(1);
    }

    // --- оснастка ---------------------------------------------------------

    private LagOperands fullyMeasured(String topic) {
        return new LagOperands(topic, minutesAgo(1L), HALF_WEEK_MS, 17L);
    }

    private static OffsetDateTime minutesAgo(Long minutes) {
        return OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(minutes);
    }

    /** Значение ряда по метке темы; пусто — ряда в выдаче реестра нет. */
    private Double value(String series, String topic) {
        Gauge gauge = registry.find(series).tag(TOPIC_TAG, topic).gauge();
        return isNull(gauge) ? null : gauge.value();
    }

    /** Имена рядов, которые реестр отдаёт наблюдателю сейчас. */
    private List<String> exportedSeries() {
        List<String> names = new ArrayList<>();
        registry.getMeters().forEach(meter -> names.add(meter.getId().getName()));
        return names;
    }

    /** Имена публичных методов класса — профиль его поверхности. */
    private static List<String> publicMethodNames(Class<?> type) {
        List<String> names = new ArrayList<>();
        for (Method method : type.getDeclaredMethods()) {
            if (Modifier.isPublic(method.getModifiers()) && !method.isSynthetic()) {
                names.add(method.getName());
            }
        }
        return names;
    }

    private List<String> constructorParameterTypes() {
        List<String> names = new ArrayList<>();
        for (Constructor<?> constructor : receptionMetricsType().getDeclaredConstructors()) {
            for (Class<?> parameter : constructor.getParameterTypes()) {
                names.add(parameter.getName());
            }
        }
        return names;
    }
}
