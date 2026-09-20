package com.example.audit.box;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Группа {@code B3} документа кейсов — та её часть, которой штатное
 * положение осей конфигурации является входом
 * (.claude/tests/cases/audit.md §«B3 — Тик состояния приёма: состав пар,
 * живость, ряды»).
 *
 * <p><b>Тик подаётся ПРЯМЫМ ВЫЗОВОМ метода джобы, и это объявленный вход,
 * а не подмена.</b> Ручного фасада у джоб сервиса нет намеренно:
 * поверхность объявлена только читающей
 * (docs/components/ReceptionStateJob.md §«Форма — джоба без ручного
 * фасада, и это объявлено»), и решение 6 называет эту точку единственной,
 * где кейс уровня 1 касается бина.
 *
 * <p><b>Пауза между наблюдениями выражена РОСТОМ наблюдаемой величины, а
 * не сном потока.</b> Возраст последнего принятого события считается в
 * момент съёма, поэтому между тактами он растёт сам — и ожидание его
 * роста есть условие, а не срок. Сон платил бы временем всегда и
 * детерминированным не был бы (решение 6).
 *
 * <p><b>Клетки, сдвигающие оси конфигурации либо отнимающие у подписки
 * назначение, живут своими классами:</b> состав подписки, выключатель
 * тика и допустимый возраст строки суть ВХОДЫ таких клеток, а контекст
 * читает их при подъёме.
 */
class ReceptionStateTickBoxTest extends SharedAuditBox {

    /** Тема первого производителя: в неё ходит большинство клеток. */
    private static final String CORE = AuditSubstrate.CORE_TOPIC;

    /** Тема второго производителя: ею наблюдается раздельность пар. */
    private static final String STRATEGY = AuditSubstrate.STRATEGY_TOPIC;

    /** Возраст события, которым клетки двигают основание ряда. */
    private static final Duration EVENT_AGE = Duration.ofMinutes(5);

    /** Потолок возраста, ниже которого он взят от момента наблюдения. */
    private static final Long OBSERVATION_AGE_CEILING = Duration.ofMinutes(1).toMillis();

    /** Пол возраста, выше которого он взят от момента происшествия. */
    private static final Long EVENT_AGE_FLOOR = Duration.ofMinutes(4).toMillis();

    /** Рост возраста, которым выражена пауза между наблюдениями. */
    private static final Long OBSERVED_GROWTH = 300L;

    /** Сколько тактов подряд наблюдает клетка о моменте обновления. */
    private static final Integer TICKS_OBSERVED = 3;

    /** Пути ручного триггера джоб по конвенции соседних сервисов. */
    private static final List<String> TRIGGER_PATHS = List.of(
            "/api/v1/audit/jobs",
            "/api/v1/audit/jobs/reception-state",
            "/api/v1/audit/jobs/journal-cleanup");

    /** Методы, которыми перебираются пути ручного триггера. */
    private static final List<String> METHODS = List.of("GET", "POST", "PUT", "PATCH", "DELETE");

    /** Описание поверхности: им читается состав отображённых маршрутов. */
    private static final String SURFACE_DESCRIPTION = "/v3/api-docs";

    /** Проба живости — первая открытая точка актуатора. */
    private static final String LIVENESS_PROBE = "/actuator/health";

    /** Съём рядов — вторая открытая точка актуатора. */
    private static final String METRICS_SCRAPE = "/actuator/prometheus";

    @Test
    @DisplayName("B3.1 — Первый такт заводит строку на каждую тему подписки")
    void theFirstTickOpensARowForEveryTopicOfTheSubscription() {
        assertThat(rows.count(RECEPTION_TABLE)).as("строк состояния нет ни одной").isZero();
        OffsetDateTime beforeTick = now();

        givenReceptionStateRows();

        assertThat(pairs()).hasSize(subscription().size());
        assertThat(pairs().stream().map(row -> row.get(TOPIC_COLUMN)).toList())
                .as("строк для тем вне подписки не заведено ни одной")
                .containsExactlyInAnyOrderElementsOf(subscription());
        for (String topic : subscription()) {
            Map<String, Object> pair = pair(topic);
            assertThat(pair.get(GROUP_COLUMN)).isEqualTo(consumerGroup());
            assertThat(pair.get(SUBSCRIBED_COLUMN)).isEqualTo(Boolean.TRUE);
            assertThat(pair.get(HALTED_COLUMN))
                    .as("остановка есть исход отказа, а не начальное состояние")
                    .isEqualTo(Boolean.FALSE);
            assertThat(pair.get(GAP_COLUMN)).as("разрыва не было").isNull();
            assertThat(pair.get(LAST_ACCEPTED_COLUMN)).as("не принято ещё ничего").isNull();
            assertThat(instant(pair, OBSERVED_COLUMN)).isBetween(beforeTick.toInstant(), now().toInstant());
            assertThat(pair.get(UPDATED_COLUMN))
                    .as("момент наблюдения и момент обновления поставлены ОДНИМ тактом")
                    .isEqualTo(pair.get(OBSERVED_COLUMN));
        }
    }

    @Test
    @DisplayName("B3.2 — Второй такт строк не задваивает и момента наблюдения не переписывает")
    void aSecondTickNeitherDuplicatesRowsNorRewritesTheObservationMoment() {
        givenReceptionStateRows();
        Map<String, Object> coreBefore = pair(CORE);
        Map<String, Object> strategyBefore = pair(STRATEGY);

        tick();

        assertThat(pairs()).as("строк по-прежнему две").hasSize(subscription().size());
        assertThat(pair(CORE).get(OBSERVED_COLUMN))
                .as("момент наблюдения переписывает слушатель, и повод у него один")
                .isEqualTo(coreBefore.get(OBSERVED_COLUMN));
        assertThat(pair(STRATEGY).get(OBSERVED_COLUMN)).isEqualTo(strategyBefore.get(OBSERVED_COLUMN));
        assertThat(instant(pair(CORE), UPDATED_COLUMN))
                .as("момент обновления двинулся вперёд")
                .isAfter(instant(coreBefore, UPDATED_COLUMN));
        assertThat(instant(pair(STRATEGY), UPDATED_COLUMN)).isAfter(instant(strategyBefore, UPDATED_COLUMN));
        assertThat(pair(CORE).get(SUBSCRIBED_COLUMN)).isEqualTo(Boolean.TRUE);
        assertThat(pair(STRATEGY).get(SUBSCRIBED_COLUMN)).isEqualTo(Boolean.TRUE);
    }

    @Test
    @DisplayName("B3.3 — Момент обновления пишется каждым тактом, независимо от приёма")
    void theUpdateMomentIsWrittenByEveryTickRegardlessOfReception() {
        givenReceptionStateRows();
        tick();

        for (int pass = 0; pass < TICKS_OBSERVED; pass++) {
            Map<String, Object> coreBefore = pair(CORE);
            Map<String, Object> strategyBefore = pair(STRATEGY);
            pauseObserving(CORE);

            tick();

            assertThat(instant(pair(CORE), UPDATED_COLUMN)).isAfter(instant(coreBefore, UPDATED_COLUMN));
            assertThat(instant(pair(STRATEGY), UPDATED_COLUMN))
                    .isAfter(instant(strategyBefore, UPDATED_COLUMN));
            assertThat(pair(CORE).get(LAST_ACCEPTED_COLUMN))
                    .as("принимать было нечего, и момент принятого остался пустым").isNull();
            assertThat(pair(STRATEGY).get(LAST_ACCEPTED_COLUMN)).isNull();
            assertThat(continuityClaimable())
                    .as("предикат свежести строки состояния истинен всё это время")
                    .isEqualTo(Boolean.TRUE);
        }
    }

    @Test
    @DisplayName("B3.10 — Возраст уезжает МОМЕНТОМ, а не готовым числом")
    void theAgeTravelsAsAMomentRatherThanAsAReadyNumber() {
        givenReceptionStateRows();
        publish(CORE, "E-B3-10", momentsAgo(EVENT_AGE), Bodies.reference());
        awaitConsumed(CORE);
        tick();
        Object tickMoment = pair(CORE).get(UPDATED_COLUMN);
        Long firstScrape = ageRowOf(CORE);

        Awaitility.await()
                .atMost(RECEPTION_WAIT)
                .pollInterval(POLL)
                .until(() -> ageRowOf(CORE) - firstScrape >= OBSERVED_GROWTH);

        assertThat(ageRowOf(CORE) - firstScrape)
                .as("возраст считается в момент съёма, а не замирает вместе с тактом")
                .isGreaterThanOrEqualTo(OBSERVED_GROWTH);
        assertThat(pair(CORE).get(UPDATED_COLUMN))
                .as("промежуточного такта между съёмами не было").isEqualTo(tickMoment);
    }

    @Test
    @DisplayName("B3.11 — До первого принятого возраст берётся от момента наблюдения")
    void beforeTheFirstAcceptedEventTheAgeIsTakenFromTheObservationMoment() {
        givenReceptionStateRows();
        tick();

        assertThat(ageRowOf(CORE)).as("ряд возраста есть и до первого приёма").isNotNull().isPositive();
        assertThat(ageRowOf(STRATEGY)).isNotNull().isPositive();
        assertThat(ageRowOf(CORE)).isLessThan(OBSERVATION_AGE_CEILING);
        assertThat(pair(CORE).get(LAST_ACCEPTED_COLUMN)).isNull();

        publish(CORE, "E-B3-11", momentsAgo(EVENT_AGE), Bodies.reference());
        awaitConsumed(CORE);
        tick();

        assertThat(ageRowOf(CORE))
                .as("после первого принятого величина считается от момента его происшествия")
                .isGreaterThan(EVENT_AGE_FLOOR);
        assertThat(ageRowOf(STRATEGY))
                .as("у соседней пары основание не менялось")
                .isLessThan(OBSERVATION_AGE_CEILING);
    }

    @Test
    @DisplayName("B3.12 — Отказ посреди такта уносит ряды и уходит наружу")
    void aFailureInTheMiddleOfATickCarriesTheSeriesAwayAndGoesOutwards() {
        givenReceptionStateRows();
        tick();
        assertThat(receptionRowCount()).as("ряды выпущены прошлым тактом").isPositive();
        Object updatedBeforeFailure = pair(CORE).get(UPDATED_COLUMN);

        rows.withoutTable(RECEPTION_TABLE, () -> {
            assertThatThrownBy(this::tick)
                    .as("такт завершается отказом, а не молча")
                    .isInstanceOf(RuntimeException.class);
            assertThat(receptionRowCount())
                    .as("картина прошлого такта не показывается под видом нынешнего")
                    .isZero();
        });

        tick();

        assertThat(receptionRowCount()).as("следующий успешный такт ряды возвращает").isPositive();
        assertThat(pair(CORE).get(UPDATED_COLUMN)).isNotEqualTo(updatedBeforeFailure);
    }

    @Test
    @DisplayName("B3.13 — Ручного фасада у тика нет, и это поверхность, а не забытая точка")
    void theTickHasNoManualFacadeAndThatIsSurfaceRatherThanAForgottenPoint() {
        for (String path : TRIGGER_PATHS) {
            for (String method : METHODS) {
                assertThat(call(method, path).status())
                        .as("точки ручного триггера не существует: " + method + " " + path)
                        .isEqualTo(404);
            }
        }
        assertThat(call("POST", JOURNAL_RECORDS).status())
                .as("у единственной точки поверхности записи нет: метод не поддержан")
                .isEqualTo(405);

        Map<String, Object> routes = surfaceRoutes();
        assertThat(routes)
                .as("отображённый маршрут ровно один — чтение журнала")
                .containsOnlyKeys(JOURNAL_RECORDS);
        assertThat(methodsOf(routes, JOURNAL_RECORDS))
                .as("и метод у него один — чтение").containsOnlyKeys("get");
        assertThat(get(LIVENESS_PROBE, TENANT).status()).as("плюс проба живости").isEqualTo(200);
        assertThat(get(METRICS_SCRAPE, TENANT).status()).as("плюс съём рядов").isEqualTo(200);
    }

    /**
     * Ждёт, пока возраст по названной теме вырастет на величину паузы.
     *
     * @param topic тема, по ряду которой мерится пауза
     */
    private void pauseObserving(String topic) {
        Long before = ageRowOf(topic);
        Awaitility.await()
                .atMost(RECEPTION_WAIT)
                .pollInterval(POLL)
                .until(() -> ageRowOf(topic) - before >= OBSERVED_GROWTH);
    }

    /** Маршруты, которые сервис отображает наружу. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> surfaceRoutes() {
        return (Map<String, Object>) get(SURFACE_DESCRIPTION, TENANT).asObject().get("paths");
    }

    /**
     * Методы названного маршрута.
     *
     * @param routes маршруты описания поверхности
     * @param route  путь, чьи методы нужны
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> methodsOf(Map<String, Object> routes, String route) {
        return (Map<String, Object>) routes.get(route);
    }
}
