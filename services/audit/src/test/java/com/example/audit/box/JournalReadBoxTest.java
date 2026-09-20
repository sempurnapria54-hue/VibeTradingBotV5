package com.example.audit.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.json.JsonParserFactory;

/**
 * Клетки {@code B8.1}-{@code B8.15} и {@code B8.17} — журнальная выборка
 * чтения на штатном положении осей
 * (.claude/tests/cases/audit.md §«B8 — Журнальная выборка чтения»).
 *
 * <p><b>Класс равен КОНФИГУРАЦИИ КОНТЕКСТА</b>, как у четырёх соседних
 * групп: шестнадцать клеток берут штатные оси у {@link SharedAuditBox} и
 * расходятся только состоянием журнала, которое каждая ставит себе сама.
 * Семнадцатая клетка группы ({@code B8.16}) живёт своим классом, потому что
 * входом ей служит САМА ось — предел окна и размер страницы.
 *
 * <p><b>У этой группы поверхность впервые ПОДАЁТ ВХОД, а не только
 * наблюдает выход.</b> Вход предмета по-прежнему запись брокера — строки
 * журнала кладёт приём, — но ВОПРОС читателя есть операнд вызова, и
 * отвержения вопроса наблюдаются только через поверхность
 * (.claude/tests/cases/audit.md §«Новая ось формы — событие как ВХОД»).
 *
 * <p><b>Размер страницы сдвинут осью субстрата</b>
 * ({@link AuditSubstrate#PAGE_SIZE}), и без этого сдвига клетки о курсорной
 * странице проводили бы через брокер две сотни записей — то есть мерили бы
 * пропускную способность субстрата, а не продолжение чтения. Предел окна
 * той же осью объявлен, но не сдвинут: неделя прогону ничего не стои́т.
 *
 * <p><b>Ожидание отвергнутого вопроса берёт ПАРУ «класс плюс названный
 * домом повод», а не HTTP-число.</b> Число ставит наш собственный код в
 * ветви обработчика, и ход выравнивания кодов по платформе сделал бы
 * пиньнутое число красным на исправной системе; число прогон пишет в
 * «Факт» (.claude/tests/cases/audit.md §«Число ответа и класс отказа —
 * разные ожидания»). Числом пиньнуты ровно два исхода: {@code 200} —
 * контракт успеха самой точки — и {@code 400} контейнера, чей статус дом
 * называет наследуемым.
 *
 * <p><b>Окно строится ОТ ПРАВОЙ границы, а не от «сейчас» минус ширина.</b>
 * Левая граница, снятая раньше правой, даёт окно шире названного на время
 * между двумя вызовами часов — и клетка о самой границе предела краснела бы
 * от собственной расстановки, а не от предмета.
 */
class JournalReadBoxTest extends SharedAuditBox {

    /** Тема первого производителя: в неё ходят все клетки класса. */
    private static final String CORE = AuditSubstrate.CORE_TOPIC;

    /** Окно клеток, которым его ширина безразлична. */
    private static final Duration WINDOW = Duration.ofHours(1);

    /** Имя поля идентичности события в строке выдачи. */
    private static final String EVENT_ID_FIELD = "eventId";

    /** Имя поля класса события. */
    private static final String TYPE_FIELD = "eventType";

    /** Имя поля момента происшествия. */
    private static final String OCCURRED_FIELD = "occurredAt";

    /** Имя поля момента приёма. */
    private static final String RECORDED_FIELD = "recordedAt";

    /** Имя поля версии формы содержимого. */
    private static final String VERSION_FIELD = "version";

    /** Имя поля контекста трассировки. */
    private static final String TRACE_FIELD = "traceContext";

    /** Имя поля радиуса: биржевой счёт. */
    private static final String ACCOUNT_FIELD = "exchangeAccountInternalId";

    /** Имя поля радиуса: инструмент. */
    private static final String INSTRUMENT_FIELD = "instrumentInternalId";

    /** Имя поля радиуса: сделка. */
    private static final String DEAL_FIELD = "dealInternalId";

    /** Имя поля радиуса: определение стратегии. */
    private static final String STRATEGY_FIELD = "strategyInternalId";

    /** Имя поля содержимого события. */
    private static final String CONTENT_FIELD = "content";

    /** Имя первой половины курсора в вопросе читателя. */
    private static final String CURSOR_MOMENT = "cursorOccurredAt";

    /** Имя второй половины курсора в вопросе читателя. */
    private static final String CURSOR_ID = "cursorEventId";

    /** Имя левой границы окна в вопросе читателя. */
    private static final String FROM = "from";

    /** Имя правой границы окна в вопросе читателя. */
    private static final String TO = "to";

    /** Имя величины нижней границы полноты. */
    private static final String BOUND_FIELD = "lowerBound";

    /** Имя предиката непрерывности. */
    private static final String CLAIMABLE_FIELD = "continuityClaimable";

    /** Имя поля класса отказа в едином error-DTO. */
    private static final String CODE_FIELD = "code";

    /** Имя поля пояснения в том же DTO. */
    private static final String MESSAGE_FIELD = "message";

    /** Имя перечня строк в теле страницы: его отсутствие читают отказы. */
    private static final String RECORDS_FIELD = "records";

    /** Класс отказа нашего кода: вопрос чтения не принят. */
    private static final String QUERY_REJECTED = "QUERY_NOT_ACCEPTED";

    /** Класс отказа контейнера: не принята сама форма вызова. */
    private static final String REQUEST_REJECTED = "REQUEST_NOT_ACCEPTED";

    /** Значение радиуса: биржевой счёт. */
    private static final String ACCOUNT = "EA-1";

    /** Значение радиуса: инструмент. */
    private static final String INSTRUMENT = "IN-1";

    /** Значение радиуса: сделка. */
    private static final String DEAL = "D-1";

    /** Значение радиуса: определение стратегии. */
    private static final String STRATEGY = "S-1";

    @Test
    @DisplayName("B8.1 — Штатная страница: строки от новых к старым и полнота рядом с ними")
    void aPageCarriesRowsNewestFirstAndCompletenessBesideThem() {
        givenReceptionStateRows();
        given("E-OLD", Duration.ofMinutes(30), fullRadius());
        given("E-MIDDLE", Duration.ofMinutes(20), fullRadius());
        given("E-NEW", Duration.ofMinutes(10), fullRadius());
        awaitAccepted(3L);

        Answer page = page();

        assertThat(page.status()).as("контракт успеха объявлен самой точкой").isEqualTo(200);
        assertThat(ids(page))
                .as("строки идут от новых к старым по моменту происшествия")
                .containsExactly("E-NEW", "E-MIDDLE", "E-OLD");
        Map<String, Object> row = page.records().getFirst();
        assertThat(row)
                .as("у строки есть идентичность, класс, оба момента, версия, четыре радиуса и содержимое")
                .containsKeys(EVENT_ID_FIELD, TYPE_FIELD, OCCURRED_FIELD, RECORDED_FIELD,
                        VERSION_FIELD, TRACE_FIELD, ACCOUNT_FIELD, INSTRUMENT_FIELD,
                        DEAL_FIELD, STRATEGY_FIELD, CONTENT_FIELD);
        assertThat(row.values()).as("и ни одно из них не пусто").doesNotContainNull();
        assertThat(page.completeness())
                .as("полнота лежит в ТОМ ЖЕ ответе, что и строки")
                .containsKeys(BOUND_FIELD, CLAIMABLE_FIELD);
        assertThat(page.completeness().get(BOUND_FIELD))
                .as("и она посчитана, а не объявлена пустой").isNotNull();
        assertThat(call("GET", "/api/v1/audit/journal/completeness").status())
                .as("отдельной точки чтения полноты у сервиса нет вовсе")
                .isEqualTo(404);
    }

    @Test
    @DisplayName("B8.2 — Окна нет: вопрос не принят")
    void aQuestionWithoutAWindowIsNotAccepted() {
        given("E-WINDOWLESS", Duration.ofMinutes(10), Bodies.reference());
        awaitAccepted(1L);
        OffsetDateTime to = now();

        Answer withoutBoth = get(JOURNAL_RECORDS, TENANT);
        Answer leftOnly = get(JOURNAL_RECORDS + "?" + FROM + "=" + moment(to.minus(WINDOW)), TENANT);
        Answer rightOnly = get(JOURNAL_RECORDS + "?" + TO + "=" + moment(to), TENANT);

        for (Answer answer : List.of(withoutBoth, leftOnly, rightOnly)) {
            assertThat(answer.asObject().get(CODE_FIELD))
                    .as("у всех трёх один класс отказа: вопрос не принят")
                    .isEqualTo(QUERY_REJECTED);
            assertThat(String.valueOf(answer.asObject().get(MESSAGE_FIELD)))
                    .as("текст называет обязательность окна")
                    .contains("Окно").contains("обязательно");
            assertThat(answer.asObject())
                    .as("строк не отдано ни одной: молчаливой подстановки окна нет")
                    .doesNotContainKey(RECORDS_FIELD);
        }
        assertThat(rows.count(JOURNAL_TABLE))
                .as("вход поставлен: строка, которую отдало бы окно по умолчанию, в журнале лежит")
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("B8.3 — Окно перевёрнуто: вопрос не принят")
    void aReversedWindowIsNotAccepted() {
        given("E-REVERSED", Duration.ofMinutes(10), Bodies.reference());
        awaitAccepted(1L);
        OffsetDateTime to = now();

        Answer reversed = pageOf(to, to.minus(WINDOW));

        assertThat(reversed.asObject().get(CODE_FIELD))
                .as("класс отказа тот же: вопрос не принят").isEqualTo(QUERY_REJECTED);
        assertThat(String.valueOf(reversed.asObject().get(MESSAGE_FIELD)))
                .as("а текст называет ИНОЙ повод — порядок границ")
                .contains("раньше левой");
        assertThat(reversed.asObject())
                .as("границы не переставляются местами молча: строк не отдано")
                .doesNotContainKey(RECORDS_FIELD);
    }

    @Test
    @DisplayName("B8.4 — Окно шире предела отвергается, а не сужается")
    void aWindowWiderThanTheLimitIsRejectedRatherThanNarrowed() {
        given("E-IN-LIMIT", Duration.ofMinutes(10), Bodies.reference());
        awaitAccepted(1L);
        OffsetDateTime to = now();

        Answer wider = pageOf(to.minus(AuditSubstrate.MAX_WINDOW).minusSeconds(1), to);
        Answer exact = pageOf(to.minus(AuditSubstrate.MAX_WINDOW), to);

        assertThat(wider.asObject().get(CODE_FIELD))
                .as("окно шире предела на секунду вопросом не принято").isEqualTo(QUERY_REJECTED);
        assertThat(String.valueOf(wider.asObject().get(MESSAGE_FIELD)))
                .as("текст называет сам предел и говорит, что окно не сужается молча")
                .contains(String.valueOf(AuditSubstrate.MAX_WINDOW))
                .contains("не сужается молча");
        assertThat(wider.asObject())
                .as("урезанной страницы под видом запрошенной нет").doesNotContainKey(RECORDS_FIELD);
        assertThat(exact.status())
                .as("окно РОВНО в предел принимается: предел есть наибольшее допустимое")
                .isEqualTo(200);
        assertThat(ids(exact)).as("и отдаёт строку окна").containsExactly("E-IN-LIMIT");
    }

    @Test
    @DisplayName("B8.5 — Курсор назван наполовину: вопрос не принят")
    void aHalfNamedCursorIsNotAccepted() {
        given("E-CURSOR", Duration.ofMinutes(10), Bodies.reference());
        awaitAccepted(1L);
        OffsetDateTime to = now();

        Answer momentOnly = pageOf(to.minus(WINDOW), to, CURSOR_MOMENT, iso(to));
        Answer identityOnly = pageOf(to.minus(WINDOW), to, CURSOR_ID, "E-CURSOR");
        Answer neither = pageOf(to.minus(WINDOW), to);

        for (Answer half : List.of(momentOnly, identityOnly)) {
            assertThat(half.asObject().get(CODE_FIELD))
                    .as("половина курсора позиции не определяет").isEqualTo(QUERY_REJECTED);
            assertThat(String.valueOf(half.asObject().get(MESSAGE_FIELD)))
                    .as("текст называет ПАРУ").contains("парой");
        }
        assertThat(neither.status())
                .as("а вопрос БЕЗ обеих половин принят: это первая страница окна")
                .isEqualTo(200);
        assertThat(ids(neither)).containsExactly("E-CURSOR");
    }

    @Test
    @DisplayName("B8.6 — Курсор продолжает чтение без пропусков и дублей при вставке между запросами")
    void theCursorContinuesWithoutGapsOrDuplicatesWhenRowsArriveBetweenRequests() {
        givenFiveRows();
        OffsetDateTime to = now();

        Answer first = pageOf(to.minus(WINDOW), to);
        assertThat(ids(first))
                .as("первая страница — ровно размер страницы, от новых к старым; "
                        + "строки одного момента разведены идентичностью")
                .containsExactly("E-1", "E-2", "E-4");
        assertThat(first.nextCursor())
                .as("позиция продолжения равна паре ПОСЛЕДНЕЙ отданной строки")
                .containsEntry(EVENT_ID_FIELD, "E-4")
                .containsEntry(OCCURRED_FIELD, first.records().getLast().get(OCCURRED_FIELD));

        // Вставка между запросами: события приезжают внутрь того же окна и
        // ложатся ВЫШЕ курсора по моменту происшествия.
        given("E-FRESH-A", Duration.ofMinutes(2), Bodies.reference());
        given("E-FRESH-B", Duration.ofMinutes(1), Bodies.reference());
        awaitAccepted(7L);

        Answer second = pageOf(to.minus(WINDOW), now(), CURSOR_MOMENT,
                String.valueOf(first.nextCursor().get(OCCURRED_FIELD)),
                CURSOR_ID, String.valueOf(first.nextCursor().get(EVENT_ID_FIELD)));

        assertThat(ids(second))
                .as("вторая страница не повторяет строк первой и не пропускает лежавших — "
                        + "включая соседку по моменту, которую курсор из одного момента потерял бы")
                .containsExactly("E-3", "E-5");
        assertThat(ids(second))
                .as("вновь принятые строки во вторую страницу не влезают: курсор идёт вниз")
                .doesNotContain("E-FRESH-A", "E-FRESH-B");
        assertThat(second.nextCursor()).as("окно дочитано").isNull();
    }

    @Test
    @DisplayName("B8.7 — Окно дочитано: позиции продолжения нет")
    void anExhaustedWindowCarriesNoContinuation() {
        givenReceptionStateRows();
        given("E-A", Duration.ofMinutes(20), Bodies.reference());
        given("E-B", Duration.ofMinutes(10), Bodies.reference());
        awaitAccepted(2L);

        Answer page = page();

        assertThat(ids(page)).as("отданы все строки окна").containsExactly("E-B", "E-A");
        assertThat(page.nextCursor())
                .as("позиции продолжения нет, и пустота означает «окно дочитано»")
                .isNull();
        assertThat(page.completeness().get(BOUND_FIELD))
                .as("полнота при этом едет как обычно").isNotNull();
    }

    @Test
    @DisplayName("B8.8 — Курсор не зависит от набора отборов")
    void theCursorDoesNotDependOnTheSetOfFilters() {
        givenFiveRows();
        OffsetDateTime to = now();

        Answer plainFirst = pageOf(to.minus(WINDOW), to);
        Answer plainSecond = pageOf(to.minus(WINDOW), to, CURSOR_MOMENT,
                String.valueOf(plainFirst.nextCursor().get(OCCURRED_FIELD)),
                CURSOR_ID, String.valueOf(plainFirst.nextCursor().get(EVENT_ID_FIELD)));
        Answer filtered = pageOf(to.minus(WINDOW), to, DEAL_FIELD, DEAL);
        Answer filteredFromPlainCursor = pageOf(to.minus(WINDOW), to, DEAL_FIELD, DEAL,
                CURSOR_MOMENT, String.valueOf(plainFirst.nextCursor().get(OCCURRED_FIELD)),
                CURSOR_ID, String.valueOf(plainFirst.nextCursor().get(EVENT_ID_FIELD)));

        assertThat(ids(plainFirst)).containsExactly("E-1", "E-2", "E-4");
        assertThat(ids(plainSecond)).containsExactly("E-3", "E-5");
        assertThat(ids(plainFirst))
                .as("обход без отборов полон и строк не задваивает")
                .doesNotContainAnyElementsOf(ids(plainSecond));
        assertThat(ids(filtered))
                .as("обход с отбором полон на своём подмножестве и тоже не задваивает")
                .containsExactly("E-1", "E-3", "E-5");
        assertThat(ids(filteredFromPlainCursor))
                .as("курсор ОДНОГО обхода на другом смысла не меняет: та же пара, то же продолжение")
                .containsExactly("E-3", "E-5");
    }

    @Test
    @DisplayName("B8.9 — Четыре отбора по радиусам конъюнктивны и необязательны")
    void theFourRadiusFiltersAreConjunctiveAndOptional() {
        given("E-ALL", Duration.ofMinutes(30), fullRadius());
        given("E-DEAL-ONLY", Duration.ofMinutes(20), Bodies.withDeal(DEAL));
        given("E-NONE", Duration.ofMinutes(10), Bodies.reference());
        awaitAccepted(3L);
        OffsetDateTime to = now();

        assertThat(ids(pageOf(to.minus(WINDOW), to)))
                .as("без отборов отданы все строки окна")
                .containsExactly("E-NONE", "E-DEAL-ONLY", "E-ALL");
        assertThat(ids(pageOf(to.minus(WINDOW), to, DEAL_FIELD, DEAL)))
                .as("с одним отбором — только несущие это значение")
                .containsExactly("E-DEAL-ONLY", "E-ALL");
        assertThat(ids(pageOf(to.minus(WINDOW), to, DEAL_FIELD, DEAL, STRATEGY_FIELD, STRATEGY)))
                .as("с двумя — только несущие ОБА: строка с одним из них выпадает")
                .containsExactly("E-ALL");
        assertThat(ids(pageOf(to.minus(WINDOW), to, ACCOUNT_FIELD, ACCOUNT,
                INSTRUMENT_FIELD, INSTRUMENT, DEAL_FIELD, DEAL, STRATEGY_FIELD, STRATEGY)))
                .as("со всеми четырьмя — только несущая все четыре")
                .containsExactly("E-ALL");
        assertThat(ids(pageOf(to.minus(WINDOW), to, STRATEGY_FIELD, STRATEGY)))
                .as("отборы друг друга не требуют: второй работает и в одиночку")
                .containsExactly("E-ALL");
        assertThat(get(JOURNAL_RECORDS + "?" + DEAL_FIELD + "=" + DEAL, TENANT)
                .asObject().get(CODE_FIELD))
                .as("окна отбор не заменяет: вопрос без окна не принят и с отбором")
                .isEqualTo(QUERY_REJECTED);
    }

    /**
     * <b>Клетка КРАСНАЯ, и её краснота есть предъявление названного
     * долга</b> (находка {@code F-8}, .claude/work/backlog.md §«Пустое
     * значение отбора журнальной выборки даёт пустую страницу молча»).
     *
     * <p>Первая половина ожидания исполнена: отбор отдаёт строки СО
     * значением. Вторая — нет. Пустое значение операнда отбора уезжает в
     * выборку как значение, и сравнение равенством не берёт ни одной
     * строки: колонка радиуса либо пуста, либо непуста, и пустой строкой не
     * бывает никогда. Читатель, оставивший поле незаполненным, получает
     * пустую страницу при непустом журнале — то есть ответ «за этот период
     * ничего не происходило» на вопрос, которого он не задавал
     * (docs/concept.md, П1). Ветвь эта домом не объявлена вовсе — тот же
     * класс, что у {@code F-4}.
     *
     * <p><b>Ожидание под текущий факт не ослаблено</b>
     * (.claude/tests/cases/audit.md §«Ожидание берётся из дома, даже когда
     * сегодня оно не исполнено»), а клетка помечена {@code debt} и в
     * умолчание прогона не входит: зелёная половина её ожидания накрыта
     * соседней клеткой {@code B8.9} целиком, и потери покрытия пометка не
     * даёт.
     */
    @Test
    @Tag("debt")
    @DisplayName("B8.10 — Отбор выбирает строки СО значением, а не строки без него")
    void aFilterPicksRowsThatCarryTheValueRatherThanRowsWithoutIt() {
        given("E-WITH-DEAL", Duration.ofMinutes(20), Bodies.withDeal(DEAL));
        given("E-WITHOUT-DEAL", Duration.ofMinutes(10), Bodies.reference());
        awaitAccepted(2L);
        OffsetDateTime to = now();

        assertThat(ids(pageOf(to.minus(WINDOW), to, DEAL_FIELD, DEAL)))
                .as("отбор отдаёт строки СО значением")
                .containsExactly("E-WITH-DEAL");
        assertThat(page().records().getFirst().get(DEAL_FIELD))
                .as("вход поставлен: у второй строки радиуса нет вовсе")
                .isNull();
        assertThat(ids(pageOf(to.minus(WINDOW), to, DEAL_FIELD, "")))
                .as("обратное этим входом не выражается: пустое значение читается как «отбор не задан»")
                .containsExactly("E-WITHOUT-DEAL", "E-WITH-DEAL");
    }

    @Test
    @DisplayName("B8.11 — Чужой тенант не виден")
    void rowsOfAnotherTenantAreNotVisible() {
        given("E-T1-NEW", Duration.ofMinutes(10), Bodies.withDeal(DEAL));
        givenOf(SECOND_TENANT, "E-T2", Duration.ofMinutes(20), Bodies.withDeal("D-T2"));
        given("E-T1-OLD", Duration.ofMinutes(30), Bodies.reference());
        awaitAccepted(3L);
        OffsetDateTime to = now();

        Answer page = pageOf(to.minus(WINDOW), to);

        assertThat(ids(page)).as("отданы только строки своего тенанта")
                .containsExactly("E-T1-NEW", "E-T1-OLD");
        assertThat(page.body()).as("ни одной строки соседа — ни в теле, ни в счёте")
                .doesNotContain("E-T2").doesNotContain(SECOND_TENANT);
        OffsetDateTime foreignMoment = (OffsetDateTime) rows
                .row(JOURNAL_TABLE, "event_id", "E-T2").get(OCCURRED_COLUMN);
        assertThat(ids(pageOf(to.minus(WINDOW), to,
                CURSOR_MOMENT, iso(foreignMoment), CURSOR_ID, "E-T2")))
                .as("идентичность соседа, поданная курсором, чужих строк не открывает")
                .containsExactly("E-T1-OLD");
        assertThat(ids(pageOf(to.minus(WINDOW), to, DEAL_FIELD, "D-T2")))
                .as("отбор по радиусу соседа отдаёт пустую страницу, а не его строки")
                .isEmpty();
    }

    @Test
    @DisplayName("B8.12 — Тенант обязателен, а доверие к нему здесь не добывается")
    void theTenantIsMandatoryAndItsTrustIsNotEstablishedHere() {
        given("E-TENANT", Duration.ofMinutes(10), Bodies.reference());
        awaitAccepted(1L);
        OffsetDateTime to = now();
        String path = journalPath(to.minus(WINDOW), to);

        Answer missing = getWithoutTenant(path);
        Answer blank = get(path, "");
        Answer unknown = get(path, "T-UNKNOWN");

        for (Answer answer : List.of(missing, blank)) {
            assertThat(answer.status())
                    .as("статус контейнера наследуется, и дом называет его зафиксированным")
                    .isEqualTo(400);
            assertThat(answer.asObject().get(CODE_FIELD))
                    .as("класс ИНОЙ: форму вызова отвергает контейнер, а не наш вопрос чтения")
                    .isEqualTo(REQUEST_REJECTED);
        }
        assertThat(unknown.status())
                .as("незнакомый тенант — не отказ: значение отбирает строки, а не сверяется")
                .isEqualTo(200);
        assertThat(unknown.records()).as("и отбирает ровно ничего").isEmpty();
        // Соседей у ящика не поднято ни одного, и адреса владельца членств
        // у сервиса нет: сверка не могла бы пройти молча — она отказала бы.
        // Ответ, дошедший до выборки целиком, и есть предъявление того, что
        // за сверкой никто не ходил.
        assertThat(unknown.completeness())
                .as("полнота при этом едет своим составом: вызов дошёл до выборки целиком")
                .containsKeys(BOUND_FIELD, CLAIMABLE_FIELD);
    }

    @Test
    @DisplayName("B8.13 — Содержимое едет объектом, а не строкой с экранированием")
    void theContentTravelsAsADocumentRatherThanAnEscapedString() {
        given("E-RICH", Duration.ofMinutes(10), Bodies.richDocument());
        awaitAccepted(1L);

        Answer page = page();

        assertThat(page.body())
                .as("за именем содержимого стои́т объект, а не кавычка")
                .contains("\"" + CONTENT_FIELD + "\":{")
                .doesNotContain("\"" + CONTENT_FIELD + "\":\"");
        assertThat(page.body()).as("экранированных кавычек внутри нет").doesNotContain("\\\"");
        assertThat(page.records().getFirst().get(CONTENT_FIELD))
                .as("поданное и отданное эквивалентны как документы")
                .isEqualTo(document(Bodies.richDocument()));
    }

    @Test
    @DisplayName("B8.14 — Наружу идёт идентичность события, а не ключ базы")
    void theOutwardIdentityIsTheEventIdRatherThanTheDatabaseKey() {
        givenFiveRows();

        Answer page = page();

        assertThat(page.records()).as("строк отдано страница, и позиция продолжения есть")
                .hasSize(AuditSubstrate.PAGE_SIZE);
        assertThat(page.records().getFirst())
                .as("числового ключа у строки нет: наружу идёт идентичность события")
                .doesNotContainKey("id")
                .containsKey(EVENT_ID_FIELD);
        assertThat(page.records().getFirst())
                .as("тенанта форма строки не несёт: он приезжает операндом вызова")
                .doesNotContainKey("tenantId");
        assertThat(page.nextCursor())
                .as("и у курсора ключа базы нет тоже — он пара «момент, идентичность»")
                .containsOnlyKeys(OCCURRED_FIELD, EVENT_ID_FIELD);
        assertThat(rows.all(JOURNAL_TABLE).getFirst())
                .as("вход поставлен: ключ у строки базы есть, и наружу он не вышел")
                .containsKey("id");
    }

    @Test
    @DisplayName("B8.15 — Пустая страница полноту всё равно несёт")
    void anEmptyPageStillCarriesItsCompleteness() {
        givenReceptionStateRows();

        Answer page = page();

        assertThat(page.records()).as("в окне нет ни одной строки").isEmpty();
        assertThat(page.nextCursor()).as("позиции продолжения нет").isNull();
        assertThat(page.completeness().get(BOUND_FIELD))
                .as("нижняя граница присутствует и посчитана").isNotNull();
        assertThat(page.completeness().get(CLAIMABLE_FIELD))
                .as("предикат непрерывности — тоже: число без своей достоверности читается как «сейчас»")
                .isEqualTo(Boolean.TRUE);
    }

    @Test
    @DisplayName("B8.17 — Строки принятых событий читаются целиком, включая версию формы")
    void rowsAreReadWholeIncludingTheFormVersionTheyArrivedWith() {
        Map<String, String> first = new LinkedHashMap<>(
                envelope("E-V1", momentsAgo(Duration.ofMinutes(20))));
        Map<String, String> second = new LinkedHashMap<>(
                envelope("E-V2", momentsAgo(Duration.ofMinutes(10))));
        second.put(VERSION, "2");
        Wire.publish(CORE, TENANT, first, Bodies.reference());
        Wire.publish(CORE, TENANT, second, Bodies.richDocument());
        awaitAccepted(2L);

        Answer page = page();

        assertThat(ids(page)).as("отданы обе строки одного класса").containsExactly("E-V2", "E-V1");
        assertThat(page.records().getFirst().get(VERSION_FIELD))
                .as("у каждой своя версия формы").isEqualTo(2);
        assertThat(page.records().getLast().get(VERSION_FIELD))
                .as("старая версия не отброшена и не переведена в новую").isEqualTo(1);
        assertThat(page.records().getFirst().get(TYPE_FIELD))
                .as("класс события у обеих один").isEqualTo(DEAL_OPENED);
        assertThat(page.records().getFirst().get(CONTENT_FIELD))
                .as("содержимое каждой отдано как принято")
                .isEqualTo(document(Bodies.richDocument()));
        assertThat(page.records().getLast().get(CONTENT_FIELD))
                .isEqualTo(document(Bodies.reference()));
    }

    /**
     * Кладёт пять строк тенанта, три из которых несут радиус сделки:
     * журнал длиннее страницы и подмножество, на котором курсор обязан
     * работать так же.
     *
     * <p><b>Две строки делят ОДИН момент происшествия, и делят его ровно на
     * границе страницы.</b> Без такой пары курсор из ПАРЫ неотличим от
     * курсора из одного момента: моменты различны, и обе формы сравнения
     * дают один ответ. Пара {@code E-4}/{@code E-3} одного момента
     * разводится второй половиной — идентичностью события, — и первая из них
     * уходит на первую страницу, вторая остаётся на второй: курсор,
     * сравнивающий только момент, потерял бы её молча.
     *
     * <p><b>Равный момент ставится ОДНИМ значением, а не двумя одинаковыми
     * вызовами часов.</b> {@code momentsAgo} снимает «сейчас» на каждом
     * вызове, и две строки «тридцать минут назад» расходятся на
     * миллисекунды — то есть парой одного момента не являются вовсе. Дефект
     * этот молчаливый: обе клетки зелены, а предмет их не мерится; поймала
     * его мутационная ось, взявшая ноль клеток
     * (.claude/tests/cases/audit.md §«Контрольный прогон на нейтрализованной
     * сборке»).
     */
    private void givenFiveRows() {
        OffsetDateTime shared = momentsAgo(Duration.ofMinutes(30));
        given("E-1", Duration.ofMinutes(10), Bodies.withDeal(DEAL));
        given("E-2", Duration.ofMinutes(20), Bodies.reference());
        givenAt("E-3", shared, Bodies.withDeal(DEAL));
        givenAt("E-4", shared, Bodies.reference());
        given("E-5", Duration.ofMinutes(50), Bodies.withDeal(DEAL));
        awaitAccepted(5L);
    }

    /** Кладёт в тему событие названного возраста с названным содержимым. */
    private void given(String eventId, Duration age, String content) {
        givenAt(eventId, momentsAgo(age), content);
    }

    /**
     * То же, но моментом происшествия идёт НАЗВАННОЕ значение.
     *
     * <p>Им ставится пара строк одного момента: возраст, поданный дважды,
     * дал бы два разных момента — часы снимаются на каждом вызове.
     */
    private void givenAt(String eventId, OffsetDateTime occurredAt, String content) {
        publish(CORE, eventId, occurredAt, content);
    }

    /** То же, но ключом записи идёт названный тенант. */
    private void givenOf(String tenant, String eventId, Duration age, String content) {
        Wire.publish(CORE, tenant, envelope(eventId, momentsAgo(age)), content);
    }

    /**
     * Ждёт, пока журнал примет названное число строк И группа догонит конец
     * темы.
     *
     * <p>Порознь каждого мало: число строк не говорит, что обработка
     * кончилась, а смещение не говорит, что строки легли.
     *
     * @param expected сколько строк журнал обязан нести
     */
    private void awaitAccepted(Long expected) {
        awaitRecordCount(expected);
        awaitConsumed(CORE);
    }

    /** Страница окна, которому его ширина безразлична. */
    private Answer page() {
        OffsetDateTime to = now();
        return pageOf(to.minus(WINDOW), to);
    }

    /** Страница названного окна с названными операндами сверх него. */
    private Answer pageOf(OffsetDateTime from, OffsetDateTime to, String... operands) {
        return get(journalPath(from, to, operands), TENANT);
    }

    /**
     * Момент в форме операнда запроса — БЕЗ кодирования.
     *
     * <p>Кодирует его сборщик пути ({@link AuditBox#journalPath}); поданный
     * закодированным, он уехал бы закодированным дважды, и клетка мерила бы
     * разбор URI вместо предмета.
     */
    private static String iso(OffsetDateTime value) {
        return value.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    /** Идентичности строк страницы в порядке выдачи. */
    private static List<String> ids(Answer page) {
        return page.records().stream()
                .map(row -> String.valueOf(row.get(EVENT_ID_FIELD)))
                .toList();
    }

    /** Документ как разобранная карта: сравнение идёт по составу, а не по тексту. */
    private static Map<String, Object> document(String json) {
        return JsonParserFactory.getJsonParser().parseMap(json);
    }

    /** Содержимое со всеми четырьмя именами радиуса верхнего уровня. */
    private static String fullRadius() {
        return Bodies.withRadius(ACCOUNT, INSTRUMENT, DEAL, STRATEGY);
    }
}
