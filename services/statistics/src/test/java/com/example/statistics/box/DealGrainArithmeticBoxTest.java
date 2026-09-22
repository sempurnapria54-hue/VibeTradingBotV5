package com.example.statistics.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетки {@code B8.1} — {@code B8.19}, кроме {@code B8.16}: арифметика
 * сделочного зерна
 * (.claude/tests/cases/statistics.md §«B8 — Арифметика сделочного зерна»).
 *
 * <p><b>Класс равен ГРУППЕ, и делить её нечем.</b> Все восемнадцать
 * прогоняемых клеток берут штатное положение осей контекста — окно пересчёта
 * величиной субстрата, выключатель снят не у одной, — ничего у субстрата не
 * отнимают и расходятся только тем, какие факты каждая себе кладёт. Оси
 * конфигурации, которая развела группу {@code B7} на восемь классов, здесь
 * нет ни у одной клетки, и заведение второго контекста объявило бы ось,
 * которой группа не ставит.
 *
 * <p><b>{@code B8.16} здесь нет, и это не пропуск.</b> Её состояние требует
 * производителя, не приславшего плановый риск, а построенный везёт его
 * всегда; дома у ветви сегодня нет вовсе — находка {@code F-3}
 * (.claude/tests/cases/statistics.md §«Кейсы, не прогоняемые сегодня»).
 * Написанная сейчас, клетка утверждала бы выдуманное ожидание.
 *
 * <p><b>Вход у всей группы один по форме:</b> строки {@code deal_facts}
 * кладутся прямой записью ({@link DealDraft}) — поверхности у факта нет
 * вовсе, — подаётся такт пересчёта, а числа читаются страницей агрегатов.
 * Ассерт по колонкам факта здесь был бы ПОДАЧЕЙ, а не проверкой, и потому
 * его нет ни у одной клетки: проверяется то, что отдала поверхность.
 *
 * <p><b>Факты лежат в ПОЛНОЧЬ своих суток, и это не стиль.</b> Проход не
 * пишет суток, начавшихся раньше первого факта ряда
 * ({@code dayRecomputable}), поэтому факт, положенный в середину суток,
 * оставил бы эти сутки непокрытыми частично — строки не появилось бы вовсе,
 * и клетка краснела бы по охране отбора, а не по своему предмету.
 *
 * <p><b>Умолчания заготовки — ЗДОРОВАЯ сделка</b> ({@link DealDraft}), и
 * клетка называет ровно то, чем её факт от здорового отличается. Отсюда
 * ожидание «счётчик стои́т на нуле» читается как «операнд подан здоровым», а
 * не как «операнд не подан».
 *
 * <p><b>Своя группа и своя тема взяты по общему доводу класса кейсов:</b>
 * контексты прогона не закрываются, а группа есть состояние на брокере.
 * Производителя в этих клетках нет ни одного, и тема их остаётся пустой.
 *
 * <p><b>Денежные суммы читаются ЛИТЕРАЛОМ тела там, где строка одна</b>
 * ({@link Answer#number}): разобранная карта отдала бы двоичный тип с
 * плавающей точкой, а суммы едут десятичной записью
 * (docs/rules/decimal-arithmetic.md). Где строк несколько, литерал
 * неадресуем — тело несёт одно имя поля у каждой строки, — и сумма берётся
 * у разобранной строки {@link #money}; значения операндов таких клеток
 * выбраны короткой десятичной записью, чтобы разбор их не округлял.
 */
class DealGrainArithmeticBoxTest extends StatisticsBox {

    /** Краткое имя класса: из него строятся его группа и его тема. */
    private static final String SLUG = "b8-arithmetic";

    /** Сутки, в которых лежат факты почти всех клеток группы. */
    private static final Integer DAY = 1;

    /** Соседние сутки: ими клетка о ключах зерна мерит рез по суткам. */
    private static final Integer EARLIER_DAY = 2;

    /** Накопленное финансирование, не двигающее ценового результата. */
    private static final String NO_FUNDING = "0";

    /** Комиссия, не двигающая ни одной суммы. */
    private static final String NO_FEE = "0";

    /** Второй биржевой счёт — второе значение первого ключа зерна. */
    private static final String SECOND_ACCOUNT = "ACCOUNT-2";

    /** Второе определение стратегии — второе значение второго ключа зерна. */
    private static final String SECOND_STRATEGY = "S-2";

    /** Вторая расчётная валюта — второе значение третьего ключа зерна. */
    private static final String SECOND_CURRENCY = "USDC";

    /** Поле популяции всех долей. */
    private static final String RISK_BEARING_DEALS = "riskBearingDeals";

    /** Поле счётчика выигравших. */
    private static final String WINNING_DEALS = "winningDeals";

    /** Поле счётчика проигравших. */
    private static final String LOSING_DEALS = "losingDeals";

    /** Поле счётчика нулевых. */
    private static final String NEUTRAL_DEALS = "neutralDeals";

    /** Поле счётчика сделок с недоступным результатом. */
    private static final String RESULT_UNAVAILABLE_DEALS = "resultUnavailableDeals";

    /** Поле счётчика сделок с нерезолвленной валютой. */
    private static final String CURRENCY_UNRESOLVED_DEALS = "currencyUnresolvedDeals";

    /** Поле счётчика сделок с нулевым плановым риском. */
    private static final String RISK_UNSIZED_DEALS = "riskUnsizedDeals";

    /** Поле счётчика закрытых биржей по марже. */
    private static final String LIQUIDATED_DEALS = "liquidatedDeals";

    /** Поле счётчика принудительно сокращённых. */
    private static final String FORCED_REDUCTION_DEALS = "forcedReductionDeals";

    /** Поле счётчика с неустановленным исходом. */
    private static final String OUTCOME_UNDETERMINED_DEALS = "outcomeUndeterminedDeals";

    /** Поле счётчика с несошедшейся сверкой. */
    private static final String MISMATCHED_DEALS = "reconciliationMismatchedDeals";

    /** Поле счётчика с непроверенной сверкой. */
    private static final String NOT_RUN_DEALS = "reconciliationNotRunDeals";

    /** Поле счётчика с неполной разбивкой. */
    private static final String BREAKDOWN_INCOMPLETE_DEALS = "breakdownIncompleteDeals";

    /** Поле счётчика с неоценённой полнотой разбивки. */
    private static final String BREAKDOWN_NOT_ASSESSED_DEALS = "breakdownNotAssessedDeals";

    /** Поле счётчика с потерянной базой риска. */
    private static final String RISK_BENCHMARK_MISSING_DEALS = "riskBenchmarkMissingDeals";

    /** Поле знаменателя суммы R-мультипликаторов. */
    private static final String R_DENOMINATOR_DEALS = "rDenominatorDeals";

    /** Поле суммы результатов ДО накопленного финансирования. */
    private static final String RESULT_BEFORE_FUNDING_SUM = "resultBeforeFundingSum";

    /** Поле суммы итогов. */
    private static final String NET_RESULT_SUM = "netResultSum";

    /** Поле суммы комиссий. */
    private static final String FEE_SUM = "feeSum";

    /** Поле суммы накопленного финансирования. */
    private static final String FUNDING_SUM = "fundingSum";

    /** Поле суммы штрафов принудительного закрытия. */
    private static final String LIQUIDATION_PENALTY_SUM = "liquidationPenaltySum";

    /** Поле суммы выигрышей. */
    private static final String WIN_RESULT_SUM = "winResultSum";

    /** Поле суммы убытков. */
    private static final String LOSS_RESULT_SUM = "lossResultSum";

    /** Поле суммы планового риска вошедших в денежные суммы. */
    private static final String PLANNED_RISK_SUM = "plannedRiskSum";

    /** Поле суммы планового риска выведенных из денежных сумм. */
    private static final String PLANNED_RISK_EXCLUDED_SUM = "plannedRiskExcludedSum";

    /** Поле суммы R-мультипликаторов. */
    private static final String R_SUM = "rSum";

    /** Поле расчётной валюты — компонента ключа зерна в выдаче. */
    private static final String RESULT_CURRENCY = "resultCurrency";

    /** Поле биржевого счёта — компонента ключа зерна в выдаче. */
    private static final String ACCOUNT_FIELD = "exchangeAccountInternalId";

    /** Поле определения стратегии — компонента ключа зерна в выдаче. */
    private static final String STRATEGY_FIELD = "strategyInternalId";

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        StatisticsSubstrate.registerOwn(registry, SLUG);
    }

    @Test
    @DisplayName("B8.1 — Закрытых сделок считаются все, а доли — только от торговавших")
    void allClosedDealsAreCountedWhileSharesTakeOnlyTheOnesThatTraded() {
        DealDraft.of("E-8-1-WIN", TENANT, midnightDaysAgo(DAY))
                .netResult("10").funding(NO_FUNDING).build().put();
        DealDraft.of("E-8-1-LOSS", TENANT, midnightDaysAgo(DAY))
                .netResult("-4").funding(NO_FUNDING).build().put();
        // Сделка, закрытая БЕЗ входа: позиции не было, поэтому риск не
        // принимался, знаменателя R нет по построению, а сверка не была
        // обязана. Результат у неё положительный — и он не должен попасть
        // ни в счётчик долей, ни в денежную сумму.
        DealDraft.of("E-8-1-NO-RISK", TENANT, midnightDaysAgo(DAY))
                .tookRisk(Boolean.FALSE)
                .netResult("7").funding(NO_FUNDING).plannedRisk("0")
                .reconciliationStatus(Bodies.NOT_RUN)
                .riskBenchmarkAvailability(Bodies.NOT_APPLICABLE)
                .build().put();

        recompute();

        Answer page = aggregates(DEAL_GRAIN, TENANT);
        Map<String, Object> row = rowOf(page.dealRows(), DAY);
        assertThat(row.get(CLOSED_DEALS))
                .as("закрытых считаются ВСЕ три: сколько кандидатов дошло до терминала — "
                        + "самостоятельный факт").isEqualTo(3);
        assertThat(row.get(RISK_BEARING_DEALS))
                .as("а популяция долей — только две торговавшие").isEqualTo(2);
        assertThat(row.get(WINNING_DEALS))
                .as("выигравшая одна: у неторговавшей результат тоже положителен, и без "
                        + "охраны популяции их было бы две").isEqualTo(1);
        assertThat(row.get(LOSING_DEALS)).as("проигравшая одна").isEqualTo(1);
        assertThat(row.get(RISK_UNSIZED_DEALS))
                .as("нулевой плановый риск неторговавшей своего счётчика не двигает: "
                        + "популяция и у него — принявшие риск").isEqualTo(0);
        assertThat(row.get(NOT_RUN_DEALS))
                .as("и непроверенная сверка неторговавшей — тоже").isEqualTo(0);
        assertThat(page.number(NET_RESULT_SUM))
                .as("в денежную сумму вошли две торговавшие: 10 и -4, а не 13 с третьей")
                .isEqualByComparingTo(new BigDecimal("6"));
    }

    @Test
    @DisplayName("B8.2 — Ценовой результат складывается пересчётом, а колонкой не хранится")
    void thePriceResultIsComposedByTheRecomputeRatherThanStoredAsAColumn() {
        DealDraft.of("E-8-2", TENANT, midnightDaysAgo(DAY))
                .netResult("10").funding("2").build().put();

        recompute();

        Answer page = aggregates(DEAL_GRAIN, TENANT);
        assertThat(page.dealRows()).as("строка собрана, и она одна").hasSize(1);
        assertThat(page.number(RESULT_BEFORE_FUNDING_SUM))
                .as("результат до финансирования есть сумма итога и финансирования")
                .isEqualByComparingTo(new BigDecimal("12"));
        assertThat(page.number(NET_RESULT_SUM))
                .as("итог едет отдельной суммой").isEqualByComparingTo(new BigDecimal("10"));
        assertThat(page.number(RESULT_BEFORE_FUNDING_SUM).subtract(page.number(NET_RESULT_SUM)))
                .as("разность двух сумм равна накопленному финансированию — охранный "
                        + "инвариант разложения")
                .isEqualByComparingTo(page.number(FUNDING_SUM));
        assertThat(rows.columnNames(DEAL_FACTS))
                .as("колонки ценового результата у факта нет ни одной: величина выводится "
                        + "при свёртке и вторым носителем не заводится")
                .noneMatch(name -> name.contains("price") || name.contains("before_funding"));
    }

    @Test
    @DisplayName("B8.3 — Ложный признак полноты графа даёт НЕДОСТУПНОСТЬ, а не ноль")
    void anIncompleteGraphYieldsUnavailabilityRatherThanZero() {
        DealDraft.of("E-8-3-COMPLETE", TENANT, midnightDaysAgo(DAY))
                .netResult("10").funding(NO_FUNDING).build().put();
        DealDraft.of("E-8-3-INCOMPLETE", TENANT, midnightDaysAgo(DAY))
                .graphComplete(Boolean.FALSE)
                .netResult("10").funding(NO_FUNDING).build().put();

        recompute();

        Answer page = aggregates(DEAL_GRAIN, TENANT);
        Map<String, Object> row = rowOf(page.dealRows(), DAY);
        assertThat(row.get(WINNING_DEALS))
                .as("первая попала в класс по знаку своего результата").isEqualTo(1);
        assertThat(row.get(RESULT_UNAVAILABLE_DEALS))
                .as("вторая — в недоступность: признак полноты графа ложен, а итог есть")
                .isEqualTo(1);
        assertThat(row.get(NEUTRAL_DEALS))
                .as("нуля на месте её результата не появилось: подстановка нуля выдала бы "
                        + "недобытое за исход").isEqualTo(0);
        assertThat(page.number(NET_RESULT_SUM))
                .as("и в денежную сумму она не вошла: 10, а не 20")
                .isEqualByComparingTo(new BigDecimal("10"));
        assertThat(page.number(RESULT_BEFORE_FUNDING_SUM))
                .as("вторая денежная сумма собрана по той же популяции")
                .isEqualByComparingTo(new BigDecimal("10"));
    }

    @Test
    @DisplayName("B8.4 — Отсутствующий итог тоже даёт недоступность")
    void anAbsentNetResultYieldsUnavailabilityToo() {
        DealDraft.of("E-8-4", TENANT, midnightDaysAgo(DAY))
                .netResult(null).funding(NO_FUNDING).build().put();

        recompute();

        Answer page = aggregates(DEAL_GRAIN, TENANT);
        Map<String, Object> row = rowOf(page.dealRows(), DAY);
        assertThat(row.get(RESULT_UNAVAILABLE_DEALS))
                .as("сделка в недоступности: пустой итог — второй конъюнкт доступности")
                .isEqualTo(1);
        assertThat(row.get(RISK_BEARING_DEALS))
                .as("из популяции долей она при этом не выпала").isEqualTo(1);
        assertThat(page.number(NET_RESULT_SUM))
                .as("в денежные суммы не вошла: сумма пустого множества — ноль")
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(page.number(RESULT_BEFORE_FUNDING_SUM))
                .as("и во вторую тоже").isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(row)
                .as("счётчик стои́т РЯДОМ с суммой, в той же строке: выпадение поэтому не "
                        + "молчаливо")
                .containsKeys(RESULT_UNAVAILABLE_DEALS, NET_RESULT_SUM, RESULT_BEFORE_FUNDING_SUM);
    }

    @Test
    @DisplayName("B8.5 — Классы результата: положительный, отрицательный, нулевой")
    void theThreeResultClassesArePositiveNegativeAndZero() {
        DealDraft.of("E-8-5-POSITIVE", TENANT, midnightDaysAgo(DAY))
                .netResult("10").funding(NO_FUNDING).build().put();
        DealDraft.of("E-8-5-NEGATIVE", TENANT, midnightDaysAgo(DAY))
                .netResult("-10").funding(NO_FUNDING).build().put();
        DealDraft.of("E-8-5-ZERO", TENANT, midnightDaysAgo(DAY))
                .netResult("0").funding(NO_FUNDING).build().put();

        recompute();

        Map<String, Object> row = rowOf(aggregates(DEAL_GRAIN, TENANT).dealRows(), DAY);
        assertThat(row.get(WINNING_DEALS)).as("строго положительный — выигрыш").isEqualTo(1);
        assertThat(row.get(LOSING_DEALS)).as("строго отрицательный — убыток").isEqualTo(1);
        assertThat(row.get(NEUTRAL_DEALS))
                .as("нулевой — свой класс, а в выигрышные он не идёт").isEqualTo(1);
        assertThat(classesOf(row))
                .as("три класса плюс недоступность разбивают популяцию долей без остатка")
                .isEqualTo(row.get(RISK_BEARING_DEALS)).isEqualTo(3);
    }

    @Test
    @DisplayName("B8.6 — Выигрышность меряется результатом ДО финансирования")
    void winningIsMeasuredByTheResultBeforeFunding() {
        // Итог отрицателен, а издержка carry положительна и по модулю больше
        // итога: результат до финансирования выходит положительным.
        DealDraft.of("E-8-6", TENANT, midnightDaysAgo(DAY))
                .netResult("-1").funding("3").build().put();

        recompute();

        Answer page = aggregates(DEAL_GRAIN, TENANT);
        Map<String, Object> row = rowOf(page.dealRows(), DAY);
        assertThat(row.get(WINNING_DEALS))
                .as("класс взят по результату ДО финансирования — тому же числу, каким "
                        + "двигается счётчик серии").isEqualTo(1);
        assertThat(row.get(LOSING_DEALS))
                .as("по итогу она была бы убыточной, и два числа об одном предмете "
                        + "разошлись бы с энфорсером серии").isEqualTo(0);
        assertThat(page.number(NET_RESULT_SUM))
                .as("итог при этом не прячется и остаётся отрицательным")
                .isEqualByComparingTo(new BigDecimal("-1"));
        assertThat(page.number(RESULT_BEFORE_FUNDING_SUM))
                .as("а результат до финансирования положителен")
                .isEqualByComparingTo(new BigDecimal("2"));
    }

    @Test
    @DisplayName("B8.7 — Нерезолвленная валюта даёт СВОЮ строку зерна с нулевыми суммами")
    void anUnresolvedCurrencyYieldsItsOwnGrainRowWithZeroMoneySums() {
        DealDraft.of("E-8-7-KNOWN-A", TENANT, midnightDaysAgo(DAY))
                .netResult("10").funding(NO_FUNDING).build().put();
        DealDraft.of("E-8-7-KNOWN-B", TENANT, midnightDaysAgo(DAY))
                .netResult("4").funding(NO_FUNDING).build().put();
        DealDraft.of("E-8-7-UNRESOLVED", TENANT, midnightDaysAgo(DAY))
                .currency(null).netResult("7").funding(NO_FUNDING).build().put();

        recompute();

        List<Map<String, Object>> handed = aggregates(DEAL_GRAIN, TENANT).dealRows();
        assertThat(handed).as("строк зерна две: с ключом валюты и с пустым ключом").hasSize(2);
        Map<String, Object> known = rowOfCurrency(handed, Bodies.CURRENCY);
        Map<String, Object> unresolved = rowOfCurrency(handed, null);
        assertThat(unresolved.get(CLOSED_DEALS))
                .as("счётчики строки пустой валюты непусты: сделка в ней есть").isEqualTo(1);
        assertThat(unresolved.get(CURRENCY_UNRESOLVED_DEALS))
                .as("и популяция её видна своим счётчиком — молча она не выпадает")
                .isEqualTo(1);
        assertThat(moneyFieldsOf(unresolved))
                .as("денежные суммы строки пустой валюты нулевые ВСЕ до одной, включая "
                        + "плановый риск: складывать числа без единицы измерения нельзя")
                .allSatisfy(sum -> assertThat(sum).isEqualByComparingTo(BigDecimal.ZERO));
        assertThat(money(known, NET_RESULT_SUM))
                .as("строка известной валюты собрана из своих двух сделок")
                .isEqualByComparingTo(new BigDecimal("14"));
        assertThat(known.get(CURRENCY_UNRESOLVED_DEALS))
                .as("а её счётчик нерезолвленных пуст").isEqualTo(0);
    }

    @Test
    @DisplayName("B8.8 — Строка собрана из сделок одной валюты либо целиком из пустых")
    void aRowIsBuiltFromOneCurrencyOrEntirelyFromEmptyOnes() {
        DealDraft.of("E-8-8-USDT", TENANT, midnightDaysAgo(DAY))
                .netResult("10").funding(NO_FUNDING).build().put();
        DealDraft.of("E-8-8-USDC", TENANT, midnightDaysAgo(DAY))
                .currency(SECOND_CURRENCY).netResult("4").funding(NO_FUNDING).build().put();
        DealDraft.of("E-8-8-EMPTY", TENANT, midnightDaysAgo(DAY))
                .currency(null).netResult("7").funding(NO_FUNDING).build().put();

        recompute();

        List<Map<String, Object>> handed = aggregates(DEAL_GRAIN, TENANT).dealRows();
        assertThat(handed)
                .as("строк столько, сколько РАЗЛИЧНЫХ значений валюты, включая пустое")
                .hasSize(3);
        assertThat(handed.stream().map(row -> row.get(RESULT_CURRENCY)).toList())
                .as("и значения эти — ровно поданные")
                .containsExactlyInAnyOrder(Bodies.CURRENCY, SECOND_CURRENCY, null);
        assertThat(handed).as("ни одна строка не смешала валюты: в каждой одна сделка")
                .allSatisfy(row -> assertThat(row.get(CLOSED_DEALS)).isEqualTo(1));
        assertThat(money(rowOfCurrency(handed, Bodies.CURRENCY), NET_RESULT_SUM))
                .as("сумма первой валюты — только её сделка")
                .isEqualByComparingTo(new BigDecimal("10"));
        assertThat(money(rowOfCurrency(handed, SECOND_CURRENCY), NET_RESULT_SUM))
                .as("сумма второй — только её").isEqualByComparingTo(new BigDecimal("4"));
        assertThat(handed.stream().map(row -> money(row, NET_RESULT_SUM)).toList())
                .as("склейки результатов разных валют в одну клетку нет нигде: ни 14, ни 21")
                .noneMatch(sum -> sum.compareTo(new BigDecimal("14")) == 0
                        || sum.compareTo(new BigDecimal("21")) == 0);
    }

    @Test
    @DisplayName("B8.9 — Суммы выигрышей и убытков хранятся по отдельности")
    void winAndLossSumsAreStoredSeparately() {
        DealDraft.of("E-8-9-WIN-A", TENANT, midnightDaysAgo(DAY))
                .netResult("10").funding(NO_FUNDING).build().put();
        DealDraft.of("E-8-9-WIN-B", TENANT, midnightDaysAgo(DAY))
                .netResult("5").funding(NO_FUNDING).build().put();
        DealDraft.of("E-8-9-LOSS", TENANT, midnightDaysAgo(DAY))
                .netResult("-3").funding(NO_FUNDING).build().put();
        DealDraft.of("E-8-9-ZERO", TENANT, midnightDaysAgo(DAY))
                .netResult("0").funding(NO_FUNDING).build().put();

        recompute();

        Answer page = aggregates(DEAL_GRAIN, TENANT);
        assertThat(page.dealRows()).as("строка одна").hasSize(1);
        assertThat(page.number(WIN_RESULT_SUM))
                .as("сумма выигрышей отдельно: без неё профит-фактор не вычислим никак")
                .isEqualByComparingTo(new BigDecimal("15"));
        assertThat(page.number(LOSS_RESULT_SUM))
                .as("сумма убытков отдельно, и знак у неё сохранён")
                .isEqualByComparingTo(new BigDecimal("-3"));
        assertThat(page.number(WIN_RESULT_SUM).add(page.number(LOSS_RESULT_SUM)))
                .as("их сумма равна результату до финансирования: нулевые вносят ноль")
                .isEqualByComparingTo(page.number(RESULT_BEFORE_FUNDING_SUM))
                .isEqualByComparingTo(new BigDecimal("12"));
        assertThat(page.body())
                .as("а профит-фактор и ожидаемость поверхностью НЕ считаются: производные "
                        + "вычисляет потребитель выдачи")
                .doesNotContain("profitFactor", "expectancy", "winRate");
    }

    @Test
    @DisplayName("B8.10 — Комиссия и штраф показаны, но из сумм не вычитаются второй раз")
    void feeAndPenaltyAreShownButNotSubtractedTwice() {
        DealDraft.of("E-8-10", TENANT, midnightDaysAgo(DAY))
                .netResult("10").funding("2").fee("0.4").liquidationPenalty("1.5")
                .build().put();

        recompute();

        Answer page = aggregates(DEAL_GRAIN, TENANT);
        assertThat(page.dealRows()).as("строка одна").hasSize(1);
        assertThat(page.number(FEE_SUM))
                .as("комиссия показана").isEqualByComparingTo(new BigDecimal("0.4"));
        assertThat(page.number(LIQUIDATION_PENALTY_SUM))
                .as("и штраф принудительного закрытия тоже")
                .isEqualByComparingTo(new BigDecimal("1.5"));
        assertThat(page.number(NET_RESULT_SUM))
                .as("итог при этом равен поданному: комиссия и штраф в него УЖЕ вошли "
                        + "вычтенными, и второго вычитания нет — иначе было бы 8.1")
                .isEqualByComparingTo(new BigDecimal("10"));
        assertThat(page.number(RESULT_BEFORE_FUNDING_SUM))
                .as("вторая денежная сумма — тоже без повторного вычитания")
                .isEqualByComparingTo(new BigDecimal("12"));
        assertThat(page.number(RESULT_BEFORE_FUNDING_SUM).subtract(page.number(NET_RESULT_SUM)))
                .as("и разность двух сумм по-прежнему равна финансированию")
                .isEqualByComparingTo(page.number(FUNDING_SUM));
    }

    @Test
    @DisplayName("B8.11 — Сумма R и её знаменатель хранятся обе")
    void bothTheRSumAndItsDenominatorAreStored() {
        DealDraft.of("E-8-11-A", TENANT, midnightDaysAgo(DAY))
                .netResult("10").funding(NO_FUNDING).plannedRisk("5").build().put();
        DealDraft.of("E-8-11-B", TENANT, midnightDaysAgo(DAY))
                .netResult("-3").funding(NO_FUNDING).plannedRisk("6").build().put();

        recompute();

        Answer page = aggregates(DEAL_GRAIN, TENANT);
        assertThat(page.dealRows()).as("строка одна").hasSize(1);
        assertThat(page.number(R_SUM))
                .as("сумма ЧАСТНЫХ по каждой сделке: 10/5 плюс -3/6; частное сумм дало бы "
                        + "7/11 и было бы другим числом")
                .isEqualByComparingTo(new BigDecimal("1.5"));
        assertThat(rowOf(page.dealRows(), DAY).get(R_DENOMINATOR_DEALS))
                .as("знаменатель хранится рядом: без него сумма не делится ни на что")
                .isEqualTo(2);
        assertThat(page.number(PLANNED_RISK_SUM))
                .as("плановый риск вошедших сложен отдельно")
                .isEqualByComparingTo(new BigDecimal("11"));
        assertThat(page.body())
                .as("средний R поверхностью не считается: его выводит потребитель выдачи")
                .doesNotContain("averageR");
    }

    @Test
    @DisplayName("B8.12 — Нулевой плановый риск имеет свой счётчик и в знаменатель не идёт")
    void aZeroPlannedRiskHasItsOwnCounterAndStaysOutOfTheDenominator() {
        DealDraft.of("E-8-12", TENANT, midnightDaysAgo(DAY))
                .netResult("10").funding(NO_FUNDING).plannedRisk("0").build().put();

        recompute();

        Answer page = aggregates(DEAL_GRAIN, TENANT);
        Map<String, Object> row = rowOf(page.dealRows(), DAY);
        assertThat(row.get(RISK_UNSIZED_DEALS))
                .as("признак — НОЛЬ, а не пустота: сайзинга на её тропе не было")
                .isEqualTo(1);
        assertThat(row.get(R_DENOMINATOR_DEALS))
                .as("в знаменатель R она не вошла").isEqualTo(0);
        assertThat(page.number(R_SUM))
                .as("и в сумму R тоже — делением на ноль проход не упал")
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(page.number(PLANNED_RISK_SUM))
                .as("нулевой риск в сумму планового риска не идёт")
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(page.number(NET_RESULT_SUM))
                .as("а в денежные суммы сделка ВОШЛА: признак сделку из них не выводит")
                .isEqualByComparingTo(new BigDecimal("10"));
    }

    @Test
    @DisplayName("B8.13 — Плановый риск выведенных из сумм сделок считается отдельно")
    void thePlannedRiskOfExcludedDealsIsSummedSeparately() {
        DealDraft.of("E-8-13", TENANT, midnightDaysAgo(DAY))
                .graphComplete(Boolean.FALSE)
                .netResult("10").funding(NO_FUNDING).plannedRisk("7").build().put();

        recompute();

        Answer page = aggregates(DEAL_GRAIN, TENANT);
        Map<String, Object> row = rowOf(page.dealRows(), DAY);
        assertThat(row.get(RESULT_UNAVAILABLE_DEALS))
                .as("сделка выведена из денежных сумм недоступностью результата")
                .isEqualTo(1);
        assertThat(page.number(PLANNED_RISK_EXCLUDED_SUM))
                .as("её плановый риск сложен СВОЕЙ величиной: читателю нужен объём "
                        + "непокрытого риска, а не только счётчик")
                .isEqualByComparingTo(new BigDecimal("7"));
        assertThat(page.number(PLANNED_RISK_SUM))
                .as("в знаменатель отношения он при этом не попал")
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(row.get(RESULT_CURRENCY))
                .as("величина считается на строке ИЗВЕСТНОЙ валюты: у строки пустой валюты "
                        + "единицы измерения нет ни при каком исходе резолва")
                .isEqualTo(Bodies.CURRENCY);
    }

    @Test
    @DisplayName("B8.14 — Каждое значение признака отбора получает свой счётчик")
    void everyValueOfASelectionFlagGetsItsOwnCounter() {
        DealDraft.of("E-8-14-LIQUIDATION", TENANT, midnightDaysAgo(DAY))
                .closeOutcome(Bodies.LIQUIDATION).netResult("1").funding(NO_FUNDING)
                .build().put();
        DealDraft.of("E-8-14-FORCED", TENANT, midnightDaysAgo(DAY))
                .closeOutcome(Bodies.FORCED_REDUCTION).netResult("1").funding(NO_FUNDING)
                .build().put();
        DealDraft.of("E-8-14-UNDETERMINED", TENANT, midnightDaysAgo(DAY))
                .closeOutcome(Bodies.UNDETERMINED).netResult("1").funding(NO_FUNDING)
                .build().put();
        DealDraft.of("E-8-14-MISMATCHED", TENANT, midnightDaysAgo(DAY))
                .reconciliationStatus(Bodies.MISMATCHED).netResult("1").funding(NO_FUNDING)
                .build().put();
        DealDraft.of("E-8-14-NOT-RUN", TENANT, midnightDaysAgo(DAY))
                .reconciliationStatus(Bodies.NOT_RUN).netResult("1").funding(NO_FUNDING)
                .build().put();
        DealDraft.of("E-8-14-INCOMPLETE", TENANT, midnightDaysAgo(DAY))
                .breakdownIncomplete(Bodies.INCOMPLETE_BY_WINDOW).netResult("1")
                .funding(NO_FUNDING).build().put();
        // Неоценённая полнота разбивки идёт с НЕДОСТУПНЫМ результатом: иначе
        // факт нарушал бы охранный инвариант unassessedBreakdownHasNoResult.
        // Ею же и мерится популяция счётчика — принявшие риск, а не вошедшие
        // в денежные суммы.
        DealDraft.of("E-8-14-NOT-ASSESSED", TENANT, midnightDaysAgo(DAY))
                .breakdownIncomplete(Bodies.NOT_ASSESSED).netResult(null)
                .funding(NO_FUNDING).build().put();
        DealDraft.of("E-8-14-BENCHMARK", TENANT, midnightDaysAgo(DAY))
                .riskBenchmarkAvailability(Bodies.MISSING).netResult("1").funding(NO_FUNDING)
                .build().put();
        DealDraft.of("E-8-14-HEALTHY", TENANT, midnightDaysAgo(DAY))
                .netResult("1").funding(NO_FUNDING).build().put();

        recompute();

        Answer page = aggregates(DEAL_GRAIN, TENANT);
        Map<String, Object> row = rowOf(page.dealRows(), DAY);
        assertThat(row.get(LIQUIDATED_DEALS)).as("закрытие по марже").isEqualTo(1);
        assertThat(row.get(FORCED_REDUCTION_DEALS)).as("принудительное сокращение").isEqualTo(1);
        assertThat(row.get(OUTCOME_UNDETERMINED_DEALS)).as("неустановленный исход").isEqualTo(1);
        assertThat(row.get(MISMATCHED_DEALS)).as("несошедшаяся сверка").isEqualTo(1);
        assertThat(row.get(NOT_RUN_DEALS)).as("непроверенная сверка").isEqualTo(1);
        assertThat(row.get(BREAKDOWN_INCOMPLETE_DEALS)).as("неполная разбивка").isEqualTo(1);
        assertThat(row.get(BREAKDOWN_NOT_ASSESSED_DEALS))
                .as("неоценённая полнота разбивки — и сделка эта в денежные суммы НЕ вошла: "
                        + "популяция счётчика шире, чем популяция сумм").isEqualTo(1);
        assertThat(row.get(RISK_BENCHMARK_MISSING_DEALS)).as("потерянная база риска").isEqualTo(1);
        assertThat(row.get(RESULT_UNAVAILABLE_DEALS))
                .as("вход поставлен: результат недоступен ровно у неё").isEqualTo(1);
        assertThat(row.get(CLOSED_DEALS)).as("сделок девять").isEqualTo(9);
        assertThat(page.body())
                .as("здоровое значение своего счётчика не имеет: его число есть ОСТАТОК")
                .doesNotContain("normalExitDeals", "reconciliationMatchedDeals",
                        "breakdownCompleteDeals", "riskBenchmarkAvailableDeals");
        assertThat(healthyOutcomesOf(row))
                .as("и остаток этот читается вычитанием: девять минус три исхода закрытия")
                .isEqualTo(6);
    }

    @Test
    @DisplayName("B8.15 — Признак ненадёжности сделку из сумм не выводит")
    void anUnreliabilityFlagDoesNotRemoveTheDealFromTheSums() {
        DealDraft.of("E-8-15-MISMATCHED", TENANT, midnightDaysAgo(DAY))
                .reconciliationStatus(Bodies.MISMATCHED).netResult("10").funding(NO_FUNDING)
                .build().put();
        DealDraft.of("E-8-15-NOT-RUN", TENANT, midnightDaysAgo(DAY))
                .reconciliationStatus(Bodies.NOT_RUN).netResult("5").funding(NO_FUNDING)
                .build().put();
        DealDraft.of("E-8-15-MATCHED", TENANT, midnightDaysAgo(DAY))
                .netResult("3").funding(NO_FUNDING).build().put();

        recompute();

        Answer page = aggregates(DEAL_GRAIN, TENANT);
        Map<String, Object> row = rowOf(page.dealRows(), DAY);
        assertThat(page.number(NET_RESULT_SUM))
                .as("несошедшаяся сверка вошла в денежную сумму наравне с прочими: 18, а "
                        + "не 8 — признак ненадёжности сделку из сумм не выводит")
                .isEqualByComparingTo(new BigDecimal("18"));
        assertThat(row.get(R_DENOMINATOR_DEALS))
                .as("и в знаменатель R она вошла тоже").isEqualTo(3);
        assertThat(row.get(MISMATCHED_DEALS))
                .as("видна она СЧЁТЧИКОМ, а не выпадением").isEqualTo(1);
        assertThat(row.get(NOT_RUN_DEALS))
                .as("«не проверяли» — второй, отдельный счётчик: от «проверили, всё в "
                        + "порядке» он отличается").isEqualTo(1);
        assertThat(row.get(CLOSED_DEALS))
                .as("а у сошедшейся сверки счётчика нет вовсе — её число есть остаток")
                .isEqualTo(3);
    }

    @Test
    @DisplayName("B8.17 — Счётчики и суммы не пусты: ноль у них исход, а не пробел")
    void countersAndSumsAreZeroRatherThanEmpty() {
        DealDraft.of("E-8-17", TENANT, midnightDaysAgo(DAY))
                .netResult("10").funding(NO_FUNDING).fee(NO_FEE).liquidationPenalty("0")
                .build().put();

        recompute();

        Answer page = aggregates(DEAL_GRAIN, TENANT);
        assertThat(page.dealRows())
                .as("строка за сутки ЕСТЬ: ею «не было ни одного» отличается от «не считали»")
                .hasSize(1);
        Map<String, Object> row = rowOf(page.dealRows(), DAY);
        assertThat(unhealthyCountersOf(row))
                .as("все счётчики нездоровых значений равны нулю, а не пусты")
                .allSatisfy(counter -> assertThat(counter).isNotNull().isEqualTo(0));
        assertThat(page.number(LOSS_RESULT_SUM))
                .as("сумма убытков без слагаемых — ноль, а не пустота")
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(page.number(LIQUIDATION_PENALTY_SUM))
                .as("сумма штрафов — тоже").isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(page.number(PLANNED_RISK_EXCLUDED_SUM))
                .as("и сумма выведенного планового риска").isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(page.number(FEE_SUM))
                .as("нулевая комиссия доехала нулём, а не пропала")
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("B8.18 — Зерно режется по ключам: счёт, определение, валюта, сутки")
    void theGrainIsCutByAccountStrategyCurrencyAndDay() {
        DealDraft.of("E-8-18-A1", TENANT, midnightDaysAgo(DAY))
                .netResult("10").funding(NO_FUNDING).build().put();
        DealDraft.of("E-8-18-A2", TENANT, midnightDaysAgo(DAY))
                .netResult("4").funding(NO_FUNDING).build().put();
        DealDraft.of("E-8-18-OTHER-STRATEGY", TENANT, midnightDaysAgo(DAY))
                .strategy(SECOND_STRATEGY).netResult("2").funding(NO_FUNDING).build().put();
        DealDraft.of("E-8-18-OTHER-ACCOUNT", TENANT, midnightDaysAgo(DAY))
                .account(SECOND_ACCOUNT).netResult("3").funding(NO_FUNDING).build().put();
        DealDraft.of("E-8-18-EARLIER-DAY", TENANT, midnightDaysAgo(EARLIER_DAY))
                .netResult("5").funding(NO_FUNDING).build().put();

        recompute();

        List<Map<String, Object>> handed = aggregates(DEAL_GRAIN, TENANT).dealRows();
        assertThat(handed)
                .as("строк ровно столько, сколько РАЗЛИЧНЫХ сочетаний ключей, — четыре при "
                        + "пяти сделках: группировки по сделке нет").hasSize(4);
        Map<String, Object> own = rowOfKeys(handed, DAY, Facts.ACCOUNT, Facts.STRATEGY);
        assertThat(own.get(CLOSED_DEALS))
                .as("строка на сутки, а не на сделку: две сделки одного ключа — одна строка")
                .isEqualTo(2);
        assertThat(money(own, NET_RESULT_SUM))
                .as("и числа её собраны ТОЛЬКО из её фактов")
                .isEqualByComparingTo(new BigDecimal("14"));
        assertThat(money(rowOfKeys(handed, DAY, Facts.ACCOUNT, SECOND_STRATEGY), NET_RESULT_SUM))
                .as("определение стратегии режет зерно")
                .isEqualByComparingTo(new BigDecimal("2"));
        assertThat(money(rowOfKeys(handed, DAY, SECOND_ACCOUNT, Facts.STRATEGY), NET_RESULT_SUM))
                .as("биржевой счёт режет его тоже")
                .isEqualByComparingTo(new BigDecimal("3"));
        assertThat(money(rowOfKeys(handed, EARLIER_DAY, Facts.ACCOUNT, Facts.STRATEGY),
                NET_RESULT_SUM))
                .as("и сутки: соседние сутки того же ключа — своя строка")
                .isEqualByComparingTo(new BigDecimal("5"));
    }

    @Test
    @DisplayName("B8.19 — Сутки зерна считаются по UTC, а не по поясу читателя")
    void theGrainDayIsCountedInUtcRatherThanInTheReadersZone() {
        ZoneOffset shifted = ZoneOffset.ofHours(5);
        OffsetDateTime dayOpening = midnightDaysAgo(DAY);
        OffsetDateTime dayClosing = dayOpening.plusHours(23L);
        assertThat(dayOpening.atZoneSameInstant(shifted).toLocalDate())
                .as("вход поставлен: в смещённом поясе эти моменты лежат в РАЗНЫХ сутках")
                .isNotEqualTo(dayClosing.atZoneSameInstant(shifted).toLocalDate());

        DealDraft.of("E-8-19-OPENING", TENANT, dayOpening)
                .netResult("10").funding(NO_FUNDING).build().put();
        DealDraft.of("E-8-19-CLOSING", TENANT, dayClosing)
                .netResult("4").funding(NO_FUNDING).build().put();

        recompute();

        List<Map<String, Object>> handed = aggregates(DEAL_GRAIN, TENANT).dealRows();
        assertThat(handed)
                .as("оба факта собраны в ОДНУ строку: разбиение по смещённому поясу дало бы "
                        + "две").hasSize(1);
        assertThat(handed.getFirst().get(BUCKET_DATE))
                .as("и сутки строки — UTC").isEqualTo(day(DAY));
        assertThat(money(handed.getFirst(), NET_RESULT_SUM))
                .as("числа сложены по обеим сделкам суток")
                .isEqualByComparingTo(new BigDecimal("14"));
    }

    /**
     * Строка выдачи с названной расчётной валютой; иное их число — падение.
     *
     * @param handed   строки выдачи
     * @param currency расчётная валюта; пусто — строка нерезолвленной валюты
     */
    private static Map<String, Object> rowOfCurrency(List<Map<String, Object>> handed,
                                                     String currency) {
        List<Map<String, Object>> found = handed.stream()
                .filter(row -> Objects.equals(currency, row.get(RESULT_CURRENCY)))
                .toList();
        assertThat(found).as("ожидалась ровно одна строка валюты " + currency).hasSize(1);
        return found.getFirst();
    }

    /**
     * Строка выдачи с названными сутками, счётом и определением; иное их
     * число — падение.
     *
     * @param handed   строки выдачи
     * @param daysBack сутки, отстоящие от нынешних на названное число
     * @param account  биржевой счёт
     * @param strategy определение стратегии
     */
    private static Map<String, Object> rowOfKeys(List<Map<String, Object>> handed,
                                                 Integer daysBack, String account,
                                                 String strategy) {
        List<Map<String, Object>> found = handed.stream()
                .filter(row -> Objects.equals(day(daysBack), row.get(BUCKET_DATE)))
                .filter(row -> Objects.equals(account, row.get(ACCOUNT_FIELD)))
                .filter(row -> Objects.equals(strategy, row.get(STRATEGY_FIELD)))
                .toList();
        assertThat(found)
                .as("ожидалась ровно одна строка ключа " + account + "/" + strategy
                        + " за " + day(daysBack)).hasSize(1);
        return found.getFirst();
    }

    /**
     * Денежная сумма РАЗОБРАННОЙ строки выдачи.
     *
     * <p><b>Литералом тела она здесь не читается, и это не небрежность:</b>
     * {@link Answer#number} находит первое вхождение имени поля, а у страницы
     * с несколькими строками имя одно у каждой — адресовать им нужную строку
     * нечем. Значения операндов таких клеток выбраны короткой десятичной
     * записью, чтобы разбор их не округлял; что сумма едет десятичной
     * записью, утверждают клетки с одной строкой.
     *
     * @param row   строка выдачи
     * @param field имя денежного поля
     */
    private static BigDecimal money(Map<String, Object> row, String field) {
        return new BigDecimal(String.valueOf(row.get(field)));
    }

    /**
     * Все денежные суммы строки выдачи — ими мерится «строка пустой валюты
     * денежных сумм не несёт».
     *
     * @param row строка выдачи
     */
    private static List<BigDecimal> moneyFieldsOf(Map<String, Object> row) {
        return List.of(RESULT_BEFORE_FUNDING_SUM, NET_RESULT_SUM, FEE_SUM, FUNDING_SUM,
                        LIQUIDATION_PENALTY_SUM, WIN_RESULT_SUM, LOSS_RESULT_SUM,
                        PLANNED_RISK_SUM, PLANNED_RISK_EXCLUDED_SUM, R_SUM).stream()
                .map(field -> money(row, field))
                .toList();
    }

    /**
     * Все счётчики нездоровых значений строки выдачи.
     *
     * @param row строка выдачи
     */
    private static List<Object> unhealthyCountersOf(Map<String, Object> row) {
        return List.of(RESULT_UNAVAILABLE_DEALS, CURRENCY_UNRESOLVED_DEALS, RISK_UNSIZED_DEALS,
                        LIQUIDATED_DEALS, FORCED_REDUCTION_DEALS, OUTCOME_UNDETERMINED_DEALS,
                        MISMATCHED_DEALS, NOT_RUN_DEALS, BREAKDOWN_INCOMPLETE_DEALS,
                        BREAKDOWN_NOT_ASSESSED_DEALS, RISK_BENCHMARK_MISSING_DEALS).stream()
                .map(row::get)
                .toList();
    }

    /**
     * Сумма трёх классов результата и недоступности — ею мерится, что
     * популяция долей разбита без остатка.
     *
     * @param row строка выдачи
     */
    private static Integer classesOf(Map<String, Object> row) {
        return (Integer) row.get(WINNING_DEALS) + (Integer) row.get(LOSING_DEALS)
                + (Integer) row.get(NEUTRAL_DEALS) + (Integer) row.get(RESULT_UNAVAILABLE_DEALS);
    }

    /**
     * Сделки со ЗДОРОВЫМ исходом закрытия — остаток, а не счётчик выдачи.
     *
     * @param row строка выдачи
     */
    private static Integer healthyOutcomesOf(Map<String, Object> row) {
        return (Integer) row.get(CLOSED_DEALS) - (Integer) row.get(LIQUIDATED_DEALS)
                - (Integer) row.get(FORCED_REDUCTION_DEALS)
                - (Integer) row.get(OUTCOME_UNDETERMINED_DEALS);
    }
}
