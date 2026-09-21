package com.example.statistics.box;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Группа {@code B3} документа кейсов — та её часть, которой штатное положение
 * осей конфигурации является входом
 * (.claude/tests/cases/statistics.md §«B3 — Тик состояния приёма: состав пар,
 * живость, ряды»).
 *
 * <p><b>Тик подаётся ПРЯМЫМ ВЫЗОВОМ метода джобы, и это объявленный вход, а
 * не подмена.</b> Ручного фасада нет ни у одной из двух джоб сервиса
 * намеренно: поверхность объявлена только читающей
 * (docs/architecture/services/statistics.md §«Какие вызовы делает и какие
 * принимает»), и решение 6 называет эту точку единственной, где кейс уровня 1
 * касается бина.
 *
 * <p><b>Пауза между наблюдениями выражена РОСТОМ наблюдаемой величины, а не
 * сном потока.</b> Возраст последнего принятого события считается в момент
 * съёма, поэтому между тактами он растёт сам — и ожидание его роста есть
 * условие, а не срок. Сон платил бы временем всегда и детерминированным не
 * был бы (решение 6).
 *
 * <p><b>Клетки, сдвигающие оси конфигурации либо отнимающие у подписки
 * назначение, живут своими классами:</b> состав подписки, выключатель тика и
 * допустимый возраст строки суть ВХОДЫ таких клеток, а контекст читает их при
 * подъёме.
 */
class ReceptionStateTickBoxTest extends SharedStatisticsBox {

    /**
     * Тема, ушедшая из подписки.
     *
     * <p><b>Имя взято у СОСЕДНЕГО производителя, а не выдумано.</b> Тему
     * владельца определений группа статистики не читает — операнда зерна не
     * несёт ни один его класс (docs/models/domain/other/StatisticsFact.md
     * §«Признак несомого класса»), — и это ровно состояние «тема из подписки
     * ушла»: строка пары есть, а подписка её больше не называет.
     */
    private static final String DEPARTED = "strategies.facts";

    /** Биржевой счёт — обязательный ключ обоих зёрен. */
    private static final String ACCOUNT = "ACCOUNT-1";

    /** Определение стратегии — компонент ключа сделочного зерна. */
    private static final String STRATEGY = "S-1";

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

    /** Насколько раньше такта наблюдается ушедшая тема. */
    private static final Duration OBSERVED_BEFORE = Duration.ofHours(2);

    /** Вставка строки пары: колонки те же, которыми её заводит тик. */
    private static final String OPEN_PAIR = """
            insert into reception_states
                (consumer_group, topic, observed_since, subscribed, reception_halted, updated_at)
            values (?, ?, ?, true, false, ?)
            """;

    /** Пути ручного триггера джоб по конвенции соседних сервисов. */
    private static final List<String> TRIGGER_PATHS = List.of(
            "/api/v1/statistics/jobs",
            "/api/v1/statistics/jobs/reception-state",
            "/api/v1/statistics/jobs/aggregate-recompute");

    /** Методы, которыми перебираются пути ручного триггера. */
    private static final List<String> METHODS = List.of("GET", "POST", "PUT", "PATCH", "DELETE");

    /** Манифест сервиса: в нём живут правила алерта, называющие ряды. */
    private static final Path MANIFEST =
            Path.of("..", "..", "deploy", "base", "services", "statistics.yaml");

    /**
     * Имя ряда приёма в тексте манифеста.
     *
     * <p><b>Шаблон берёт ЛЮБОЕ пространство имён, а не своё.</b> Собранные
     * шаблоном {@code statistics_reception_…} имена принадлежали бы своему
     * пространству по построению, и утверждение о том, что имена СВОИ, мерило
     * бы сам шаблон, а не манифест.
     */
    private static final Pattern SERIES = Pattern.compile("[a-z][a-z0-9_]*_reception_[a-z_]+");

    /** Пространство имён, которому ряды этого сервиса принадлежат. */
    private static final String OWN_NAMESPACE = "statistics_reception_";

    /** Пространство имён рядов журнала: с ним имена статистики не совпадают. */
    private static final String JOURNAL_NAMESPACE = "audit_journal_reception";

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
            assertThat(instant(pair, OBSERVED_COLUMN))
                    .isBetween(beforeTick.toInstant(), now().toInstant());
            assertThat(pair.get(UPDATED_COLUMN))
                    .as("момент наблюдения и момент обновления поставлены ОДНИМ тактом")
                    .isEqualTo(pair.get(OBSERVED_COLUMN));
        }
    }

    @Test
    @DisplayName("B3.2 — Второй такт строк не задваивает и момента наблюдения не переписывает")
    void aSecondTickNeitherDuplicatesRowsNorRewritesTheObservationMoment() {
        givenReceptionStateRows();
        Map<String, Object> before = pair(topic());

        tick();

        assertThat(pairs()).as("строк по-прежнему столько же").hasSize(subscription().size());
        assertThat(pair(topic()).get(OBSERVED_COLUMN))
                .as("момент наблюдения переписывает слушатель, и повод у него один")
                .isEqualTo(before.get(OBSERVED_COLUMN));
        assertThat(instant(pair(topic()), UPDATED_COLUMN))
                .as("момент обновления двинулся вперёд")
                .isAfter(instant(before, UPDATED_COLUMN));
        assertThat(pair(topic()).get(SUBSCRIBED_COLUMN)).isEqualTo(Boolean.TRUE);
        assertThat(pair(topic()).get(HALTED_COLUMN))
                .as("величины приёма такт не трогает").isEqualTo(Boolean.FALSE);
        assertThat(pair(topic()).get(GAP_COLUMN)).isNull();
        assertThat(pair(topic()).get(LAST_ACCEPTED_COLUMN)).isNull();
    }

    @Test
    @DisplayName("B3.3 — Момент обновления пишется каждым тактом, независимо от приёма")
    void theUpdateMomentIsWrittenByEveryTickRegardlessOfReception() {
        givenReceptionStateRows();
        tick();

        for (int pass = 0; pass < TICKS_OBSERVED; pass++) {
            Map<String, Object> before = pair(topic());
            pauseObserving(topic());

            tick();

            assertThat(instant(pair(topic()), UPDATED_COLUMN))
                    .isAfter(instant(before, UPDATED_COLUMN));
            assertThat(pair(topic()).get(LAST_ACCEPTED_COLUMN))
                    .as("принимать было нечего, и момент принятого остался пустым").isNull();
            assertThat(continuityClaimable())
                    .as("предикат свежести строки состояния истинен всё это время")
                    .isEqualTo(Boolean.TRUE);
        }
    }

    @Test
    @DisplayName("B3.4 — Ушедшая из подписки тема получает ложь, а строка не удаляется")
    void aTopicThatLeftTheSubscriptionGetsFalseWhileItsRowSurvives() {
        givenReceptionStateRows();
        OffsetDateTime observedSince = momentsAgo(OBSERVED_BEFORE);
        rows.write(OPEN_PAIR, consumerGroup(), DEPARTED, observedSince, observedSince);
        Map<String, Object> subscribedBefore = pair(topic());

        tick();

        assertThat(pairs()).as("строк по-прежнему две: ушедшая не удалена").hasSize(2);
        assertThat(pair(topic()).get(SUBSCRIBED_COLUMN))
                .as("оставшаяся в подписке несёт истину").isEqualTo(Boolean.TRUE);
        assertThat(pair(DEPARTED).get(SUBSCRIBED_COLUMN))
                .as("ушедшая — ложь").isEqualTo(Boolean.FALSE);
        assertThat(instant(pair(DEPARTED), OBSERVED_COLUMN))
                .as("момент наблюдения ушедшей такт не трогает: им держится нижняя граница полноты")
                .isEqualTo(observedSince.toInstant());
        assertThat(instant(pair(DEPARTED), UPDATED_COLUMN))
                .as("момент обновления двинулся и у ушедшей")
                .isAfter(observedSince.toInstant());
        assertThat(instant(pair(topic()), UPDATED_COLUMN))
                .isAfter(instant(subscribedBefore, UPDATED_COLUMN));
        assertThat(lowerBound())
                .as("граница берётся по ВСЕМ строкам, и ушедшая из неё не выпала").isNotNull();
    }

    @Test
    @DisplayName("B3.10 — Возраст уезжает МОМЕНТОМ, а не готовым числом")
    void theAgeTravelsAsAMomentRatherThanAsAReadyNumber() {
        givenReceptionStateRows();
        publish("E-B3-10", DEAL_CLOSED, momentsAgo(EVENT_AGE), Bodies.dealClosed(ACCOUNT, STRATEGY));
        awaitConsumed();
        tick();
        Object tickMoment = pair(topic()).get(UPDATED_COLUMN);
        Long firstScrape = ageRowOf(topic());

        Awaitility.await()
                .atMost(RECEPTION_WAIT)
                .pollInterval(POLL)
                .until(() -> ageRowOf(topic()) - firstScrape >= OBSERVED_GROWTH);

        assertThat(ageRowOf(topic()) - firstScrape)
                .as("возраст считается в момент съёма, а не замирает вместе с тактом")
                .isGreaterThanOrEqualTo(OBSERVED_GROWTH);
        assertThat(pair(topic()).get(UPDATED_COLUMN))
                .as("промежуточного такта между съёмами не было").isEqualTo(tickMoment);
    }

    @Test
    @DisplayName("B3.11 — До первого принятого возраст берётся от момента наблюдения")
    void beforeTheFirstAcceptedEventTheAgeIsTakenFromTheObservationMoment() {
        givenReceptionStateRows();
        tick();

        assertThat(ageRowOf(topic()))
                .as("ряд возраста есть и до первого приёма").isNotNull().isPositive();
        assertThat(ageRowOf(topic())).isLessThan(OBSERVATION_AGE_CEILING);
        assertThat(pair(topic()).get(LAST_ACCEPTED_COLUMN)).isNull();

        publish("E-B3-11", DEAL_CLOSED, momentsAgo(EVENT_AGE), Bodies.dealClosed(ACCOUNT, STRATEGY));
        awaitConsumed();
        tick();

        assertThat(ageRowOf(topic()))
                .as("после первого принятого величина считается от момента его происшествия")
                .isGreaterThan(EVENT_AGE_FLOOR);
    }

    @Test
    @DisplayName("B3.12 — Отказ посреди такта уносит ряды и уходит наружу")
    void aFailureInTheMiddleOfATickCarriesTheSeriesAwayAndGoesOutwards() {
        givenReceptionStateRows();
        tick();
        assertThat(receptionRowCount()).as("ряды выпущены прошлым тактом").isPositive();
        Object updatedBeforeFailure = pair(topic()).get(UPDATED_COLUMN);

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
        assertThat(pair(topic()).get(UPDATED_COLUMN)).isNotEqualTo(updatedBeforeFailure);
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
        assertThat(call("POST", AGGREGATE_ROWS).status())
                .as("у единственной точки поверхности записи нет: метод не поддержан")
                .isEqualTo(405);

        Map<String, Object> routes = surfaceRoutes();
        assertThat(routes)
                .as("отображённый маршрут ровно один — агрегатная выборка")
                .containsOnlyKeys(AGGREGATE_ROWS);
        assertThat(methodsOf(routes, AGGREGATE_ROWS))
                .as("и метод у него один — чтение").containsOnlyKeys("get");
        assertThat(get(LIVENESS_PROBE, TENANT).status()).as("плюс проба живости").isEqualTo(200);
        assertThat(get(METRICS_SCRAPE, TENANT).status()).as("плюс съём рядов").isEqualTo(200);
    }

    @Test
    @DisplayName("B3.15 — Имена рядов — контракт с правилом алерта, и они СВОИ")
    void theSeriesNamesAreAContractWithTheAlertRuleAndTheyAreItsOwn() throws IOException {
        givenReceptionStateRows();
        publish("E-B3-15", DEAL_CLOSED, momentsAgo(EVENT_AGE), Bodies.dealClosed(ACCOUNT, STRATEGY));
        awaitConsumed();
        tick();

        Set<String> named = seriesNamedByManifest();
        assertThat(named)
                .as("клетка без имён сошлась бы зелёной, ничего не измерив")
                .isNotEmpty();
        String exposition = scrape();
        assertThat(named)
                .as("имя печатает соглашение реестра, а правило манифеста берёт его на веру")
                .allSatisfy(series -> assertThat(exposition).contains(series));
        assertThat(named)
                .as("каждое имя, названное правилами, принадлежит пространству имён статистики")
                .allSatisfy(series -> assertThat(series).startsWith(OWN_NAMESPACE));
        assertThat(exposition)
                .as("журнальных рядов эта экспозиция не печатает: один ряд на две группы склеил бы "
                        + "их состояния")
                .doesNotContain(JOURNAL_NAMESPACE);
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

    /** Имена рядов приёма, названные текстом манифеста сервиса. */
    private Set<String> seriesNamedByManifest() throws IOException {
        assertThat(Files.exists(MANIFEST))
                .as("манифест ищется от каталога модуля: %s", MANIFEST.toAbsolutePath())
                .isTrue();
        Set<String> named = new LinkedHashSet<>();
        Matcher matcher = SERIES.matcher(Files.readString(MANIFEST, StandardCharsets.UTF_8));
        while (matcher.find()) {
            named.add(matcher.group());
        }
        return named;
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
