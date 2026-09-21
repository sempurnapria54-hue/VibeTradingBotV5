package com.example.audit.box;

import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Группа {@code B11} документа кейсов: отсутствие выходов
 * (.claude/tests/cases/audit.md §«B11 — Отсутствие выходов»).
 *
 * <p><b>Все семь клеток стоя́т на ШТАТНОМ положении осей и живут одним
 * классом:</b> предмет группы — чего сервис не делает НИ ПРИ КАКОМ входе, и
 * сдвинутая ось сузила бы утверждение до своего положения.
 *
 * <p><b>Клейм отсутствия читается по состоянию БРОКЕРА, по схеме и по
 * объявленной поверхности, а не по тому, что кейс не догадался позвать.</b>
 * Группа потребителя есть состояние на брокере; концы тем говорят, сколько
 * в них прибавилось; состав схемы печатает сама база; обращения к
 * провайдеру идентичности считает его стаб.
 *
 * <p><b>Стабов соседей у этого ящика нет ни одного, и это не пробел
 * субстрата.</b> Исходящих адресов сервису не назначено вовсе — стаб,
 * поднятый под несуществующий адрес, ловил бы не «сервис не позвал соседа»,
 * а «кейс не назвал адреса», то есть был бы зелен по построению
 * ({@link IdentityStub}, шапка). Структурная половина отрицания читается
 * поэтому по конфигурации сервиса, и это НАЗВАННАЯ ЦЕНА: поверхности,
 * отвечающей «каких клиентов я объявляю», у сервиса нет, а чтение бинов
 * контекста было бы касанием внутренности, которого форма ящика не
 * допускает (.claude/decisions/test-contour-design-pass.md, решение 1).
 *
 * <p><b>Отравленное сообщение здесь ПОЧИНЕНО тем же ходом, и замена
 * названа.</b> Группа {@code B2} платит за каждую свою клетку отдельным
 * контекстом ({@link PoisonedReceptionBox}) потому, что неизлечимое
 * отравление занимает единственный поток слушателя до конца прогона. Тропа
 * отказа нужна здесь ВХОДОМ, а не предметом, поэтому отравление ставится
 * отнятой таблицей журнала и снимается её возвратом: слушатель доживает до
 * успешного повтора, и общий контекст остаётся годным соседним клеткам.
 *
 * <p><b>Ожидание «большее нескольких тактов расписания» заменено ДВУМЯ
 * проходами каждой джобы, и замена названа.</b> Расписание в прогоне
 * глушится выражением такта, до которого он не доживает
 * ({@link AuditSubstrate}) — иначе выключатели тика и чистки перестали бы
 * быть входами своих клеток. Ждать тактов нечего: их не будет по
 * построению. Утверждение при этом не слабеет — джоб у сервиса ровно две, и
 * обе кейс подаёт сам.
 *
 * <p><b>Обращения к стабу провайдера читаются ДЕЛЬТОЙ окна клетки, а не его
 * накопленной выдачей.</b> Стаб копит пути всех кейсов прогона, и
 * накопленную выдачу уже читает {@code B9.2} — второго её носителя здесь не
 * заводится (Д1828). Предмет этой клетки другой: что ушло наружу за ПОЛНЫЙ
 * набор троп, а не за прогон целиком.
 *
 * <p><b>Названное ограничение наблюдателя одно:</b> внутренние темы брокера
 * (с префиксом {@code __}) заводит он сам под свою бухгалтерию, и
 * публикацией сервиса они не являются — равенство состава тем читается
 * поэтому по НЕвнутренним именам.
 */
class AbsentOutputsBoxTest extends SharedAuditBox {

    /** Тема первого производителя: она же первая тема подписки. */
    private static final String CORE = AuditSubstrate.CORE_TOPIC;

    /** Тема второго производителя: вторая тема подписки. */
    private static final String STRATEGY = AuditSubstrate.STRATEGY_TOPIC;

    /** Тема, на которую не подписан никто: ею читается «ни в чужие». */
    private static final String UNSUBSCRIBED = "audit-box.unsubscribed";

    /** Возраст события, с которым клетки ходят в тему. */
    private static final Duration EVENT_AGE = Duration.ofMinutes(5);

    /** Окно чтения, накрывающее всё, что кладут клетки. */
    private static final Duration READ_WINDOW = Duration.ofHours(1);

    /** Префикс внутренних тем брокера: их заводит он сам под свою бухгалтерию. */
    private static final String INTERNAL_PREFIX = "__";

    /** Хвост имени темы, которую завела бы переадресация отказавшего сообщения. */
    private static final String DEAD_LETTER_SUFFIX = ".DLT";

    /**
     * Начало имени темы, которую сервис завёл бы под СВОЙ след.
     *
     * <p><b>Клейм по нему АБСОЛЮТЕН, а не оконен, и это не избыточность
     * рядом с равенством состава.</b> Равенство читает окно клетки и видит
     * заведение, случившееся ВНУТРИ него; тема же, заводимая на каждом
     * принятом событии, появляется у брокера с первым событием прогона —
     * то есть раньше окна, — и к клетке приходит уже заведённой. Окно
     * тогда сходится при живом дефекте. Ни одна тема субстрата этого
     * начала не несёт: темы производителей названы их именами, темы клеток
     * — их метками, чужая — приставкой {@code audit-box}.
     */
    private static final String SERVICE_TOPIC_PREFIX = "audit.";

    /** Два пути, которыми сервис ходит к провайдеру идентичности, и иных нет. */
    private static final Set<String> IDENTITY_PATHS =
            Set.of("/.well-known/openid-configuration", "/jwks");

    /**
     * Сколько вызовов под токеном клетка делает после отметки.
     *
     * <p><b>Их больше одного намеренно:</b> ожидание «и не на каждый
     * запрос» выразимо только СРАВНЕНИЕМ числа обращений к провайдеру с
     * числом запросов, а на одном запросе оба числа совпали бы при любом
     * поведении.
     */
    private static final Integer AUTHENTICATED_PROBES = 5;

    /** Изменяющие методы: ими перебираются пути входящей записи. */
    private static final List<String> WRITING_METHODS = List.of("POST", "PUT", "PATCH", "DELETE");

    /**
     * Существующие и правдоподобные пути записи журнала.
     *
     * <p><b>Путей ручного триггера джоб здесь нет намеренно:</b> их
     * перебирает {@code B3.13} — там предмет ровно в том, что фасада нет у
     * тика, — и второго носителя этому не заводится (Д1828). Здесь предмет
     * другой: точки, которой кто-то мог бы ПОЛОЖИТЬ строку журнала, не
     * существует ни по одному адресу.
     */
    private static final List<String> WRITE_PATHS = List.of(
            JOURNAL_RECORDS,
            JOURNAL_RECORDS + "/E-1",
            "/api/v1/audit/journal",
            "/api/v1/audit/records",
            "/api/v1/audit/events",
            "/api/v1/audit");

    /** Пути, которыми журнал уезжал бы соседу пакетом, а не человеку страницей. */
    private static final List<String> EXPORT_PATHS = List.of(
            "/api/v1/audit/journal/export",
            "/api/v1/audit/journal/all",
            "/api/v1/audit/journal/stream",
            "/api/v1/audit/journal/records/bulk",
            "/api/v1/internal/audit/journal");

    /** Таблицы схемы сервиса: состав закрыт, и пятая предъявится падением. */
    private static final List<String> OWN_TABLES =
            List.of(JOURNAL_TABLE, RECEPTION_TABLE, DENIALS_TABLE, "flyway_schema_history");

    /** Таблицы чужих предметов: ни фактов статистики, ни торгового состояния. */
    private static final List<String> FOREIGN_TABLES = List.of(
            "statistics_facts", "statistics_aggregates", "journal_aggregates",
            "deals", "deal_tranches", "orders", "algo_orders", "positions",
            "candles", "instruments", "strategies", "tenants");

    /** Имена группы, которых процессу никто не называл. */
    private static final List<String> FOREIGN_GROUPS =
            List.of("audit.statistics", "statistics.journal", "audit");

    /** Четыре колонки радиуса: единственное, что сервис из содержимого берёт. */
    private static final List<String> RADIUS_COLUMNS = List.of("exchange_account_internal_id",
            "instrument_internal_id", "deal_internal_id", "strategy_internal_id");

    /**
     * Полный состав колонок строки журнала: ключ, конверт, два момента,
     * радиусы и само содержимое.
     *
     * <p><b>Перечень закрытый ровно затем, чтобы колонка, заведённая под
     * выведенную из содержимого величину, предъявилась падением, а не
     * растворилась в маске.</b>
     */
    private static final List<String> JOURNAL_COLUMNS = List.of(
            "id", "event_id", "tenant_id", "event_type", "version", "trace_context",
            "occurred_at", "recorded_at",
            "exchange_account_internal_id", "instrument_internal_id",
            "deal_internal_id", "strategy_internal_id", "content");

    /** Поля выдачи чтения: три и ни одного счётного. */
    private static final Set<String> PAGE_FIELDS = Set.of("records", "nextCursor", "completeness");

    /** Поля полноты: нижняя граница и предикат — ни суммы, ни доли. */
    private static final Set<String> COMPLETENESS_FIELDS =
            Set.of("lowerBound", "continuityClaimable");

    /** Поля строки выдачи: конверт, два момента, радиусы и содержимое. */
    private static final Set<String> RECORD_FIELDS = Set.of("eventId", "eventType", "occurredAt",
            "recordedAt", "version", "traceContext", "exchangeAccountInternalId",
            "instrumentInternalId", "dealInternalId", "strategyInternalId", "content");

    /**
     * Заводит тему, на которую не подписан никто.
     *
     * <p><b>Стои́т она до клеток, а не внутри своей</b>, потому что её конец
     * читают три клетки, а порядок методов каркас теста не обещает: тема,
     * заведённая внутри одной из них, отдавала бы соседкам отказ спроса
     * вместо нуля.
     */
    @BeforeAll
    static void givenTopicNobodySubscribesTo() {
        Wire.createTopic(UNSUBSCRIBED);
    }

    @Test
    @DisplayName("B11.1 — Сервис не публикует ничего")
    void theServicePublishesNothing() {
        Map<String, Long> endsBefore = endOffsets();
        Set<String> topicsBefore = externalTopics();

        everyTrope();

        assertThat(endOffsets().get(CORE) - endsBefore.get(CORE))
                .as("в тему первого производителя прибавилось ровно то, что положил "
                        + "кейс: штатная запись и отравленная")
                .isEqualTo(2L);
        assertThat(endOffsets().get(STRATEGY) - endsBefore.get(STRATEGY))
                .as("во вторую — ровно одна, положенная кейсом")
                .isEqualTo(1L);
        assertThat(endOffsets().get(UNSUBSCRIBED))
                .as("в чужую тему не ушло ни одной записи")
                .isZero();
        assertThat(externalTopics())
                .as("тем сервис не заводит ни одной, и темы мёртвых писем в их числе")
                .isEqualTo(topicsBefore)
                .noneMatch(topic -> topic.endsWith(DEAD_LETTER_SUFFIX))
                .as("и своей темы под след у него нет ни за одно событие прогона")
                .noneMatch(topic -> topic.startsWith(SERVICE_TOPIC_PREFIX));
        assertThat(rows.tableNames())
                .as("таблицы outbox в схеме нет ни одной колонкой: публиковать сервису нечем")
                .noneMatch(table -> table.contains("outbox"));
        assertThat(rows.count(JOURNAL_TABLE))
                .as("строк ровно столько, сколько принято событий: события о событии "
                        + "не заводится")
                .isEqualTo(3L);
    }

    @Test
    @DisplayName("B11.2 — Сервис не зовёт соседей")
    void theServiceCallsNoNeighbours() {
        journal(TENANT, momentsAgo(READ_WINDOW), now());
        Integer identityBefore = identity.paths().size();
        Set<String> topicsBefore = externalTopics();

        everyTrope();
        for (int probe = 0; probe < AUTHENTICATED_PROBES; probe++) {
            journal(TENANT, momentsAgo(READ_WINDOW), now());
        }

        List<String> duringTropes = identity.paths().subList(identityBefore, identity.paths().size());
        assertThat(duringTropes)
                .as("за полный набор троп к провайдеру ушли только описание издателя и "
                        + "ключи: точек подтверждения токена сервис не зовёт вовсе")
                .allMatch(IDENTITY_PATHS::contains);
        assertThat(duringTropes.size())
                .as("и не на каждый запрос: под токеном сделано больше %s вызовов, "
                        + "а подпись проверяется локально", AUTHENTICATED_PROBES)
                .isLessThan(AUTHENTICATED_PROBES);
        assertThat(externalTopics())
                .as("исходящее к брокеру ограничено чтением: ни темы, ни её настройки "
                        + "сервис не заводит и не меняет")
                .isEqualTo(topicsBefore)
                .noneMatch(topic -> topic.startsWith(SERVICE_TOPIC_PREFIX));
        assertThat(configuration())
                .as("исходящих ключей конфигурации соседей в дереве нет")
                .doesNotContain("neighbours", "base-url");
    }

    @Test
    @DisplayName("B11.3 — Входящей точки записи нет")
    void thereIsNoIncomingWritePoint() {
        publish(CORE, "E-B11-3", momentsAgo(EVENT_AGE), Bodies.reference());
        awaitRecordCount(1L);

        for (String path : WRITE_PATHS) {
            for (String method : WRITING_METHODS) {
                assertThat(call(method, path).status())
                        .as("записи не принимает %s %s: либо нет такого пути, либо метод "
                                + "не поддержан", method, path)
                        .isIn(404, 405);
            }
        }

        assertThat(rows.count(JOURNAL_TABLE))
                .as("строк журнала через поверхность не появилось ни одной")
                .isEqualTo(1L);
        assertThat(record().get("event_id"))
                .as("единственный писатель журнала — слушатель приёма")
                .isEqualTo("E-B11-3");
    }

    @Test
    @DisplayName("B11.4 — Сервис не толкует содержимое и не считает по нему величин")
    void theServiceNeitherInterpretsContentNorDerivesFiguresFromIt() {
        OffsetDateTime occurredAt = momentsAgo(EVENT_AGE);
        publishOfType(CORE, "E-B11-4-OPENED", DEAL_OPENED, Bodies.richDocument());
        publishOfType(STRATEGY, "E-B11-4-UNKNOWN", "SOMETHING_NOBODY_DECLARED",
                Bodies.richDocument());
        awaitRecordCount(2L);
        givenReceptionStateRows();

        tick();
        tick();
        cleanup();
        cleanup();
        Answer page = journal(TENANT, occurredAt.minusMinutes(1), now());

        assertThat(rows.tableNames())
                .as("таблиц фактов, агрегатов и проекций над содержимым нет ни одной")
                .containsExactlyInAnyOrderElementsOf(OWN_TABLES);
        assertThat(rows.columnNames(JOURNAL_TABLE))
                .as("и колонки под выведенную из содержимого величину тоже нет ни одной")
                .containsExactlyInAnyOrderElementsOf(JOURNAL_COLUMNS);
        Map<String, Object> row = rows.row(JOURNAL_TABLE, "event_id", "E-B11-4-OPENED");
        RADIUS_COLUMNS.forEach(column -> assertThat(row.get(column))
                .as("колонка радиуса %s пуста: одноимённого компонента содержимое "
                        + "не несёт, а толковать его сервис не берётся", column)
                .isNull());
        assertThat(row.get("event_type"))
                .as("класс события — дискриминатор конверта, а не вывод из содержимого")
                .isEqualTo(DEAL_OPENED);

        assertThat(page.status()).isEqualTo(200);
        assertThat(page.asObject().keySet())
                .as("выдача чтения несёт только свои три поля")
                .isSubsetOf(PAGE_FIELDS);
        assertThat(page.completeness().keySet())
                .as("и полнота — только границу и предикат: ни сумм, ни счётов, ни долей")
                .containsExactlyInAnyOrderElementsOf(COMPLETENESS_FIELDS);
        assertThat(page.records())
                .hasSize(2)
                .allSatisfy(record -> assertThat(record.keySet())
                        .as("а строка выдачи — только то, что приехало конвертом и телом")
                        .isSubsetOf(RECORD_FIELDS));
    }

    @Test
    @DisplayName("B11.5 — Журнала соседу сервис не отдаёт и в чужие базы не ходит")
    void theServiceNeitherExportsTheJournalNorReachesForeignDatabases() {
        everyTrope();

        for (String path : EXPORT_PATHS) {
            assertThat(get(path, TENANT).status())
                    .as("поверхности, отдающей журнал соседу пакетом, нет: %s", path)
                    .isEqualTo(404);
        }
        assertThat(journal(TENANT, momentsAgo(READ_WINDOW), now()).status())
                .as("все тропы проходят при единственной доступной базе")
                .isEqualTo(200);
        assertThat(sourceDeclarations())
                .as("второго источника данных контекст не объявляет: адрес базы в "
                        + "конфигурации один")
                .isEqualTo(1L);
        assertThat(rows.tableNames())
                .as("агрегатов соседа и чужого торгового состояния в схеме нет")
                .doesNotContainAnyElementsOf(FOREIGN_TABLES);
    }

    @Test
    @DisplayName("B11.6 — Сервис не принимает за вторую durable-группу")
    void theServiceJoinsNoSecondDurableGroup() {
        everyTrope();

        assertThat(Wire.consumerGroups())
                .as("всякая известная брокеру группа названа конфигурацией одного из "
                        + "контекстов прогона: имени, которого процессу не называли, "
                        + "среди них нет")
                .contains(consumerGroup())
                .allMatch(group -> group.startsWith(AuditSubstrate.CONSUMER_GROUP));
        assertThat(pairs().stream().map(pair -> pair.get(GROUP_COLUMN)).distinct().toList())
                .as("строк состояния с чужим именем группы он не заводит и не обновляет")
                .containsExactly(consumerGroup());
        FOREIGN_GROUPS.forEach(group -> assertThat(Wire.committedOffset(group, CORE))
                .as("смещений группы %s он не двигает: её у брокера нет вовсе", group)
                .isNull());
    }

    @Test
    @DisplayName("B11.7 — Журнал не влияет на решения и ничего не запрещает")
    void theJournalDrivesNoDecisionAndForbidsNothing() {
        publishOfType(CORE, "E-B11-7-OPENED", DEAL_OPENED, Bodies.reference());
        publishOfType(STRATEGY, "E-B11-7-ACTIVATED", "STRATEGY_ACTIVATED", Bodies.reference());
        awaitRecordCount(2L);
        givenReceptionStateRows();
        Integer identityBefore = identity.paths().size();
        Map<String, Long> endsBefore = endOffsets();

        tick();
        tick();
        cleanup();
        cleanup();

        assertThat(identity.paths().size())
                .as("к стабам соседей не уходит ни одной команды: исходящего вызова у "
                        + "проходов нет вовсе")
                .isEqualTo(identityBefore);
        assertThat(endOffsets())
                .as("и ни одной команды в тему: концы всех трёх тем не сдвинулись")
                .isEqualTo(endsBefore);
        assertThat(rows.tableNames())
                .as("строк о сделках, заявках и позициях не появляется: таблиц под них нет")
                .doesNotContainAnyElementsOf(FOREIGN_TABLES);
        assertThat(rows.count(JOURNAL_TABLE))
                .as("проходов, читающих рынок или состояние торговли, у сервиса нет — "
                        + "джоб две, и журнала не пополняет ни одна")
                .isEqualTo(2L);
    }

    /**
     * Полный набор троп сервиса: приём обеих тем, отравленное сообщение,
     * такт тика, проход чистки, чтение страницы и отказ доступа.
     *
     * <p><b>Он один на четыре клетки, и это не экономия:</b> предмет каждой
     * — отсутствие выхода ПРИ ЛЮБОМ входе, и разный набор троп у соседних
     * клеток сделал бы их утверждения о разном.
     */
    private void everyTrope() {
        publish(CORE, "E-B11-CORE", momentsAgo(EVENT_AGE), Bodies.reference());
        publish(STRATEGY, "E-B11-STRATEGY", momentsAgo(EVENT_AGE), Bodies.reference());
        awaitRecordCount(2L);
        givenReceptionStateRows();
        poisonAndHeal();
        tick();
        cleanup();
        journal(TENANT, momentsAgo(READ_WINDOW), now());
        getAnonymously(JOURNAL_RECORDS);
    }

    /**
     * Проводит сообщение по тропе ОТКАЗА и возвращает приём к жизни.
     *
     * <p>Отнятая таблица журнала роняет обработку, обработчик отказа ставит
     * флаг остановки и уходит в повтор; возвращённая таблица даёт повтору
     * состояться, и слушатель освобождает свой единственный поток.
     */
    private void poisonAndHeal() {
        rows.withoutTable(JOURNAL_TABLE, () -> {
            publish(CORE, "E-B11-POISON", momentsAgo(EVENT_AGE), Bodies.reference());
            awaitHalted(CORE);
        });
        awaitRecordCount(3L);
    }

    /**
     * Кладёт в тему запись названного класса.
     *
     * @param topic   тема производителя
     * @param eventId идентичность события
     * @param type    класс события в заголовке конверта
     * @param content содержимое дословно
     */
    private void publishOfType(String topic, String eventId, String type, String content) {
        Map<String, String> headers = new LinkedHashMap<>(envelope(eventId, momentsAgo(EVENT_AGE)));
        headers.put(EVENT_TYPE, type);
        Wire.publish(topic, TENANT, headers, content);
    }

    /** Концы трёх тем брокера: двух своих и одной чужой. */
    private Map<String, Long> endOffsets() {
        Map<String, Long> ends = new LinkedHashMap<>();
        ends.put(CORE, Wire.endOffset(CORE));
        ends.put(STRATEGY, Wire.endOffset(STRATEGY));
        ends.put(UNSUBSCRIBED, Wire.endOffset(UNSUBSCRIBED));
        return ends;
    }

    /** Темы брокера за вычетом его собственных внутренних. */
    private Set<String> externalTopics() {
        return Wire.topicNames().stream()
                .filter(topic -> isFalse(topic.startsWith(INTERNAL_PREFIX)))
                .collect(Collectors.toSet());
    }

    /** Сколько адресов источника данных объявляет конфигурация сервиса. */
    private Long sourceDeclarations() {
        return configuration().lines()
                .filter(line -> line.strip().startsWith("url:"))
                .count();
    }

    /**
     * Конфигурация сервиса дословно.
     *
     * <p>Читается она как ВХОД ящика — тем же родом, что оси
     * {@code @DynamicPropertySource} группы {@code B10}, — а не как его
     * внутренность: поверхности, отвечающей «каких клиентов я объявляю», у
     * сервиса нет, и довод живёт в шапке класса.
     */
    private String configuration() {
        try (InputStream source = getClass().getResourceAsStream("/application.yaml")) {
            return new String(source.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new IllegalStateException("Конфигурация сервиса не прочиталась", failure);
        }
    }
}
