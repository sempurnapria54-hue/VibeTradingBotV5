package com.example.statistics.box;

import static java.util.Objects.nonNull;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетки {@code B10.8} — {@code B10.10} и {@code B10.17}: курсорная страница
 * агрегатной выборки
 * (.claude/tests/cases/statistics.md §«B10 — Агрегатная выборка чтения»).
 *
 * <p><b>Класс отделён от группы ОСЬЮ КОНФИГУРАЦИИ, а не числом клеток.</b>
 * Размер страницы есть ВХОД всех четырёх: при штатных двух сотнях строк
 * продолжение чтения пришлось бы предъявлять двумя сотнями суток зерна, то
 * есть мерить пропускную способность субстрата, а не курсор. Положение оси —
 * часть ключа кэша контекста, и сдвинуть его внутри класса нечем: соседние
 * четырнадцать клеток группы живут на штатных осях
 * ({@link AggregateReadBoxTest}).
 *
 * <p><b>Предел ширины окна сдвинут ради ОДНОЙ клетки, и сдвинут ЗАМЕТНО.</b>
 * {@code B10.17} утверждает, что предел приезжает величиной конфигурации, а
 * не константой кода, и на штатных девяноста двух сутках это не выразимо
 * вовсе — они совпадают с умолчанием сервиса дословно, то есть клетка прошла
 * бы и на реализации, читающей константу. Девять суток от умолчания отличаются
 * на порядок и при этом шире окна пересчёта: клетки страницы читают своё окно
 * в прежней ширине, и сдвиг не отнимает у них ни одного вопроса.
 *
 * <p><b>Сутки зерна — единственный способ развести строки по СТРАНИЦАМ здесь,
 * и вторым ключом им служит пустота.</b> Клетка о пустых компонентах ключа
 * кладёт четыре строки ОДНИХ суток и читает их порядком сравнения позиции:
 * пустота в нём наибольшая, потому что {@code desc} у Postgres означает
 * {@code nulls first} ({@code DealAggregateRepository#findPage}).
 *
 * <p><b>Факты кладутся прямой записью, такт подаёт сам кейс</b> — по общему
 * доводу группы: предмет здесь второе следствие события, выдача уже собранных
 * строк.
 *
 * <p><b>Своя группа и своя тема взяты по общему доводу класса кейсов:</b>
 * контексты прогона не закрываются, а группа есть состояние на брокере;
 * производителя в этих клетках нет ни одного.
 */
class AggregateReadAxesBoxTest extends StatisticsBox {

    /** Краткое имя класса: из него строятся его группа и его тема. */
    private static final String SLUG = "b10-axes";

    /** Размер страницы ЭТОГО контекста: он на два порядка меньше штатного. */
    private static final Integer OWN_PAGE_SIZE = 2;

    /** Предел ширины окна ЭТОГО контекста: он на порядок у́же штатного. */
    private static final Integer OWN_MAX_WINDOW_DAYS = 9;

    /** Ширина окна чтения клеток, которым она безразлична. */
    private static final Integer WINDOW = StatisticsSubstrate.RECOMPUTE_WINDOW_DAYS - 1;

    /** Нынешние сутки: в них ложится строка, приехавшая МЕЖДУ запросами. */
    private static final Integer TODAY = 0;

    /** Сутки, в которых лежат факты клеток о пустых компонентах ключа. */
    private static final Integer DAY = 1;

    /** Потолок числа страниц обхода: без него дефект курсора дал бы вечный цикл. */
    private static final Integer PAGE_CEILING = 10;

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

    /** Имя третьего компонента позиции: пустота у него — значение. */
    private static final String CURSOR_STRATEGY = "cursorStrategyInternalId";

    /** Имя четвёртого компонента позиции: пустота у него — значение тоже. */
    private static final String CURSOR_CURRENCY = "cursorResultCurrency";

    /** Поле суток зерна в отданной позиции продолжения. */
    private static final String CURSOR_BUCKET_FIELD = "bucketDate";

    /** Поле биржевого счёта в ней же. */
    private static final String CURSOR_ACCOUNT_FIELD = "exchangeAccountInternalId";

    /** Поле определения стратегии в ней же. */
    private static final String CURSOR_STRATEGY_FIELD = "strategyInternalId";

    /** Поле расчётной валюты в ней же. */
    private static final String CURSOR_CURRENCY_FIELD = "resultCurrency";

    /** Имя поля класса отказа в едином error-DTO. */
    private static final String CODE_FIELD = "code";

    /** Имя поля пояснения в том же DTO. */
    private static final String MESSAGE_FIELD = "message";

    /** Класс отказа нашего кода: вопрос чтения не принят. */
    private static final String QUERY_REJECTED = "QUERY_NOT_ACCEPTED";

    /** Запись пустого компонента ключа в перечне прочитанного. */
    private static final String ABSENT = "-";

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        StatisticsSubstrate.registerOwn(registry, SLUG, Map.of(
                StatisticsSubstrate.READ_PAGE_SIZE_KEY, String.valueOf(OWN_PAGE_SIZE),
                StatisticsSubstrate.READ_MAX_WINDOW_KEY, String.valueOf(OWN_MAX_WINDOW_DAYS)));
    }

    @Test
    @DisplayName("B10.8 — Курсор продолжает чтение без пропусков и дублей")
    void theCursorContinuesWithoutGapsOrDuplicates() {
        Facts.deal("E-10-8-D1", TENANT, midnightDaysAgo(1));
        Facts.deal("E-10-8-D2", TENANT, midnightDaysAgo(2));
        Facts.deal("E-10-8-D3", TENANT, midnightDaysAgo(3));
        Facts.deal("E-10-8-D4", TENANT, midnightDaysAgo(4));
        recompute();

        Answer first = page(WINDOW);
        assertThat(bucketDatesOf(first.dealRows()))
                .as("первая страница — ровно размер страницы, от новых суток к старым")
                .containsExactly(day(1), day(2));
        assertThat(first.nextCursor())
                .as("позиция продолжения равна ключу ПОСЛЕДНЕЙ отданной строки, а не "
                        + "смещению")
                .containsEntry(CURSOR_BUCKET_FIELD, day(2))
                .containsEntry(CURSOR_ACCOUNT_FIELD, Facts.ACCOUNT);

        // Строка, приехавшая МЕЖДУ запросами: её сутки новее курсора, то есть
        // лежат ВЫШЕ него в порядке выдачи.
        Facts.deal("E-10-8-FRESH", TENANT, midnightDaysAgo(TODAY));
        recompute();

        Answer second = page(WINDOW, cursorOf(first));

        assertThat(bucketDatesOf(second.dealRows()))
                .as("вторая страница продолжает первую: ни одной строки дважды и ни одной "
                        + "пропущенной")
                .containsExactly(day(3), day(4));
        assertThat(bucketDatesOf(second.dealRows()))
                .as("добавленная между запросами строка сдвига не вносит — позиция берётся "
                        + "КЛЮЧОМ зерна, а на смещении она сдвинула бы окно обхода")
                .doesNotContain(day(TODAY));
        assertThat(second.nextCursor()).as("после неё окно дочитано").isNull();
        assertThat(rows.count(DEAL_AGGREGATES))
                .as("вход при этом вырос: строк в базе пять, а обход прошёл по четырём, "
                        + "лежавшим на его начало")
                .isEqualTo(5L);
    }

    @Test
    @DisplayName("B10.9 — Строки с пустыми компонентами ключа из страницы не выпадают")
    void rowsWithEmptyKeyComponentsDoNotFallOutOfThePage() {
        Facts.deal("E-10-9-FULL", TENANT, Facts.STRATEGY, Bodies.CURRENCY, midnightDaysAgo(DAY));
        Facts.deal("E-10-9-NO-STRATEGY", TENANT, null, Bodies.CURRENCY, midnightDaysAgo(DAY));
        Facts.deal("E-10-9-NO-CURRENCY", TENANT, Facts.STRATEGY, null, midnightDaysAgo(DAY));
        Facts.deal("E-10-9-NEITHER", TENANT, null, null, midnightDaysAgo(DAY));
        recompute();

        List<Map<String, Object>> collected = readWholeWindow();

        assertThat(rows.count(DEAL_AGGREGATES))
                .as("вход поставлен: зерно разошлось на четыре строки одних суток")
                .isEqualTo(4L);
        assertThat(collected)
                .as("прочитано ровно столько строк, сколько собрано: ни одна строка с "
                        + "пустым компонентом ключа не потеряна")
                .hasSize(4);
        assertThat(keysOf(collected))
                .as("и порядок выдачи совпадает с порядком сравнения позиции: пустота в "
                        + "нём НАИБОЛЬШАЯ, а сравнение кортежей на ней дало бы неизвестно — "
                        + "то есть молчаливую потерю хвоста")
                .containsExactly(ABSENT + "/" + ABSENT,
                        ABSENT + "/" + Bodies.CURRENCY,
                        Facts.STRATEGY + "/" + ABSENT,
                        Facts.STRATEGY + "/" + Bodies.CURRENCY);
    }

    @Test
    @DisplayName("B10.10 — Окно дочитано: позиции продолжения нет")
    void anExhaustedWindowCarriesNoContinuation() {
        Facts.deal("E-10-10-D1", TENANT, midnightDaysAgo(1));
        Facts.deal("E-10-10-D2", TENANT, midnightDaysAgo(2));
        recompute();

        Answer full = page(WINDOW);

        assertThat(full.dealRows())
                .as("отдана полная страница — ровно её размер").hasSize(OWN_PAGE_SIZE);
        assertThat(full.nextCursor())
                .as("позиции продолжения при этом НЕТ: продолжение узнаётся лишней "
                        + "прочитанной строкой, а равенством размера странице оно обещало бы "
                        + "читателю ещё один — пустой — запрос")
                .isNull();

        // Третья строка: та же полная страница, но за нею теперь есть хвост.
        Facts.deal("E-10-10-D3", TENANT, midnightDaysAgo(3));
        recompute();

        Answer withTail = page(WINDOW);

        assertThat(withTail.dealRows())
                .as("страница та же по размеру").hasSize(OWN_PAGE_SIZE);
        assertThat(withTail.nextCursor())
                .as("а позиция продолжения появилась: различает их лишняя строка, а не "
                        + "число отданных")
                .isNotNull();
    }

    @Test
    @DisplayName("B10.17 — Размер страницы и предел окна приходят конфигурацией")
    void thePageSizeAndTheWindowLimitArriveByConfiguration() {
        Facts.deal("E-10-17-D1", TENANT, midnightDaysAgo(1));
        Facts.deal("E-10-17-D2", TENANT, midnightDaysAgo(2));
        Facts.deal("E-10-17-D3", TENANT, midnightDaysAgo(3));
        recompute();

        Answer exact = page(OWN_MAX_WINDOW_DAYS - 1);
        Answer wider = page(OWN_MAX_WINDOW_DAYS);
        Answer sharedLimit = page(StatisticsSubstrate.READ_MAX_WINDOW_DAYS - 1);

        assertThat(exact.status())
                .as("окно ровно в НАЗНАЧЕННЫЙ предел принимается").isEqualTo(200);
        assertThat(wider.asObject().get(CODE_FIELD))
                .as("шире назначенного на сутки — уже нет").isEqualTo(QUERY_REJECTED);
        assertThat(reason(wider))
                .as("и текст называет НАЗНАЧЕННЫЙ предел, а не умолчание сервиса")
                .contains("допустимого: " + OWN_MAX_WINDOW_DAYS)
                .doesNotContain(String.valueOf(StatisticsSubstrate.READ_MAX_WINDOW_DAYS));
        assertThat(sharedLimit.asObject().get(CODE_FIELD))
                .as("окно штатного предела, законное у соседей, здесь отвергнуто: граница "
                        + "приёма двигается вслед за конфигурацией, а не стои́т константой")
                .isEqualTo(QUERY_REJECTED);

        assertThat(exact.dealRows())
                .as("страница режется НАЗНАЧЕННЫМ размером, а не умолчанием сервиса")
                .hasSize(OWN_PAGE_SIZE);
        assertThat(exact.nextCursor())
                .as("и третья строка осталась за нею").isNotNull();

        // Ни то, ни другое не задаётся операндом вызова: читатель не может
        // попросить ни окна шире предела, ни страницы больше назначенной.
        Answer asked = page(OWN_MAX_WINDOW_DAYS - 1, "pageSize", String.valueOf(OWN_PAGE_SIZE + 1),
                "maxWindowDays", String.valueOf(StatisticsSubstrate.READ_MAX_WINDOW_DAYS));
        Answer askedWider = page(OWN_MAX_WINDOW_DAYS,
                "maxWindowDays", String.valueOf(StatisticsSubstrate.READ_MAX_WINDOW_DAYS));

        assertThat(asked.dealRows())
                .as("запрошенный размер страницы не читается: строк по-прежнему назначенное "
                        + "число — значит, число живёт в ОДНОМ месте")
                .hasSize(OWN_PAGE_SIZE);
        assertThat(askedWider.asObject().get(CODE_FIELD))
                .as("запрошенный предел окна не читается тоже").isEqualTo(QUERY_REJECTED);
    }

    /**
     * Страница сделочного зерна за окно названной ширины с операндами сверх
     * него.
     *
     * @param daysBack сколько суток назад открывается окно; правая граница —
     *                 нынешние сутки
     * @param extra    пары «имя операнда, значение» сверх зерна и окна
     */
    private Answer page(Integer daysBack, String... extra) {
        String[] operands = new String[6 + extra.length];
        operands[0] = GRAIN;
        operands[1] = DEAL_GRAIN;
        operands[2] = FROM;
        operands[3] = day(daysBack);
        operands[4] = TO;
        operands[5] = day(0);
        System.arraycopy(extra, 0, operands, 6, extra.length);
        return get(aggregatePath(operands), TENANT);
    }

    /**
     * Весь ряд окна, прочитанный страницами по отдаваемым позициям.
     *
     * <p><b>Обход ограничен потолком страниц:</b> курсор, не двигающийся
     * вперёд, дал бы вечный цикл, и клетка не упала бы, а висела.
     */
    private List<Map<String, Object>> readWholeWindow() {
        List<Map<String, Object>> collected = new ArrayList<>();
        Answer page = page(WINDOW);
        for (int read = 0; read < PAGE_CEILING; read++) {
            collected.addAll(page.dealRows());
            if (nonNull(page.nextCursor())) {
                page = page(WINDOW, cursorOf(page));
                continue;
            }
            return collected;
        }
        throw new AssertionError("Обход окна не кончился за " + PAGE_CEILING + " страниц: "
                + "позиция продолжения не двигается вперёд");
    }

    /**
     * Операнды позиции, собранные из отданной страницей.
     *
     * <p><b>Пустые компоненты не называются вовсе, и это не упрощение:</b> у
     * них пустота есть ЗНАЧЕНИЕ ключа, а операнд без значения и отсутствующий
     * операнд для вопроса одно и то же.
     *
     * @param page страница, чью позицию продолжает следующий вопрос
     */
    private static String[] cursorOf(Answer page) {
        Map<String, Object> cursor = page.nextCursor();
        List<String> operands = new ArrayList<>(List.of(
                CURSOR_BUCKET, String.valueOf(cursor.get(CURSOR_BUCKET_FIELD)),
                CURSOR_ACCOUNT, String.valueOf(cursor.get(CURSOR_ACCOUNT_FIELD))));
        if (nonNull(cursor.get(CURSOR_STRATEGY_FIELD))) {
            operands.add(CURSOR_STRATEGY);
            operands.add(String.valueOf(cursor.get(CURSOR_STRATEGY_FIELD)));
        }
        if (nonNull(cursor.get(CURSOR_CURRENCY_FIELD))) {
            operands.add(CURSOR_CURRENCY);
            operands.add(String.valueOf(cursor.get(CURSOR_CURRENCY_FIELD)));
        }
        return operands.toArray(new String[0]);
    }

    /** Ключи зерна прочитанных строк записью «определение/валюта». */
    private static List<String> keysOf(List<Map<String, Object>> collected) {
        return collected.stream()
                .map(row -> component(row, CURSOR_STRATEGY_FIELD)
                        + "/" + component(row, CURSOR_CURRENCY_FIELD))
                .toList();
    }

    /** Компонент ключа записью; пустой — своей записью, а не пустым местом. */
    private static String component(Map<String, Object> row, String field) {
        return nonNull(row.get(field)) ? String.valueOf(row.get(field)) : ABSENT;
    }

    /** Пояснение отказа: им читается ПОВОД, тогда как класс читается кодом. */
    private static String reason(Answer answer) {
        return String.valueOf(answer.asObject().get(MESSAGE_FIELD));
    }
}
