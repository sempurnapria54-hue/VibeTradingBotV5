package com.example.statistics.box;

import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Группа {@code B13} документа кейсов: отсутствие выходов
 * (.claude/tests/cases/statistics.md §«B13 — Отсутствие выходов»).
 *
 * <p><b>Все девять клеток живут одним классом:</b> предмет группы — чего
 * сервис не делает НИ ПРИ КАКОМ входе, и сдвинутая ось конфигурации сузила бы
 * утверждение до своего положения. Оси, разведшей группы {@code B7} и
 * {@code B12} по классам, здесь не ставит ни одна клетка.
 *
 * <p><b>Своя ПАРА «группа + тема» при этом обязательна, и довод у неё
 * механический — тот, что объявлен у самого хода
 * ({@link StatisticsSubstrate#registerOwn}): класс ЛОМАЕТ приём.</b> Полный
 * набор входов включает тропу отказа, а она есть состояние, общее всем
 * контекстам одного имени: обработчик отказа перематывает позицию и уводит
 * группу в перетряхивание назначения, а попартиционный ряд лага у клиента
 * после этого возвращается пустым до первой новой выборки. Сосед, чей предмет
 * — ряды съёма, получал бы тогда отсутствие ряда по причине, которой не
 * ставил. Замерено прогоном: на общей паре клетка о съёме рядов краснела
 * ровно так.
 *
 * <p><b>Тропа отказа при этом НЕСУЩАЯ, а не полнота ради полноты.</b> Тема
 * мёртвых писем заводится только на отказавшем сообщении, и отрицание «её у
 * брокера нет» без такого сообщения было бы верным на пустом месте.
 *
 * <p><b>Клейм отсутствия читается по состоянию БРОКЕРА, по чужой БАЗЕ, по
 * схеме и по объявленной поверхности, а не по тому, что кейс не догадался
 * позвать.</b> Группа потребителя есть состояние на брокере; концы тем
 * говорят, сколько в них прибавилось; счётчики подключений и транзакций
 * общекластерные и говорят о спросе к соседней базе; состав схемы печатает
 * сама база; обращения к провайдеру идентичности считает его стаб.
 *
 * <p><b>Стабов соседей у этого ящика нет ни одного, и это не пробел
 * субстрата.</b> Исходящих адресов сервису не назначено вовсе
 * (.claude/tests/cases/statistics.md §«Чем достаются выходы»): стаб,
 * поднятый под несуществующий адрес, ловил бы не «сервис не позвал соседа», а
 * «кейс не назвал адреса», то есть был бы зелен по построению. Структурная
 * половина отрицания читается поэтому по конфигурации сервиса, и это
 * НАЗВАННАЯ ЦЕНА: поверхности, отвечающей «каких клиентов я объявляю», у
 * сервиса нет, а перебор бинов контекста был бы взглядом внутрь ящика
 * (.claude/decisions/test-contour-design-pass.md, решение 1).
 *
 * <p><b>Чужая база при этом поднимается РЯДОМ, а не заменяется чтением
 * конфигурации.</b> Отрицание «журнал сервис не читает», стоящее на одном
 * адресе в конфигурации, утверждает о НАМЕРЕНИИ и сходится там, где читать
 * было нечего. База-соседка делает клейм непустым: её строка лежит, спрос к
 * ней возможен из того же кластера, и предмет клетки ровно в том, что спроса
 * не случилось ни одного ({@link Rows#beside}).
 *
 * <p><b>Отравленное сообщение здесь ПОЧИНЕНО тем же ходом, и замена
 * названа.</b> Группа {@code B2} платит за каждую свою клетку отдельным
 * контекстом ({@link PoisonedReceptionBox}) потому, что неизлечимое
 * отравление занимает единственный поток слушателя до конца прогона. Тропа
 * отказа нужна здесь ВХОДОМ, а не предметом, поэтому отравление ставится
 * отнятой таблицей фактов и снимается её возвратом: слушатель доживает до
 * успешного повтора, и общий контекст остаётся годным соседним клеткам.
 *
 * <p><b>Ожидание «большее нескольких тактов расписания» заменено ПРЯМЫМ
 * тактом каждой джобы, и замена названа.</b> Расписание в прогоне глушится
 * выражением такта, до которого он не доживает ({@link StatisticsSubstrate}),
 * — иначе выключатели обеих джоб перестали бы быть входами своих клеток.
 * Ждать тактов нечего: их не будет по построению. Утверждение при этом не
 * слабеет — джоб у сервиса ровно две, и обе кейс подаёт сам.
 *
 * <p><b>Три половины клеток здесь не повторяются, и у каждой назван свой
 * носитель</b> (Д1828, .claude/rules/carrier-levels.md): «ручного запуска
 * джоб нет» несёт {@code B3.13} — там предмет ровно в этом; «неразложенные
 * поля не сохранены нигде» и «поверхности, отдающей строку факта, не
 * существует» несёт {@code B1.17}; «колонок аудита сущности у строк нет» —
 * {@code B12.10}. Второй записи тем же клеймам не заводится.
 *
 * <p><b>Названное ограничение наблюдателя одно:</b> внутренние темы брокера
 * (с префиксом {@code __}) заводит он сам под свою бухгалтерию, и публикацией
 * сервиса они не являются — равенство состава тем читается поэтому по
 * НЕвнутренним именам.
 */
class AbsentOutputsBoxTest extends StatisticsBox {

    /** Краткое имя клеток: из него строятся их группа и их тема. */
    private static final String SLUG = "b13-absent";

    /**
     * Тема, на которую не подписан никто: ею читается «ни в чужие».
     *
     * <p><b>Имя её НЕ начинается ни со слова «statistics», ни с имени
     * сервиса, и это не вкус.</b> Соседняя клетка {@code B1.1} утверждает,
     * что тем, чьё имя начинается со слова сервиса, у брокера нет ни одной, —
     * и её предикат шире, чем здешний {@link #SERVICE_TOPIC_PREFIX}. Тема
     * прогона, названная приставкой сервиса, уронила бы соседку по причине,
     * которой та не ставила.
     */
    private static final String UNSUBSCRIBED = "nobody-subscribes.topic";

    /** Возраст события, с которым клетки ходят в тему. */
    private static final Duration EVENT_AGE = Duration.ofMinutes(5);

    /** Префикс внутренних тем брокера: их заводит он сам под свою бухгалтерию. */
    private static final String INTERNAL_PREFIX = "__";

    /** Хвост имени темы, которую завела бы переадресация отказавшего сообщения. */
    private static final String DEAD_LETTER_SUFFIX = ".DLT";

    /**
     * Начало имени темы, которую сервис завёл бы под СВОЙ выход.
     *
     * <p><b>Клейм по нему АБСОЛЮТЕН, а не оконен, и это не избыточность рядом
     * с равенством состава.</b> Равенство читает окно клетки и видит
     * заведение, случившееся ВНУТРИ него; тема же, заводимая на каждом
     * принятом событии, появляется у брокера с первым событием прогона — то
     * есть раньше окна, — и к клетке приходит уже заведённой. Окно тогда
     * сходится при живом дефекте. Ни одна тема субстрата этого начала не
     * несёт: тема производителя названа его именем, темы классов со своей
     * парой — их метками ({@link StatisticsSubstrate#ownTopic}), чужая —
     * приставкой {@code nobody-subscribes} (довод — у {@link #UNSUBSCRIBED}).
     */
    private static final String SERVICE_TOPIC_PREFIX = "statistics.";

    /** Два пути, которыми сервис ходит к провайдеру идентичности, и иных нет. */
    private static final Set<String> IDENTITY_PATHS =
            Set.of("/.well-known/openid-configuration", "/jwks");

    /**
     * Сколько вызовов под токеном клетка делает после отметки.
     *
     * <p><b>Их больше одного намеренно:</b> ожидание «и не на каждый запрос»
     * выразимо только СРАВНЕНИЕМ числа обращений к провайдеру с числом
     * запросов, а на одном запросе оба числа совпали бы при любом поведении.
     */
    private static final Integer AUTHENTICATED_PROBES = 5;

    /** Изменяющие методы: ими перебираются пути входящей записи. */
    private static final List<String> WRITING_METHODS = List.of("POST", "PUT", "PATCH", "DELETE");

    /**
     * Существующие и правдоподобные пути записи.
     *
     * <p><b>Путей ручного триггера джоб здесь нет намеренно:</b> их
     * перебирает {@code B3.13} — там предмет ровно в том, что фасада нет ни у
     * одной из двух джоб, — и второго носителя этому не заводится (Д1828).
     * Здесь предмет другой: точки, которой кто-то мог бы ПОЛОЖИТЬ строку, не
     * существует ни по одному адресу.
     */
    private static final List<String> WRITE_PATHS = List.of(
            AGGREGATE_ROWS,
            AGGREGATE_ROWS + "/D-1",
            "/api/v1/statistics/aggregates",
            "/api/v1/statistics/facts",
            "/api/v1/statistics/events",
            "/api/v1/statistics");

    /** Пути, которыми строка факта уезжала бы наружу пакетом либо поштучно. */
    private static final List<String> FACT_PATHS = List.of(
            "/api/v1/statistics/facts",
            "/api/v1/statistics/facts/rows",
            "/api/v1/statistics/deal-facts",
            "/api/v1/statistics/aggregates/facts",
            "/api/v1/internal/statistics/facts");

    /** Таблицы предмета: шесть, положенных цепочкой миграций. */
    private static final List<String> SUBJECT_TABLES = List.of(
            DEAL_FACTS, INCIDENT_FACTS, DEAL_AGGREGATES, INCIDENT_AGGREGATES,
            RECEPTION_TABLE, DENIALS_TABLE);

    /** Таблицы чужих предметов: ни журнала, ни торгового состояния, ни рынка. */
    private static final List<String> FOREIGN_TABLES = List.of(
            "audit_records", "journal_records", "journal_aggregates",
            "deals", "deal_tranches", "orders", "algo_orders", "positions",
            "candles", "instruments", "strategies", "tenants", "exchange_accounts");

    /** Имена группы, которых процессу никто не называл. */
    private static final List<String> FOREIGN_GROUPS =
            List.of("audit.journal", "statistics", "statistics.journal");

    /** Чужая база рядом: журнал аудита, поднятый в том же кластере. */
    private static final String JOURNAL_DATABASE = "audit_journal_beside";

    /** Таблица журнала в чужой базе: её строку сервис мог бы прочитать и не читает. */
    private static final String JOURNAL_TABLE = "audit_records";

    /** Наблюдатель строк чужой базы: он же её и заводит. */
    private static final Rows JOURNAL = Rows.beside(JOURNAL_DATABASE);

    /**
     * Поля страницы выдачи: зерно, строки двух зёрен, позиция и полнота — и ни
     * одного, несущего строку факта.
     */
    private static final Set<String> PAGE_FIELDS =
            Set.of("grain", "dealRows", "incidentRows", "nextCursor", "completeness");

    /** Поля полноты: нижняя граница и предикат — ни суммы, ни доли. */
    private static final Set<String> COMPLETENESS_FIELDS =
            Set.of("lowerBound", "continuityClaimable");

    /**
     * Полный состав полей строки сделочного агрегата.
     *
     * <p><b>Перечень закрытый ровно затем, чтобы величина, заведённая по
     * операнду, которого факт не несёт, предъявилась падением, а не
     * растворилась в маске.</b>
     */
    private static final Set<String> DEAL_ROW_FIELDS = Set.of(
            "exchangeAccountInternalId", "strategyInternalId", "bucketDate", "resultCurrency",
            "closedDeals", "riskBearingDeals", "winningDeals", "losingDeals", "neutralDeals",
            "resultUnavailableDeals", "currencyUnresolvedDeals", "riskUnsizedDeals",
            "liquidatedDeals", "forcedReductionDeals", "outcomeUndeterminedDeals",
            "reconciliationMismatchedDeals", "reconciliationNotRunDeals",
            "breakdownIncompleteDeals", "breakdownNotAssessedDeals",
            "riskBenchmarkMissingDeals", "rDenominatorDeals",
            "resultBeforeFundingSum", "netResultSum", "feeSum", "fundingSum",
            "liquidationPenaltySum", "winResultSum", "lossResultSum",
            "plannedRiskSum", "plannedRiskExcludedSum", "rSum", "assembledAt");

    /** Полный состав полей строки агрегата происшествий. */
    private static final Set<String> INCIDENT_ROW_FIELDS = Set.of(
            "exchangeAccountInternalId", "bucketDate", "openedDeals", "orderDecisions",
            "raisedHolds", "hardRaisedHolds", "manuallyRaisedHolds",
            "anomalyReports", "criticalAnomalyReports", "manualOperationReports", "assembledAt");

    /**
     * Слова величин СОСТОЯНИЯ торговли: экспозиции, живого риска и плеча.
     *
     * <p>Ими читается отрицание «экспозиции и живого риска сервис не считает»:
     * это состояние, а не происшествие, и читается оно у владельца
     * (docs/rules/statistics-aggregates.md §«Чего агрегаты не считают»).
     */
    private static final List<String> STATE_WORDS =
            List.of("exposure", "liverisk", "openrisk", "equity", "leverage", "margin");

    /**
     * Полный состав колонок строки сделочного факта: ключ строки, ключи зерна,
     * ось времени и операнды счётчиков.
     *
     * <p><b>Перечень закрытый ровно затем, чтобы колонка, заведённая под
     * выведенную из содержимого величину, предъявилась падением, а не
     * растворилась в маске.</b> Колонок аудита сущности среди них нет по своей
     * причине, и её носитель — {@code B12.10} (Д1828).
     */
    private static final List<String> DEAL_FACT_COLUMNS = List.of(
            "event_id", "tenant_id", "exchange_account_internal_id", "strategy_internal_id",
            "result_currency", "closed_at", "took_risk", "graph_complete", "net_result",
            "fee", "funding", "liquidation_penalty", "planned_risk", "close_outcome",
            "reconciliation_status", "breakdown_incomplete", "risk_benchmark_availability");

    /** Неразложенные поля содержимого: их несёт богатый документ клетки. */
    private static final List<String> UNRESOLVED_FIELDS =
            List.of("nested", "series", "objects", "альфа");

    /** Сколько событий кладёт клетка об агрегате, не двигаемом событием. */
    private static final Integer LATE_EVENTS = 10;

    /** Сутки, в которых лежат факты клеток о пересчёте. */
    private static final Integer DAY = 1;

    /** Нынешние сутки: в их полночь кладёт события полный набор троп. */
    private static final Integer TODAY = 0;

    /** Убыточный итог сделки: им собирается серия убытков. */
    private static final String LOSING_RESULT = "-40.000000000000000000";

    /**
     * Поднимает журнал аудита РЯДОМ: его строка лежит и доступна по сети.
     *
     * <p><b>Стои́т он до клеток, а не внутри своей</b>, потому что чужая база
     * общая всему классу, а порядок методов каркас теста не обещает;
     * заведение к тому же идемпотентно, и повторный прогон класса застаёт
     * строку на месте.
     */
    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        StatisticsSubstrate.registerOwn(registry, SLUG);
    }

    @BeforeAll
    static void givenJournalDatabaseBeside() {
        JOURNAL.write("create table if not exists " + JOURNAL_TABLE
                + " (event_id varchar(64) primary key, content text not null)");
        JOURNAL.write("insert into " + JOURNAL_TABLE + " (event_id, content) values (?, ?)"
                + " on conflict (event_id) do nothing", "E-JOURNAL", "{\"kept\": true}");
        Wire.createTopic(UNSUBSCRIBED);
    }

    @Test
    @DisplayName("B13.1 — Сервис не публикует ничего")
    void theServicePublishesNothing() {
        Map<String, Long> endsBefore = endOffsets();
        Set<String> topicsBefore = externalTopics();

        everyTrope("1");

        assertThat(endOffsets().get(topic()) - endsBefore.get(topic()))
                .as("в тему производителя прибавилось ровно то, что положил кейс: две "
                        + "штатные записи и отравленная")
                .isEqualTo(3L);
        assertThat(endOffsets().get(UNSUBSCRIBED))
                .as("в чужую тему не ушло ни одной записи")
                .isZero();
        assertThat(externalTopics())
                .as("тем сервис не заводит ни одной, и тема мёртвых писем в их числе")
                .isEqualTo(topicsBefore)
                .noneMatch(topic -> topic.endsWith(DEAD_LETTER_SUFFIX))
                .as("и своей темы под выход у него нет ни за одно событие прогона")
                .noneMatch(topic -> topic.startsWith(SERVICE_TOPIC_PREFIX));
        assertThat(rows.tableNames())
                .as("таблицы outbox в схеме нет ни одной колонкой: публиковать сервису нечем")
                .noneMatch(table -> table.contains("outbox"));
        assertThat(rows.count(DEAL_FACTS) + rows.count(INCIDENT_FACTS))
                .as("строк фактов ровно столько, сколько принято событий: события о "
                        + "событии не заводится")
                .isEqualTo(3L);
        assertThat(aggregates(DEAL_GRAIN, TENANT).dealRows())
                .as("проекция при этом СОБРАНА — иначе отрицания сошлись бы на сервисе, "
                        + "который вообще ничего не сделал")
                .isNotEmpty();
    }

    @Test
    @DisplayName("B13.2 — Сервис не зовёт соседей")
    void theServiceCallsNoNeighbours() {
        aggregates(DEAL_GRAIN, TENANT);
        Integer identityBefore = identity.paths().size();
        Set<String> topicsBefore = externalTopics();

        everyTrope("2");
        for (int probe = 0; probe < AUTHENTICATED_PROBES; probe++) {
            aggregates(DEAL_GRAIN, TENANT);
        }

        List<String> duringTropes =
                identity.paths().subList(identityBefore, identity.paths().size());
        assertThat(duringTropes)
                .as("за полный набор троп к провайдеру ушли только описание издателя и "
                        + "ключи: точек подтверждения токена сервис не зовёт вовсе")
                .allMatch(IDENTITY_PATHS::contains);
        assertThat(duringTropes.size())
                .as("и не на каждый запрос: под токеном сделано %s вызовов, а подпись "
                        + "проверяется локально", AUTHENTICATED_PROBES)
                .isLessThan(AUTHENTICATED_PROBES);
        assertThat(externalTopics())
                .as("исходящее к брокеру ограничено чтением: ни темы, ни её настройки "
                        + "сервис не заводит и не меняет")
                .isEqualTo(topicsBefore)
                .as("и своей темы у него нет ни за один такт прогона — клейм тут "
                        + "АБСОЛЮТЕН: тема, заведённая раньше окна клетки, равенству "
                        + "состава невидима")
                .noneMatch(topic -> topic.startsWith(SERVICE_TOPIC_PREFIX));
        assertThat(thresholdRowOf(topic()))
                .as("единственный исходящий вызов сервиса состоялся, и это спрос СРОКА "
                        + "ХРАНЕНИЯ у брокера: порог алерта выведен из добытого им числа")
                .isNotNull()
                .isPositive();
        assertThat(configuration())
                .as("исходящих ключей конфигурации соседей в дереве нет")
                .doesNotContain("neighbours", "base-url");
    }

    @Test
    @DisplayName("B13.3 — Входящей точки записи нет")
    void thereIsNoIncomingWritePoint() {
        givenReceptionStateRows();
        publish("E-B13-3", DEAL_CLOSED, momentsAgo(EVENT_AGE),
                Bodies.dealClosed(Facts.ACCOUNT, Facts.STRATEGY));
        awaitConsumed();
        Map<String, Long> countsBefore = subjectCounts();
        String versionBefore = pairVersion(topic());

        for (String path : WRITE_PATHS) {
            for (String method : WRITING_METHODS) {
                assertThat(call(method, path).status())
                        .as("записи не принимает %s %s: либо нет такого пути, либо метод "
                                + "не поддержан", method, path)
                        .isIn(404, 405);
            }
        }

        assertThat(subjectCounts())
                .as("ни одна строка ни одной таблицы не завелась вызовом снаружи")
                .isEqualTo(countsBefore);
        assertThat(pairVersion(topic()))
                .as("и ни одна не изменилась: версия строки состояния приёма неподвижна — "
                        + "обновление, кладущее то же значение, её сдвинуло бы")
                .isEqualTo(versionBefore);
        assertThat(dealFact().get("event_id"))
                .as("единственный писатель фактов — слушатель приёма")
                .isEqualTo("E-B13-3");
    }

    @Test
    @DisplayName("B13.4 — Журнал аудита сервис не читает")
    void theServiceDoesNotReadTheAuditJournal() {
        assertThat(JOURNAL.count(JOURNAL_TABLE))
                .as("журнал рядом поднят и доступен по сети: его строку читает то же "
                        + "соединение прогона — значит, читать сервису было ЧТО")
                .isEqualTo(1L);
        Long readsBefore = JOURNAL.readsOf(JOURNAL_TABLE);

        everyTrope("4");

        assertThat(rows.clientBackendsIn(JOURNAL_DATABASE))
                .as("клиентского подключения к чужой базе у процесса нет ни одного: пул "
                        + "держал бы своё открытым до конца жизни контекста")
                .isZero();
        assertThat(JOURNAL.readsOf(JOURNAL_TABLE))
                .as("и следа спроса к ней тоже нет: за полный набор троп журнальную "
                        + "таблицу не прочитали ни обходом, ни индексом")
                .isEqualTo(readsBefore);
        assertThat(JOURNAL.count(JOURNAL_TABLE))
                .as("строка журнала осталась на месте и нетронутой")
                .isEqualTo(1L);
        assertThat(sourceDeclarations())
                .as("второго источника данных конфигурация не объявляет: адрес базы в "
                        + "ней один")
                .isEqualTo(1L);
        assertThat(rows.tableNames())
                .as("журнальных таблиц и чужого торгового состояния в своей схеме нет")
                .doesNotContainAnyElementsOf(FOREIGN_TABLES);

        rows.write("delete from " + DEAL_FACTS);
        rows.write("delete from " + INCIDENT_FACTS);
        rows.write("delete from " + DEAL_AGGREGATES);
        rows.write("delete from " + INCIDENT_AGGREGATES);
        recompute();

        assertThat(rows.count(DEAL_AGGREGATES) + rows.count(INCIDENT_AGGREGATES))
                .as("источник пересчёта — только СВОИ факты: при пустых фактах проход не "
                        + "собирает ни строки, хотя запись журнала рядом лежит")
                .isZero();
    }

    @Test
    @DisplayName("B13.5 — Строк фактов наружу сервис не отдаёт")
    void theServiceHandsOutNoFactRows() {
        Facts.deal("E-B13-5-DEAL", TENANT, midnightDaysAgo(DAY));
        Facts.incident("E-B13-5-INCIDENT", TENANT, DEAL_OPENED, midnightDaysAgo(DAY));
        givenReceptionStateRows();
        recompute();

        for (String path : FACT_PATHS) {
            assertThat(get(path, TENANT).status())
                    .as("точки, отдающей строку факта, нет: %s", path)
                    .isEqualTo(404);
        }

        rows.resetStatementCounters();
        Answer dealPage = aggregates(DEAL_GRAIN, TENANT);
        Answer incidentPage = aggregates(INCIDENT_GRAIN, TENANT);

        assertThat(rows.statementCalls("%deal_facts%"))
                .as("чтение страницы к таблице фактов не ходит ни одним запросом: "
                        + "поверхность отдаёт агрегаты")
                .isZero();
        assertThat(dealPage.status()).isEqualTo(200);
        assertThat(dealPage.asObject().keySet())
                .as("выдача несёт только свои пять полей, и поля со строками фактов "
                        + "среди них нет")
                .isSubsetOf(PAGE_FIELDS);
        assertThat(dealPage.completeness().keySet())
                .as("полнота — только граница и предикат: ни сумм, ни счётов")
                .isSubsetOf(COMPLETENESS_FIELDS);
        assertThat(dealPage.dealRows())
                .hasSize(1)
                .allSatisfy(row -> assertThat(row.keySet())
                        .as("строка выдачи — свёртка суток, а не факт: ни идентичности "
                                + "события, ни момента терминала в ней нет")
                        .isSubsetOf(DEAL_ROW_FIELDS));
        assertThat(incidentPage.incidentRows())
                .hasSize(1)
                .allSatisfy(row -> assertThat(row.keySet()).isSubsetOf(INCIDENT_ROW_FIELDS));

        recompute();

        assertThat(rows.statementCalls("%deal_facts%"))
                .as("читатель фактов один — пересчёт того же сервиса: его проход к "
                        + "таблице ходит, и он единственный, кто это сделал")
                .isPositive();
    }

    @Test
    @DisplayName("B13.6 — Содержимого события сервис не толкует и величин по нему не изобретает")
    void theServiceNeitherInterpretsContentNorDerivesFiguresFromIt() {
        givenReceptionStateRows();
        publish("E-B13-6-DEAL", DEAL_CLOSED, midnightDaysAgo(DAY),
                Bodies.dealClosedRichDocument(Facts.ACCOUNT));
        publish("E-B13-6-INCIDENT", DEAL_OPENED, midnightDaysAgo(DAY),
                Bodies.incident(Facts.ACCOUNT));
        awaitConsumed();
        recompute();
        Answer dealPage = aggregates(DEAL_GRAIN, TENANT);

        assertThat(rows.tableNames())
                .as("таблиц под выведенные из содержимого величины нет ни одной: состав "
                        + "схемы равен предмету плюс журнал миграций")
                .containsAll(SUBJECT_TABLES)
                .hasSize(SUBJECT_TABLES.size() + 1);
        Map<String, Object> fact = dealFact();
        assertThat(rows.columnNames(DEAL_FACTS))
                .as("и колонки под неразложенное поле у таблицы фактов тоже нет ни одной: "
                        + "перечень закрыт, и семнадцатая колонка предъявится падением")
                .containsExactlyInAnyOrderElementsOf(DEAL_FACT_COLUMNS);
        UNRESOLVED_FIELDS.forEach(field -> assertThat(fact.values().stream()
                .map(String::valueOf))
                .as("неразложенное поле %s не сохранено ни в одной колонке", field)
                .noneMatch(value -> value.contains(field)));

        assertThat(dealPage.dealRows())
                .hasSize(1)
                .allSatisfy(row -> assertThat(row.keySet())
                        .as("в строке агрегата нет ни одной величины, операнда которой не "
                                + "несёт факт: перечень её полей закрыт")
                        .isSubsetOf(DEAL_ROW_FIELDS));
        assertThat(stateWordsAmong(DEAL_ROW_FIELDS))
                .as("экспозиции, живого риска и плеча среди величин выдачи нет: это "
                        + "состояние, а не происшествие, и читается оно у владельца")
                .isEmpty();
        assertThat(stateWordsAmong(rows.columnNames(DEAL_AGGREGATES)))
                .as("их нет и в колонках проекции")
                .isEmpty();
        assertThat(stateWordsAmong(rows.columnNames(INCIDENT_AGGREGATES))).isEmpty();
    }

    @Test
    @DisplayName("B13.7 — Агрегат событием не двигается")
    void theAggregateIsNotMovedByAnEvent() {
        givenReceptionStateRows();
        publish("E-B13-7-FIRST", DEAL_CLOSED, midnightDaysAgo(DAY),
                Bodies.dealClosed(Facts.ACCOUNT, Facts.STRATEGY));
        awaitConsumed();
        recompute();
        Map<String, Object> assembled = onlyDealRow();

        for (int event = 0; event < LATE_EVENTS; event++) {
            publish("E-B13-7-LATE-" + event, DEAL_CLOSED, midnightDaysAgo(DAY),
                    Bodies.dealClosed(Facts.ACCOUNT, Facts.STRATEGY));
        }
        publish("E-B13-7-FIRST", DEAL_CLOSED, midnightDaysAgo(DAY),
                Bodies.dealClosed(Facts.ACCOUNT, Facts.STRATEGY));
        awaitDealFactCount(LATE_EVENTS + 1L);

        assertThat(onlyDealRow())
                .as("числа строки не изменились ни на единицу, и момент её сборки тоже: "
                        + "накопителя, который двигало бы событие, не существует")
                .isEqualTo(assembled);
        assertThat(assembled.get(CLOSED_DEALS))
                .as("собрана она была по ОДНОМУ факту — иначе равенство сошлось бы на "
                        + "строке, которой события и не касались")
                .isEqualTo(1);
        assertThat(rows.count(DEAL_FACTS))
                .as("факты при этом легли все, а повтор поглощён ключом события: "
                        + "безразличие проекции не в том, что приём встал")
                .isEqualTo(LATE_EVENTS + 1L);

        recompute();

        assertThat(onlyDealRow().get(CLOSED_DEALS))
                .as("двигает её только ПРОХОД, и он пересобирает сутки целиком")
                .isEqualTo(LATE_EVENTS + 1);
    }

    @Test
    @DisplayName("B13.8 — Числа статистики ни на что не влияют")
    void theStatisticsFiguresDriveNothing() {
        givenReceptionStateRows();
        for (int day = 1; day <= 3; day++) {
            DealDraft.of("E-B13-8-LOSS-" + day, TENANT, midnightDaysAgo(day))
                    .netResult(LOSING_RESULT)
                    .build()
                    .put();
        }
        recompute();
        aggregates(DEAL_GRAIN, TENANT);
        Integer identityBefore = identity.paths().size();
        Map<String, Long> endsBefore = endOffsets();
        Set<String> topicsBefore = externalTopics();

        tick();
        recompute();
        Answer page = aggregates(DEAL_GRAIN, TENANT);

        assertThat(page.dealRows())
                .as("серия убытков собрана и видна: отрицания ниже стоя́т на числах, а "
                        + "не на их отсутствии")
                .hasSize(3)
                .allSatisfy(row -> assertThat(row.get("losingDeals")).isEqualTo(1));
        assertThat(identity.paths().size())
                .as("наружу не ушло ни одного вызова: исходящего вызова у проходов нет "
                        + "вовсе")
                .isEqualTo(identityBefore);
        assertThat(endOffsets())
                .as("и ни одного события: концы обеих тем не сдвинулись")
                .isEqualTo(endsBefore);
        assertThat(externalTopics())
                .as("ступени защиты сервис не поднимает и командой её не просит: тем он "
                        + "не заводит")
                .isEqualTo(topicsBefore)
                .as("и своей темы у него нет ни за один такт прогона — тот же довод "
                        + "абсолютного клейма, что и у соседних клеток")
                .noneMatch(topic -> topic.startsWith(SERVICE_TOPIC_PREFIX));
        assertThat(rows.tableNames())
                .as("и строк о холдах, сделках и заявках не появляется: таблиц под них нет")
                .doesNotContainAnyElementsOf(FOREIGN_TABLES);
        assertThat(aggregates(DEAL_GRAIN, TENANT).status())
                .as("поверхность после серии убытков отвечает тем же: runtime-решения по "
                        + "этим числам не принимает ни один его компонент")
                .isEqualTo(200);
    }

    @Test
    @DisplayName("B13.9 — Второй durable-группой сервис не принимает")
    void theServiceJoinsNoSecondDurableGroup() {
        everyTrope("9");

        Set<String> positioned = Wire.consumerGroups().stream()
                .filter(group -> Objects.nonNull(Wire.committedOffset(group, topic())))
                .collect(Collectors.toSet());
        assertThat(positioned)
                .as("позиция чтения на теме фактов есть ровно у ОДНОЙ группы, и это своя: "
                        + "вторая, читающая ту же тему, получила бы часть темы себе")
                .containsExactly(consumerGroup());
        assertThat(pairs())
                .as("строк состояния заводится столько, сколько тем у этой группы")
                .hasSize(subscription().size());
        assertThat(pairs().stream().map(pair -> pair.get(GROUP_COLUMN)).distinct().toList())
                .as("и все они — с её именем: строк с чужим именем группы он не заводит")
                .containsExactly(consumerGroup());
        FOREIGN_GROUPS.forEach(group -> assertThat(Wire.committedOffset(group, topic()))
                .as("смещений группы %s он не двигает: её позиции на теме нет вовсе", group)
                .isNull());
    }

    /**
     * Полный набор троп сервиса: приём обоих зёрен, отравленное сообщение,
     * такт тика, проход пересчёта, чтение страницы и отказ доступа.
     *
     * <p><b>Он один на четыре клетки, и это не экономия:</b> предмет каждой —
     * отсутствие выхода ПРИ ЛЮБОМ входе, и разный набор троп у соседних клеток
     * сделал бы их утверждения о разном.
     *
     * <p><b>События кладутся в ПОЛНОЧЬ нынешних суток, а не пятью минутами
     * ранее.</b> Проход не пишет суток, начавшихся раньше первого факта ряда
     * (docs/spec/statistics-aggregates.json, {@code dayRecomputable}), и факт в
     * середине суток оставил бы строку проекции несобранной вовсе — то есть
     * половина «проекция собрана» у клетки о публикациях краснела бы по охране
     * отбора, а не по своему предмету.
     *
     * @param label метка клетки: из неё строятся идентичности её событий
     */
    private void everyTrope(String label) {
        givenReceptionStateRows();
        publish("E-B13-" + label + "-DEAL", DEAL_CLOSED, midnightDaysAgo(TODAY),
                Bodies.dealClosed(Facts.ACCOUNT, Facts.STRATEGY));
        publish("E-B13-" + label + "-INCIDENT", DEAL_OPENED, midnightDaysAgo(TODAY),
                Bodies.incident(Facts.ACCOUNT));
        awaitConsumed();
        poisonAndHeal(label);
        tick();
        recompute();
        aggregates(DEAL_GRAIN, TENANT);
        aggregates(INCIDENT_GRAIN, TENANT);
        getAnonymously(AGGREGATE_ROWS);
    }

    /**
     * Проводит сообщение по тропе ОТКАЗА и возвращает приём к жизни.
     *
     * <p>Отнятая таблица сделочных фактов роняет обработку, обработчик отказа
     * ставит флаг остановки и уходит в повтор; возвращённая таблица даёт
     * повтору состояться, и слушатель освобождает свой единственный поток.
     *
     * @param label метка клетки
     */
    private void poisonAndHeal(String label) {
        rows.withoutTable(DEAL_FACTS, () -> {
            publish("E-B13-" + label + "-POISON", DEAL_CLOSED, midnightDaysAgo(TODAY),
                    Bodies.dealClosed(Facts.ACCOUNT, Facts.STRATEGY));
            awaitHalted(topic());
        });
        awaitDealFactCount(2L);
    }

    /** Единственная строка сделочного агрегата выдачи; иное число — падение. */
    private Map<String, Object> onlyDealRow() {
        List<Map<String, Object>> handed = aggregates(DEAL_GRAIN, TENANT).dealRows();
        if (handed.size() != 1) {
            throw new AssertionError("Ожидалась ровно одна строка агрегата, их " + handed.size());
        }
        return handed.getFirst();
    }

    /** Концы двух тем брокера: своей и чужой. */
    private Map<String, Long> endOffsets() {
        Map<String, Long> ends = new LinkedHashMap<>();
        ends.put(topic(), Wire.endOffset(topic()));
        ends.put(UNSUBSCRIBED, Wire.endOffset(UNSUBSCRIBED));
        return ends;
    }

    /** Темы брокера за вычетом его собственных внутренних. */
    private Set<String> externalTopics() {
        return Wire.topicNames().stream()
                .filter(topic -> isFalse(topic.startsWith(INTERNAL_PREFIX)))
                .collect(Collectors.toSet());
    }

    /** Число строк в каждой таблице предмета. */
    private Map<String, Long> subjectCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        SUBJECT_TABLES.forEach(table -> counts.put(table, rows.count(table)));
        return counts;
    }

    /**
     * Слова состояния торговли, встреченные среди названных имён.
     *
     * <p>Сравнение идёт по нижнему регистру: поле выдачи пишется горбатым
     * именем, колонка — подчёркиваниями, и образец, зависящий от написания,
     * мерил бы его, а не наличие величины.
     *
     * @param names имена полей либо колонок
     */
    private static List<String> stateWordsAmong(Iterable<String> names) {
        List<String> found = new ArrayList<>();
        names.forEach(name -> STATE_WORDS.stream()
                .filter(word -> name.toLowerCase(Locale.ROOT).contains(word))
                .forEach(word -> found.add(name + " → " + word)));
        return found;
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
     * {@code @DynamicPropertySource} группы {@code B12}, — а не как его
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
