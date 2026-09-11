package com.example.auditstatistics;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.auditstatistics.domain.model.PairLagOperands;
import com.example.auditstatistics.domain.model.ReceptionPairMoments;
import com.example.auditstatistics.metrics.JournalReceptionMetrics;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Ряды экспорта операндов алерта на лаг пары
 * (docs/architecture/data-ownership.md §«Outbox и доставка»).
 *
 * <p><b>Что здесь проверяется по существу.</b> Предмет рядов — не число, а
 * РАЗЛИЧЕНИЕ ТРЁХ СОСТОЯНИЙ величины: измерено, измерено и равно нулю, не
 * измерено. Первые два обязаны приехать числом, третье — <b>отсутствием
 * ряда</b>: поданное числом, оно молча решает исход алерта, а нулём —
 * решает его в разрешающую сторону (docs/concept.md, П1).
 *
 * <p><b>Реестр здесь настоящий, а не простой.</b> Имена рядов читает
 * правило алерта манифеста, и соглашение об именах применяет именно
 * реестр Prometheus; на простом реестре проба мерила бы имена, которых
 * наблюдатель никогда не увидит.
 */
class ReceptionMetricsExportTest {

    private static final String CORE_TOPIC = "trading-core.facts";
    private static final String STRATEGIES_TOPIC = "strategies.facts";
    private static final Long HALF_WEEK_MS = 302_400_000L;

    private static final String AGE_SERIES = "audit_journal_reception_last_event_age_ms";
    private static final String THRESHOLD_SERIES = "audit_journal_reception_lag_alert_threshold_ms";
    private static final String UNCONSUMED_SERIES = "audit_journal_reception_unconsumed_records";

    private final PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    private final JournalReceptionMetrics metrics = new JournalReceptionMetrics(registry);

    @Test
    @DisplayName("Измеренная пара отдаёт три ряда, и все три — с меткой темы")
    void aMeasuredPairExportsThreeSeries() {
        metrics.replaceWith(List.of(fullyMeasured(CORE_TOPIC)));

        assertThat(sample(AGE_SERIES, CORE_TOPIC))
                .as("возраст — первый операнд предиката")
                .isNotNull();
        assertThat(sample(THRESHOLD_SERIES, CORE_TOPIC))
                .as("порог уезжает РЯДОМ, иначе правило сравнивало бы ряд с калиброванным числом")
                .isEqualTo(HALF_WEEK_MS.doubleValue());
        assertThat(sample(UNCONSUMED_SERIES, CORE_TOPIC))
                .as("остаток непринятого — второй конъюнкт предиката")
                .isEqualTo(17.0);
    }

    @Test
    @DisplayName("Срок темы не добыт — ряда порога нет вовсе, а возраст и остаток остаются")
    void anUnderivedThresholdRemovesOnlyItsOwnSeries() {
        metrics.replaceWith(List.of(new PairLagOperands(CORE_TOPIC, minutesAgo(1L), null, 17L)));

        assertThat(sample(THRESHOLD_SERIES, CORE_TOPIC))
                .as("пустой порог — не ноль: нулевой кричал бы всегда, а ряда нет — это третий исход предиката")
                .isNull();
        assertThat(sample(AGE_SERIES, CORE_TOPIC)).isNotNull();
        assertThat(sample(UNCONSUMED_SERIES, CORE_TOPIC)).isNotNull();
    }

    @Test
    @DisplayName("Остаток непринятого неизвестен — ряда нет; известный ноль — ряд есть")
    void anUnknownRemainderIsNotAZero() {
        metrics.replaceWith(List.of(new PairLagOperands(CORE_TOPIC, minutesAgo(1L), HALF_WEEK_MS, null)));
        assertThat(sample(UNCONSUMED_SERIES, CORE_TOPIC))
                .as("пустота означает «неизвестно» и алерт гасить не вправе")
                .isNull();

        metrics.replaceWith(List.of(new PairLagOperands(CORE_TOPIC, minutesAgo(1L), HALF_WEEK_MS, 0L)));
        assertThat(sample(UNCONSUMED_SERIES, CORE_TOPIC))
                .as("известный ноль означает «принимать нечего» и алерт гасит — он обязан ПРИЕХАТЬ")
                .isEqualTo(0.0);
    }

    @Test
    @DisplayName("Такт не измерял — рядов не остаётся ни одного")
    void forgettingRemovesEverySeries() {
        metrics.replaceWith(List.of(fullyMeasured(CORE_TOPIC), fullyMeasured(STRATEGIES_TOPIC)));

        metrics.forget();

        assertThat(registry.scrape())
                .as("ряд от прошлого такта утверждал бы измеренное там, где ничего не измерялось")
                .doesNotContain(AGE_SERIES)
                .doesNotContain(THRESHOLD_SERIES)
                .doesNotContain(UNCONSUMED_SERIES);
    }

    @Test
    @DisplayName("Состав рядов заменяется целиком: ушедшая пара уносит свои ряды")
    void aPairGoneFromTheTickTakesItsSeriesAway() {
        metrics.replaceWith(List.of(fullyMeasured(CORE_TOPIC), fullyMeasured(STRATEGIES_TOPIC)));

        metrics.replaceWith(List.of(fullyMeasured(CORE_TOPIC)));

        assertThat(sample(AGE_SERIES, STRATEGIES_TOPIC))
                .as("иначе снятая тема продолжала бы отвечать возрастом, который никто не мерит")
                .isNull();
        assertThat(sample(AGE_SERIES, CORE_TOPIC)).isNotNull();
    }

    @Test
    @DisplayName("Порог едет за откалиброванным сроком: значение ряда переписывается, а не остаётся")
    void aRecalibratedThresholdOverwritesTheRow() {
        metrics.replaceWith(List.of(new PairLagOperands(CORE_TOPIC, minutesAgo(1L), HALF_WEEK_MS, 17L)));

        metrics.replaceWith(List.of(new PairLagOperands(CORE_TOPIC, minutesAgo(1L), HALF_WEEK_MS / 2, 17L)));

        assertThat(sample(THRESHOLD_SERIES, CORE_TOPIC))
                .as("срок, откалиброванный владельцем темы вниз, обязан опустить и порог — иначе алерт "
                        + "молчал бы до самой потери")
                .isEqualTo(HALF_WEEK_MS.doubleValue() / 2);
    }

    @Test
    @DisplayName("Возраст считается в момент съёма и растёт от durable-момента")
    void theAgeIsMeasuredAtScrapeTime() {
        metrics.replaceWith(List.of(new PairLagOperands(CORE_TOPIC, minutesAgo(5L), HALF_WEEK_MS, 17L)));

        assertThat(sample(AGE_SERIES, CORE_TOPIC))
                .as("замороженный тактом, возраст перестал бы расти вместе с умершим тиком")
                .isGreaterThanOrEqualTo(300_000.0);
    }

    @Test
    @DisplayName("Момент из будущего не даёт отрицательного возраста")
    void aFutureMomentDoesNotYieldANegativeAge() {
        OffsetDateTime ahead = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(5L);

        metrics.replaceWith(List.of(new PairLagOperands(CORE_TOPIC, ahead, HALF_WEEK_MS, 17L)));

        assertThat(sample(AGE_SERIES, CORE_TOPIC))
                .as("отрицательный возраст заведомо ниже порога — то есть тихое «всё в порядке»")
                .isEqualTo(0.0);
    }

    @Test
    @DisplayName("До первого приёма основание возраста — момент наблюдения, а не пустота")
    void beforeTheFirstAcceptanceTheAgeCountsFromObservation() {
        OffsetDateTime observedSince = minutesAgo(3L);
        ReceptionPairMoments fresh = new ReceptionPairMoments(CORE_TOPIC, null, observedSince);

        assertThat(fresh.lastEventMoment())
                .as("иначе у здоровой пары без входов ряда возраста не было бы вовсе — и второе правило "
                        + "поднимало бы алерт на паре, с которой всё в порядке")
                .isEqualTo(observedSince);
    }

    @Test
    @DisplayName("Принятое событие есть — основание возраста его момент, а не момент наблюдения")
    void afterTheFirstAcceptanceTheAgeCountsFromTheEvent() {
        OffsetDateTime accepted = minutesAgo(1L);
        ReceptionPairMoments used = new ReceptionPairMoments(CORE_TOPIC, accepted, minutesAgo(9L));

        assertThat(used.lastEventMoment()).isEqualTo(accepted);
    }

    private PairLagOperands fullyMeasured(String topic) {
        return new PairLagOperands(topic, minutesAgo(1L), HALF_WEEK_MS, 17L);
    }

    private OffsetDateTime minutesAgo(Long minutes) {
        return OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(minutes);
    }

    /** Значение ряда по метке темы; пусто — ряда в экспозиции нет. */
    private Double sample(String series, String topic) {
        for (String line : registry.scrape().split("\n")) {
            if (line.startsWith(series + "{") && line.contains("topic=\"" + topic + "\"")) {
                return Double.valueOf(line.substring(line.lastIndexOf(' ') + 1).trim());
            }
        }
        return null;
    }
}
