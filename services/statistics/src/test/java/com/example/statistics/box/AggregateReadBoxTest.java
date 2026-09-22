package com.example.statistics.box;

import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетки {@code B10.1} — {@code B10.7} и {@code B10.11} — {@code B10.16},
 * {@code B10.18}: агрегатная выборка чтения на штатном положении осей
 * (.claude/tests/cases/statistics.md §«B10 — Агрегатная выборка чтения»).
 *
 * <p><b>Класс равен КОНФИГУРАЦИИ КОНТЕКСТА, и делит группу ровно она.</b>
 * Четырнадцать клеток здесь берут штатное положение осей и расходятся только
 * тем, какие факты каждая себе кладёт и какой вопрос задаёт поверхности;
 * четыре клетки СТРАНИЦЫ ({@code B10.8} — {@code B10.10}, {@code B10.17})
 * живут своим классом, потому что входом им служит сама ось — размер
 * страницы, а у последней ещё и предел ширины окна
 * ({@link AggregateReadAxesBoxTest}).
 *
 * <p><b>У этой группы поверхность впервые ПОДАЁТ ВХОД, а не только
 * наблюдает выход.</b> Строки агрегатов кладёт проход пересчёта, а ВОПРОС
 * читателя есть операнд вызова, и отвержения вопроса наблюдаются только
 * поверхностью.
 *
 * <p><b>Факты кладутся прямой записью</b> ({@link Facts}, {@link DealDraft}),
 * <b>такт пересчёта подаёт сам кейс</b>: у события два следствия, разнесённые
 * во времени разными исполнителями, и предметом этой группы является второе
 * из них — выдача уже собранных строк. Подача фактов сообщением сделала бы
 * четырнадцать клеток зависящими от живости приёма, а тропу «слушатель кладёт
 * факт» держат клетки {@code B1}.
 *
 * <p><b>Факты лежат в ПОЛНОЧЬ своих суток</b> ({@link #midnightDaysAgo}):
 * проход не пишет суток, начавшихся раньше первого факта ряда
 * ({@code dayRecomputable}), и факт в середине суток оставил бы их покрытыми
 * частично — строки не появилось бы вовсе, то есть клетка краснела бы по
 * охране отбора, а не по своему предмету.
 *
 * <p><b>Ожидание отвергнутого вопроса берёт ПАРУ «класс плюс названный повод»,
 * а не HTTP-число</b> (.claude/tests/cases/statistics.md §«Число ответа и
 * класс отказа — разные ожидания»): число ставит наш собственный код в ветви
 * обработчика, и ход выравнивания кодов по платформе сделал бы пиньнутое число
 * красным на исправной системе. Числом пиньнуты ровно два исхода: {@code 200}
 * — контракт успеха самой точки — и {@code 400} КОНТЕЙНЕРА, чей статус дом
 * называет наследуемым.
 *
 * <p><b>Предел ширины окна читается ОСЬЮ СУБСТРАТА</b>
 * ({@link StatisticsSubstrate#READ_MAX_WINDOW_DAYS}), а не умолчанием
 * сервиса: унаследованное умолчание пришлось бы прочитать из его ресурса, то
 * есть заглянуть ящику внутрь. Сдвинут он здесь не будет — клейм «предел
 * приезжает конфигурацией» несёт {@code B10.17} со своим контекстом, и вторым
 * его носителем эта клетка не становится (.claude/rules/carrier-levels.md).
 *
 * <p><b>Своя группа и своя тема взяты по общему доводу класса кейсов:</b>
 * контексты прогона не закрываются, а группа есть состояние на брокере.
 * Производителя в этих клетках нет ни одного, и тема их остаётся пустой.
 */
class AggregateReadBoxTest extends StatisticsBox {

    /** Краткое имя класса: из него строятся его группа и его тема. */
    private static final String SLUG = "b10-read";

    /** Сутки, в которых лежат факты почти всех клеток группы. */
    private static final Integer DAY = 1;

    /** Соседние, более старые сутки: ими читается порядок выдачи. */
    private static final Integer EARLIER_DAY = 2;

    /** Ширина окна чтения клеток, которым она безразлична. */
    private static final Integer WINDOW = StatisticsSubstrate.RECOMPUTE_WINDOW_DAYS - 1;

    /** Имя владельца первым сегментом после версии: по нему адресует периметр. */
    private static final String OWNER = "statistics";

    /** Имя операнда зерна в вопросе читателя. */
    private static final String GRAIN = "grain";

    /** Имя левой границы окна. */
    private static final String FROM = "from";

    /** Имя правой границы окна. */
    private static final String TO = "to";

    /** Имя первого обязательного компонента позиции. */
    private static final String CURSOR_BUCKET = "cursorBucketDate";

    /** Имя второго обязательного компонента позиции. */
    private static final String CURSOR_ACCOUNT = "cursorExchangeAccountInternalId";

    /** Имя компонента позиции, которого нет в ключе зерна происшествий. */
    private static final String CURSOR_STRATEGY = "cursorStrategyInternalId";

    /** Имя второго такого компонента. */
    private static final String CURSOR_CURRENCY = "cursorResultCurrency";

    /** Имя перечня строк сделочного зерна в теле страницы. */
    private static final String DEAL_ROWS = "dealRows";

    /** Имя перечня строк зерна происшествий. */
    private static final String INCIDENT_ROWS = "incidentRows";

    /** Имя величины нижней границы полноты. */
    private static final String BOUND_FIELD = "lowerBound";

    /** Имя предиката непрерывности — второй величины того же состава. */
    private static final String CLAIMABLE_FIELD = "continuityClaimable";

    /** Имя поля класса отказа в едином error-DTO. */
    private static final String CODE_FIELD = "code";

    /** Имя поля пояснения в том же DTO. */
    private static final String MESSAGE_FIELD = "message";

    /** Класс отказа нашего кода: вопрос чтения не принят. */
    private static final String QUERY_REJECTED = "QUERY_NOT_ACCEPTED";

    /** Класс отказа контейнера: не принята сама форма вызова. */
    private static final String REQUEST_REJECTED = "REQUEST_NOT_ACCEPTED";

    /** Поле биржевого счёта — компонента ключа обоих зёрен в выдаче. */
    private static final String ACCOUNT_FIELD = "exchangeAccountInternalId";

    /** Поле определения стратегии — компонента ключа сделочного зерна. */
    private static final String STRATEGY_FIELD = "strategyInternalId";

    /** Поле расчётной валюты — четвёртого компонента того же ключа. */
    private static final String CURRENCY_FIELD = "resultCurrency";

    /** Поле популяции всех долей: знаменатель доли выигрышных. */
    private static final String RISK_BEARING_DEALS = "riskBearingDeals";

    /** Поле счётчика выигравших — числителя той же доли. */
    private static final String WINNING_DEALS = "winningDeals";

    /** Поле суммы выигрышей — слагаемого профит-фактора. */
    private static final String WIN_RESULT_SUM = "winResultSum";

    /** Поле суммы убытков — второго его слагаемого. */
    private static final String LOSS_RESULT_SUM = "lossResultSum";

    /** Поле суммы R-мультипликаторов — числителя среднего R. */
    private static final String R_SUM = "rSum";

    /** Поле знаменателя того же среднего. */
    private static final String R_DENOMINATOR_DEALS = "rDenominatorDeals";

    /** Колонка тенанта в строке агрегата: наружу она не выходит. */
    private static final String TENANT_COLUMN = "tenant_id";

    /** Суррогатный ключ базы: наружу не выходит он же. */
    private static final String SURROGATE_COLUMN = "id";

    /**
     * Поля строки, которые не являются ни счётчиком, ни суммой: компоненты
     * ключа зерна и момент сборки.
     *
     * <p><b>Ими отрицание ПРОИЗВОДНЫХ берёт положительную форму.</b> Перечень
     * частей слова («доля», «фактор», «среднее») пришлось бы держать полным,
     * а производная под непредусмотренным именем осталась бы ему невидимой;
     * к тому же он ложно срабатывает на законных именах — {@code strategy}
     * несёт в себе {@code rate}. Утверждение «всё прочее есть слагаемое»
     * закрывает обе дыры разом: и доля, и фактор, и ожидаемость, и среднее не
     * кончаются ни на счётчик, ни на сумму, как бы их ни назвали.
     */
    private static final List<String> NON_SUMMAND_FIELDS =
            List.of(ACCOUNT_FIELD, STRATEGY_FIELD, CURRENCY_FIELD, BUCKET_DATE, ASSEMBLED_AT);

    /** Хвост имени счётчика: им читается «это слагаемое, а не производная». */
    private static final String COUNTER_TAIL = "Deals";

    /** Хвост имени денежной суммы — второго рода слагаемого. */
    private static final String SUM_TAIL = "Sum";

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        StatisticsSubstrate.registerOwn(registry, SLUG);
    }

    @Test
    @DisplayName("B10.1 — Штатная страница: строки выбранного зерна и полнота рядом с ними")
    void aPageCarriesTheRowsOfTheChosenGrainAndCompletenessBesideThem() {
        givenReceptionStateRows();
        Facts.deal("E-10-1-OLD", TENANT, midnightDaysAgo(EARLIER_DAY));
        Facts.deal("E-10-1-NEW", TENANT, midnightDaysAgo(DAY));
        Facts.incident("E-10-1-INC", TENANT, HOLD_RAISED, midnightDaysAgo(DAY));

        recompute();

        Answer deals = page(DEAL_GRAIN, WINDOW);
        assertThat(deals.status()).as("контракт успеха объявлен самой точкой").isEqualTo(200);
        assertThat(bucketDatesOf(deals.dealRows()))
                .as("строки идут от новых суток к старым")
                .containsExactly(day(DAY), day(EARLIER_DAY));
        assertThat(deals.incidentRows())
                .as("перечень второго зерна ОТСУТСТВУЕТ, а не пуст: пустой означал бы "
                        + "«строк в окне нет», и читатель не отличил бы его от «спрошено "
                        + "другое зерно»")
                .isNull();
        assertThat(deals.completeness())
                .as("полнота лежит в ТОМ ЖЕ ответе, что и строки, и несёт обе величины")
                .containsKeys(BOUND_FIELD, CLAIMABLE_FIELD);
        assertThat(deals.completeness().get(BOUND_FIELD))
                .as("и она посчитана, а не объявлена пустой").isNotNull();
        assertThat(deals.nextCursor())
                .as("окно дочитано: позиции продолжения нет").isNull();

        Answer incidents = page(INCIDENT_GRAIN, WINDOW);
        assertThat(incidents.incidentRows())
                .as("у второго зерна своя строка тех же суток").hasSize(1);
        assertThat(incidents.dealRows())
                .as("а сделочный перечень отсутствует зеркально: заполнен ровно тот, чьё "
                        + "зерно названо").isNull();
    }

    @Test
    @DisplayName("B10.2 — Зерно не названо либо названо вне перечня: вопрос не принят")
    void anUnnamedOrForeignGrainIsNotAccepted() {
        Facts.deal("E-10-2", TENANT, midnightDaysAgo(DAY));
        recompute();

        Answer unnamed = ask(FROM, day(WINDOW), TO, day(0));
        Answer foreign = page("ORDER", WINDOW);

        for (Answer answer : List.of(unnamed, foreign)) {
            assertThat(answer.asObject().get(CODE_FIELD))
                    .as("у обоих поводов один класс отказа").isEqualTo(QUERY_REJECTED);
            assertThat(reason(answer))
                    .as("текст называет перечень допустимых значений ЦЕЛИКОМ, а не один "
                            + "из них: читатель не обязан перебирать")
                    .contains(DEAL_GRAIN).contains(INCIDENT_GRAIN);
            assertThat(answer.asObject())
                    .as("строк не отдано ни одной: молчаливого выбора зерна нет")
                    .doesNotContainKey(DEAL_ROWS).doesNotContainKey(INCIDENT_ROWS);
        }
        assertThat(reason(unnamed))
                .as("и текст у обоих ОДИН: различать их значило бы различать поводы, по "
                        + "которым читатель делает одно и то же")
                .isEqualTo(reason(foreign));
        assertThat(rows.count(DEAL_AGGREGATES))
                .as("вход поставлен: строка, которую отдало бы названное зерно, собрана")
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("B10.3 — Окна нет либо названа одна граница: вопрос не принят")
    void aMissingOrHalfNamedWindowIsNotAccepted() {
        Facts.deal("E-10-3", TENANT, midnightDaysAgo(DAY));
        recompute();

        Answer withoutBoth = ask(GRAIN, DEAL_GRAIN);
        Answer leftOnly = ask(GRAIN, DEAL_GRAIN, FROM, day(WINDOW));
        Answer rightOnly = ask(GRAIN, DEAL_GRAIN, TO, day(0));

        for (Answer answer : List.of(withoutBoth, leftOnly, rightOnly)) {
            assertThat(answer.asObject().get(CODE_FIELD))
                    .as("у всех трёх один класс отказа").isEqualTo(QUERY_REJECTED);
            assertThat(reason(answer))
                    .as("текст называет обязательность ОБЕИХ границ")
                    .contains("Окно").contains("обе границы");
            assertThat(answer.asObject())
                    .as("чтения без предела не происходит ни в одном из трёх случаев")
                    .doesNotContainKey(DEAL_ROWS);
        }
        assertThat(rows.count(DEAL_AGGREGATES))
                .as("вход поставлен: строка, которую отдало бы окно по умолчанию, лежит")
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("B10.4 — Окно перевёрнуто: отказ, а не пустая выдача")
    void aReversedWindowIsRejectedRatherThanAnsweredEmpty() {
        Facts.deal("E-10-4", TENANT, midnightDaysAgo(DAY));
        recompute();

        Answer reversed = ask(GRAIN, DEAL_GRAIN, FROM, day(0), TO, day(WINDOW));

        assertThat(reversed.asObject().get(CODE_FIELD))
                .as("класс отказа тот же — вопрос не принят").isEqualTo(QUERY_REJECTED);
        assertThat(reason(reversed))
                .as("а текст называет ИНОЙ повод — порядок границ").contains("раньше левой");
        assertThat(reversed.asObject())
                .as("пустого перечня с контрактом успеха не отдано: он читался бы как «за "
                        + "этот период строк не было»")
                .doesNotContainKey(DEAL_ROWS);
        assertThat(page(DEAL_GRAIN, WINDOW).dealRows())
                .as("вход поставлен: те же границы в порядке отдают строку").hasSize(1);
    }

    @Test
    @DisplayName("B10.5 — Окно шире предела отвергается, а не сужается")
    void aWindowWiderThanTheLimitIsRejectedRatherThanNarrowed() {
        Facts.deal("E-10-5", TENANT, midnightDaysAgo(DAY));
        recompute();

        Answer exact = page(DEAL_GRAIN, StatisticsSubstrate.READ_MAX_WINDOW_DAYS - 1);
        Answer wider = page(DEAL_GRAIN, StatisticsSubstrate.READ_MAX_WINDOW_DAYS);

        assertThat(exact.status())
                .as("окно РОВНО в предел принято: предел есть наибольшее допустимое число "
                        + "суток, а не первое запрещённое")
                .isEqualTo(200);
        assertThat(exact.dealRows()).as("и отдаёт строку окна").hasSize(1);
        assertThat(wider.asObject().get(CODE_FIELD))
                .as("окно на одни сутки шире вопросом не принято").isEqualTo(QUERY_REJECTED);
        assertThat(reason(wider))
                .as("текст называет сам предел и говорит, что окно не сужается")
                .contains(String.valueOf(StatisticsSubstrate.READ_MAX_WINDOW_DAYS))
                .contains("не сужается молча");
        assertThat(wider.asObject())
                .as("урезанной страницы под видом запрошенной нет")
                .doesNotContainKey(DEAL_ROWS);
    }

    @Test
    @DisplayName("B10.6 — Позиция названа наполовину: вопрос не принят")
    void aHalfNamedCursorIsNotAccepted() {
        Facts.deal("E-10-6", TENANT, midnightDaysAgo(DAY));
        recompute();

        Answer bucketOnly = page(DEAL_GRAIN, WINDOW, CURSOR_BUCKET, day(DAY));
        Answer accountOnly = page(DEAL_GRAIN, WINDOW, CURSOR_ACCOUNT, Facts.ACCOUNT);
        Answer tailOnly = page(DEAL_GRAIN, WINDOW,
                CURSOR_STRATEGY, Facts.STRATEGY, CURSOR_CURRENCY, Bodies.CURRENCY);
        Answer neither = page(DEAL_GRAIN, WINDOW);

        for (Answer half : List.of(bucketOnly, accountOnly, tailOnly)) {
            assertThat(half.asObject().get(CODE_FIELD))
                    .as("половина позиции позиции не определяет").isEqualTo(QUERY_REJECTED);
            assertThat(reason(half))
                    .as("текст называет ПАРУ обязательных компонентов")
                    .contains("сутками зерна и биржевым счётом вместе");
            assertThat(half.asObject())
                    .as("чтения с начала окна под видом продолжения не происходит")
                    .doesNotContainKey(DEAL_ROWS);
        }
        assertThat(neither.status())
                .as("а вопрос БЕЗ обеих половин принят: это первая страница")
                .isEqualTo(200);
        assertThat(neither.dealRows())
                .as("половина позиции отличима от её отсутствия").hasSize(1);
    }

    @Test
    @DisplayName("B10.7 — Позиция несёт компоненты чужого зерна: вопрос не принят")
    void aCursorCarryingForeignGrainComponentsIsNotAccepted() {
        Facts.deal("E-10-7-DEAL", TENANT, midnightDaysAgo(DAY));
        Facts.incident("E-10-7-INC", TENANT, HOLD_RAISED, midnightDaysAgo(DAY));
        recompute();

        Answer withStrategy = page(INCIDENT_GRAIN, WINDOW,
                CURSOR_BUCKET, day(DAY), CURSOR_ACCOUNT, Facts.ACCOUNT,
                CURSOR_STRATEGY, Facts.STRATEGY);
        Answer withCurrency = page(INCIDENT_GRAIN, WINDOW,
                CURSOR_BUCKET, day(DAY), CURSOR_ACCOUNT, Facts.ACCOUNT,
                CURSOR_CURRENCY, Bodies.CURRENCY);
        Answer allFourAtDeal = page(DEAL_GRAIN, WINDOW,
                CURSOR_BUCKET, day(0), CURSOR_ACCOUNT, Facts.ACCOUNT,
                CURSOR_STRATEGY, Facts.STRATEGY, CURSOR_CURRENCY, Bodies.CURRENCY);

        for (Answer foreign : List.of(withStrategy, withCurrency)) {
            assertThat(foreign.asObject().get(CODE_FIELD))
                    .as("лишний компонент позиции не игнорируется молча")
                    .isEqualTo(QUERY_REJECTED);
            assertThat(reason(foreign))
                    .as("текст называет, каких компонентов в ключе зерна происшествий нет")
                    .contains("не выбранного зерна")
                    .contains("зерна происшествий нет");
            assertThat(foreign.asObject())
                    .as("страницы, о которой читатель не спрашивал, не отдано")
                    .doesNotContainKey(INCIDENT_ROWS);
        }
        assertThat(allFourAtDeal.status())
                .as("у сделочного зерна обратной ветви нет: его ключ несёт все четыре")
                .isEqualTo(200);
        assertThat(allFourAtDeal.dealRows())
                .as("и вопрос дошёл до выборки — перечень заполнен, а не отсутствует")
                .isNotNull();
    }

    @Test
    @DisplayName("B10.11 — Чужой тенант не виден")
    void aForeignTenantIsNotVisible() {
        Facts.deal("E-10-11-MINE", TENANT, midnightDaysAgo(DAY));
        Facts.deal("E-10-11-FOREIGN", SECOND_TENANT, midnightDaysAgo(DAY));
        recompute();

        Answer mine = page(DEAL_GRAIN, WINDOW);
        Answer foreign = get(aggregatePath(GRAIN, DEAL_GRAIN, FROM, day(WINDOW), TO, day(0)),
                SECOND_TENANT);

        assertThat(rows.count(DEAL_AGGREGATES))
                .as("вход поставлен: строк в базе две — по одной на тенанта")
                .isEqualTo(2L);
        assertThat(mine.dealRows()).as("отдана только своя").hasSize(1);
        assertThat(rowOf(mine.dealRows(), DAY).get(CLOSED_DEALS))
                .as("и чисел соседа нет ни в одной клетке: сделка одна, а не две")
                .isEqualTo(1);
        assertThat(mine.body())
                .as("имени соседа нет и в теле ответа").doesNotContain(SECOND_TENANT);
        assertThat(foreign.dealRows())
                .as("радиус отбора — заголовок контекста: тот же вопрос под именем соседа "
                        + "отдаёт его строку").hasSize(1);
    }

    @Test
    @DisplayName("B10.12 — Тенант обязателен, а доверие к нему здесь не добывается")
    void theTenantIsMandatoryAndItsTrustIsNotEstablishedHere() {
        givenReceptionStateRows();
        Facts.deal("E-10-12", TENANT, midnightDaysAgo(DAY));
        recompute();
        String path = aggregatePath(GRAIN, DEAL_GRAIN, FROM, day(WINDOW), TO, day(0));

        Answer missing = getWithoutTenant(path);
        Answer blank = get(path, "");
        Answer unknown = get(path, THIRD_TENANT);

        for (Answer answer : List.of(missing, blank)) {
            assertThat(answer.status())
                    .as("статус контейнера наследуется, и дом называет его зафиксированным")
                    .isEqualTo(400);
            assertThat(answer.asObject().get(CODE_FIELD))
                    .as("класс ИНОЙ: форму вызова отвергает контейнер, а не наш вопрос чтения")
                    .isEqualTo(REQUEST_REJECTED);
        }
        assertThat(unknown.status())
                .as("незнакомый тенант — не отказ: сверки с членствами не происходит, и "
                        + "строки к соседу за ней нет ни одной")
                .isEqualTo(200);
        assertThat(unknown.dealRows())
                .as("полученный тенант ОТБИРАЕТ строки, и отбирает ровно ничего").isEmpty();
        assertThat(unknown.completeness())
                .as("вызов при этом дошёл до выборки целиком: полнота едет своим составом")
                .containsKeys(BOUND_FIELD, CLAIMABLE_FIELD);
        assertThat(rows.all(DEAL_AGGREGATES, TENANT_COLUMN))
                .as("и новым тенант не присваивается: строка в базе одна, и она прежнего "
                        + "владельца")
                .singleElement()
                .satisfies(row -> assertThat(row.get(TENANT_COLUMN)).isEqualTo(TENANT));
    }

    @Test
    @DisplayName("B10.13 — Пустая страница полноту всё равно несёт")
    void anEmptyPageStillCarriesCompleteness() {
        givenReceptionStateRows();

        Answer page = page(DEAL_GRAIN, WINDOW);

        assertThat(page.status()).as("контракт успеха тот же").isEqualTo(200);
        assertThat(page.dealRows())
                .as("перечень выбранного зерна ПУСТ, а не отсутствует").isNotNull().isEmpty();
        assertThat(page.incidentRows())
                .as("а невыбранного — отсутствует: читатель отличает «строк нет» от «зерно "
                        + "спрошено другое»").isNull();
        assertThat(page.completeness())
                .as("обе величины полноты отданы и на пустой странице: без них ноль строк "
                        + "неотличим от остановленного приёма")
                .containsKeys(BOUND_FIELD, CLAIMABLE_FIELD);
        assertThat(page.completeness().get(BOUND_FIELD))
                .as("и граница посчитана").isNotNull();
    }

    @Test
    @DisplayName("B10.14 — Наружу идут ключи зерна, а не ключ базы")
    void theOutwardKeysAreTheGrainKeysRatherThanTheDatabaseKey() {
        Facts.deal("E-10-14-DEAL", TENANT, midnightDaysAgo(DAY));
        Facts.incident("E-10-14-INC", TENANT, HOLD_RAISED, midnightDaysAgo(DAY));
        recompute();

        Map<String, Object> dealRow = page(DEAL_GRAIN, WINDOW).dealRows().getFirst();
        Map<String, Object> incidentRow = page(INCIDENT_GRAIN, WINDOW).incidentRows().getFirst();

        assertThat(rows.columnNames(DEAL_AGGREGATES))
                .as("клетка не впустую: суррогатный ключ и тенант у строки БАЗЫ есть")
                .contains(SURROGATE_COLUMN, TENANT_COLUMN);
        for (Map<String, Object> handed : List.of(dealRow, incidentRow)) {
            assertThat(handed)
                    .as("суррогатного ключа базы в отданной строке нет ни у одного зерна")
                    .doesNotContainKey(SURROGATE_COLUMN);
            assertThat(handed.keySet())
                    .as("всякая идентичность едет ВНЕШНЕЙ: имён, кончающихся на «Id» мимо "
                            + "«InternalId», в строке нет")
                    .filteredOn(name -> name.toLowerCase(Locale.ROOT).endsWith("id"))
                    .allMatch(name -> name.endsWith("InternalId"));
            assertThat(handed).as("счёт едет внешним именем").containsKey(ACCOUNT_FIELD);
            assertThat(handed)
                    .as("тенанта строка не несёт вовсе — он радиус вызова, один на выдачу")
                    .doesNotContainKey("tenantInternalId").doesNotContainKey(TENANT_COLUMN);
        }
        assertThat(dealRow)
                .as("а определение стратегии — второй внешней идентичностью сделочного ключа")
                .containsKey(STRATEGY_FIELD);
    }

    @Test
    @DisplayName("B10.15 — Момент сборки едет со строкой")
    void theAssemblyMomentTravelsWithTheRow() {
        Facts.deal("E-10-15-DEAL", TENANT, midnightDaysAgo(DAY));
        Facts.incident("E-10-15-INC", TENANT, HOLD_RAISED, midnightDaysAgo(DAY));
        // Нижняя граница УСЕЧЕНА ВНИЗ до миллисекунд намеренно: колонка хранит
        // момент с микросекундной точностью, а часы процесса идут в
        // наносекундах — без усечения клетка мерила бы округление хранения, а
        // не момент прохода. Верхней границе усечения не нужно: усечение
        // только уменьшает, и записанное не бывает позже снятого после.
        OffsetDateTime before = momentsAgo(Duration.ZERO);

        recompute();

        OffsetDateTime after = now();
        Map<String, Object> dealRow = page(DEAL_GRAIN, WINDOW).dealRows().getFirst();
        Map<String, Object> incidentRow = page(INCIDENT_GRAIN, WINDOW).incidentRows().getFirst();
        for (Map<String, Object> handed : List.of(dealRow, incidentRow)) {
            assertThat(handed.get(ASSEMBLED_AT))
                    .as("момент сборки едет у строки ОБОИХ зёрен: число без своей "
                            + "актуальности читается как «сейчас»")
                    .isNotNull();
            assertThat(moment(handed, ASSEMBLED_AT))
                    .as("и он есть момент ПРОХОДА, собравшего строку, — он лежит между "
                            + "двумя часами, между которыми проход и состоялся")
                    .isAfterOrEqualTo(before).isBeforeOrEqualTo(after);
        }
        assertThat(moment(dealRow, ASSEMBLED_AT))
                .as("моментом СОБЫТИЯ он при этом не является: ось времени зерна лежит "
                        + "сутками раньше")
                .isAfter(midnightDaysAgo(DAY));
    }

    @Test
    @DisplayName("B10.16 — Производные поверхностью не считаются")
    void derivedValuesAreNotComputedBySurface() {
        // Сделка, закрытая БЕЗ входа: риск не принимался, планового риска нет.
        // Ею знаменатели доли выигрышных и среднего R встают на ноль разом.
        DealDraft.of("E-10-16-NO-RISK", TENANT, midnightDaysAgo(DAY))
                .tookRisk(Boolean.FALSE)
                .plannedRisk("0")
                .reconciliationStatus(Bodies.NOT_RUN)
                .riskBenchmarkAvailability(Bodies.NOT_APPLICABLE)
                .build().put();

        recompute();

        Answer page = page(DEAL_GRAIN, WINDOW);
        Map<String, Object> row = page.dealRows().getFirst();
        assertThat(page.status())
                .as("деления на ноль не произошло нигде: страница отдана")
                .isEqualTo(200);
        assertThat(row.get(RISK_BEARING_DEALS))
                .as("клетка не впустую: знаменатель доли выигрышных равен нулю").isEqualTo(0);
        assertThat(row.get(R_DENOMINATOR_DEALS))
                .as("и знаменатель среднего R — тоже").isEqualTo(0);
        assertThat(row.keySet())
                .as("производных в строке нет ни одной: всё, что не ключ зерна и не момент "
                        + "сборки, есть счётчик либо сумма — то есть слагаемое, а доле, "
                        + "фактору, ожидаемости и среднему такими именами не называться")
                .filteredOn(name -> isFalse(NON_SUMMAND_FIELDS.contains(name)))
                .allMatch(name -> name.endsWith(COUNTER_TAIL) || name.endsWith(SUM_TAIL));
        assertThat(row)
                .as("отданы только слагаемые, и их хватает, чтобы читатель посчитал сам")
                .containsKeys(RISK_BEARING_DEALS, WINNING_DEALS, WIN_RESULT_SUM,
                        LOSS_RESULT_SUM, R_SUM, R_DENOMINATOR_DEALS);
    }

    @Test
    @DisplayName("B10.18 — Выборка одна на два зерна, второй точки нет")
    void oneSelectionServesBothGrainsAndThereIsNoSecondPoint() {
        Map<String, Object> routes = surfaceRoutes();

        assertThat(routes)
                .as("клетка не впустую: описание поверхности отображённые маршруты несёт")
                .isNotEmpty();
        assertThat(routes.keySet())
                .as("первым сегментом после версии у всякого маршрута стои́т имя "
                        + "ВЛАДЕЛЬЦА: периметр выбирает адресата по нему и своего перечня "
                        + "маршрутов не держит")
                .allMatch(route -> route.startsWith("/api/v1/" + OWNER + "/"));
        assertThat(routes.keySet())
                .as("отдельной точки на каждое зерно нет: имени зерна не несёт ни один путь")
                .noneMatch(route -> route.toLowerCase(Locale.ROOT).contains("deal")
                        || route.toLowerCase(Locale.ROOT).contains("incident"));
        assertThat(queryOperandsOf(routes, AGGREGATE_ROWS))
                .as("зерно приезжает ОПЕРАНДОМ вызова вместе с окном и позицией — то есть "
                        + "разрезом одной выборки, а не второй точкой")
                .contains(GRAIN, FROM, TO, CURSOR_BUCKET, CURSOR_ACCOUNT);
    }

    /**
     * Страница названного зерна за окно названной ширины с операндами сверх
     * него.
     *
     * @param grain    зерно строки
     * @param daysBack сколько суток назад открывается окно; правая граница —
     *                 нынешние сутки
     * @param extra    пары «имя операнда, значение» сверх зерна и окна
     */
    private Answer page(String grain, Integer daysBack, String... extra) {
        String[] operands = new String[6 + extra.length];
        operands[0] = GRAIN;
        operands[1] = grain;
        operands[2] = FROM;
        operands[3] = day(daysBack);
        operands[4] = TO;
        operands[5] = day(0);
        System.arraycopy(extra, 0, operands, 6, extra.length);
        return get(aggregatePath(operands), TENANT);
    }

    /**
     * Вопрос, собранный ровно из названных операндов.
     *
     * <p>Им спрашивают поверхность клетки, чей предмет — ОТСУТСТВИЕ операнда:
     * зерна, одной границы окна, обеих.
     *
     * @param operands пары «имя операнда, значение»
     */
    private Answer ask(String... operands) {
        return get(aggregatePath(operands), TENANT);
    }

    /** Пояснение отказа: им читается ПОВОД, тогда как класс читается кодом. */
    private static String reason(Answer answer) {
        return String.valueOf(answer.asObject().get(MESSAGE_FIELD));
    }

    /**
     * Имена операндов запроса, объявленные у названного маршрута.
     *
     * @param routes маршруты описания поверхности
     * @param route  путь, чьи операнды нужны
     */
    @SuppressWarnings("unchecked")
    private static List<String> queryOperandsOf(Map<String, Object> routes, String route) {
        Map<String, Object> methods = (Map<String, Object>) routes.get(route);
        Map<String, Object> read = (Map<String, Object>) methods.get("get");
        List<Map<String, Object>> declared = (List<Map<String, Object>>) read.get("parameters");
        return declared.stream().map(operand -> String.valueOf(operand.get("name"))).toList();
    }
}
