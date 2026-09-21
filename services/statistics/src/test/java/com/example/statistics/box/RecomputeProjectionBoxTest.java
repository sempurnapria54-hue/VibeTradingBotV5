package com.example.statistics.box;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетки {@code B7.2} — {@code B7.10}: сутки зерна, идемпотентность проекции
 * и охрана начала ряда
 * (.claude/tests/cases/statistics.md §«B7 — Пересчёт: окно, порции и охрана
 * начала ряда»).
 *
 * <p><b>Класс равен КОНФИГУРАЦИИ КОНТЕКСТА</b>, как у соседних групп: все
 * девять клеток берут штатное положение осей — окно пересчёта величиной
 * субстрата, выключатель снят не у одной, такт, до которого прогон не
 * доживает, — и расходятся только тем, какие факты каждая себе кладёт.
 *
 * <p><b>Клеток об ОКНЕ здесь нет, и это не раскладка по вкусу.</b> Ожидание
 * {@code B7.1} говорит, что ширина окна взята ВЕЛИЧИНОЙ КОНФИГУРАЦИИ, а не
 * константой кода; на штатных семи сутках предъявить это нечем — они
 * совпадают с умолчанием сервиса дословно, и клетка прошла бы на реализации,
 * читающей константу. Тот же довод у {@code B7.15} (окно сужено), у
 * {@code B7.16} (окно расширено до всего ряда) и у {@code B7.17} (событие
 * старше окна): каждой из них нужна СВОЯ ось, то есть свой контекст, —
 * {@link NarrowedRecomputeWindowBoxTest} и
 * {@link WidenedRecomputeWindowBoxTest}.
 *
 * <p><b>Факты кладутся ПРЯМОЙ ЗАПИСЬЮ</b> ({@link Facts}) — они предусловие
 * пересчётного кейса, а не его выход; довод — у самого помощника. Отсюда
 * следствие для класса: приёма он не касается ни одной клеткой, строк пар не
 * заводит и такта тика приёма не подаёт.
 *
 * <p><b>Такт пересчёта подаёт сама клетка</b> ({@link #recompute()}), и
 * ждать после него нечего: строка агрегата асинхронным следом не является —
 * её производит проход, который клетка и запустила.
 *
 * <p><b>Своя группа и своя тема взяты по общему доводу класса кейсов:</b>
 * контексты прогона не закрываются, а группа есть состояние на брокере.
 * Производителя в этих клетках нет ни одного, и тема их остаётся пустой —
 * но пустая своя тема дешевле, чем слушатель, доедающий чужие записи посреди
 * чужого предмета.
 *
 * <p><b>Ассерт идёт ПОВЕРХНОСТЬЮ, а не колонками</b>, потому что у строк
 * агрегатов объявленный читатель есть и читает он их именно ею
 * (.claude/tests/cases/statistics.md §«Чем достаются выходы»). Счёт строк по
 * таблице берётся там, где предмет клетки — ЧИСЛО строк в базе, а не их
 * содержимое: вторая строка того же ключа поверхностью и страницей видна
 * была бы одинаково с первой.
 */
class RecomputeProjectionBoxTest extends StatisticsBox {

    /** Краткое имя класса: из него строятся его группа и его тема. */
    private static final String SLUG = "b7-projection";

    /** Сутки, открывающие ряд фактов там, где клетка мерит не охрану. */
    private static final Integer ANCHOR_DAY = 4;

    /** Ранние сутки пары, которой мерится граница полуинтервала. */
    private static final Integer EARLIER_DAY = 3;

    /** Поздние сутки той же пары. */
    private static final Integer LATER_DAY = 2;

    /** Сутки, в которых лежат факты клеток об идемпотентности. */
    private static final Integer SINGLE_DAY = 1;

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        StatisticsSubstrate.registerOwn(registry, SLUG);
    }

    @Test
    @DisplayName("B7.2 — Сутки берутся по оси времени СВОЕГО зерна")
    void eachGrainTakesItsDayFromItsOwnTimeAxis() {
        Facts.deal("E-7-2-DEAL", TENANT, midnightDaysAgo(EARLIER_DAY));
        Facts.incident("E-7-2-INCIDENT", TENANT, DEAL_OPENED, midnightDaysAgo(LATER_DAY));

        recompute();

        List<Map<String, Object>> dealRows = aggregates(DEAL_GRAIN, TENANT).dealRows();
        List<Map<String, Object>> incidentRows = aggregates(INCIDENT_GRAIN, TENANT).incidentRows();
        assertThat(dealRows).as("сделочная строка собрана, и она одна").hasSize(1);
        assertThat(incidentRows).as("строка происшествий собрана, и она одна").hasSize(1);
        assertThat(dealRows.getFirst().get(BUCKET_DATE))
                .as("сделочные сутки взяты по моменту ТЕРМИНАЛА сделки")
                .isEqualTo(day(EARLIER_DAY));
        assertThat(incidentRows.getFirst().get(BUCKET_DATE))
                .as("сутки происшествий — по моменту ПРОИСШЕСТВИЯ: ось у каждого зерна своя")
                .isEqualTo(day(LATER_DAY));
        assertThat(incidentRows.getFirst().get(BUCKET_DATE))
                .as("общего момента у двух зёрен нет: сутки разошлись")
                .isNotEqualTo(dealRows.getFirst().get(BUCKET_DATE));
    }

    @Test
    @DisplayName("B7.3 — Сутки меряются полуинтервалом: полночь не задваивается")
    void theDayIsAHalfOpenIntervalAndMidnightIsNotCountedTwice() {
        // Ряд открывается сутками РАНЬШЕ обеих меряемых: иначе охрана начала
        // ряда погасила бы ранние сутки (B7.7), и клетка краснела бы по ней, а
        // не по границе полуинтервала.
        Facts.deal("E-7-3-ANCHOR", TENANT, midnightDaysAgo(ANCHOR_DAY));
        Facts.deal("E-7-3-LAST-MS", TENANT, midnightDaysAgo(LATER_DAY).minusNanos(1_000_000L));
        Facts.deal("E-7-3-MIDNIGHT", TENANT, midnightDaysAgo(LATER_DAY));
        Facts.deal("E-7-3-FIRST-MS", TENANT, midnightDaysAgo(LATER_DAY).plusNanos(1_000_000L));

        recompute();

        List<Map<String, Object>> handed = aggregates(DEAL_GRAIN, TENANT).dealRows();
        assertThat(closedDealsOf(handed, EARLIER_DAY))
                .as("последняя миллисекунда суток принадлежит ИМ: граница справа исключающая")
                .isEqualTo(1);
        assertThat(closedDealsOf(handed, LATER_DAY))
                .as("полночь и первая миллисекунда — следующим: граница слева включающая")
                .isEqualTo(2);
        assertThat(handed.stream().mapToInt(row -> (Integer) row.get(CLOSED_DEALS)).sum())
                .as("ни один факт не посчитан дважды: сумма по суткам равна числу фактов")
                .isEqualTo(4);
    }

    @Test
    @DisplayName("B7.4 — Повторный прогон даёт те же числа: проекция, а не накопитель")
    void theSecondPassYieldsTheSameNumbers() {
        Facts.deal("E-7-4-A", TENANT, midnightDaysAgo(SINGLE_DAY));
        Facts.deal("E-7-4-B", TENANT, midnightDaysAgo(SINGLE_DAY));

        recompute();
        Map<String, Object> first = onlyRow(aggregates(DEAL_GRAIN, TENANT).dealRows());

        recompute();
        Map<String, Object> second = onlyRow(aggregates(DEAL_GRAIN, TENANT).dealRows());

        assertThat(rows.count(DEAL_AGGREGATES))
                .as("строк по ключу зерна по-прежнему одна").isEqualTo(1L);
        assertThat(numbersOf(second))
                .as("числа не изменились ни в одной колонке: проекция повтора не боится")
                .isEqualTo(numbersOf(first));
        assertThat(moment(second, ASSEMBLED_AT))
                .as("а момент сборки обновился — им и различается «пересчитано и вышло то же» "
                        + "от «не пересчитывалось»")
                .isAfter(moment(first, ASSEMBLED_AT));
    }

    @Test
    @DisplayName("B7.5 — Upsert идёт по именованному ключу зерна и строк не задваивает")
    void theUpsertGoesByTheNamedGrainKeyAndNeverDoublesARow() {
        Facts.deal("E-7-5-A", TENANT, midnightDaysAgo(SINGLE_DAY));
        recompute();

        assertThat(onlyRow(aggregates(DEAL_GRAIN, TENANT).dealRows()).get(CLOSED_DEALS))
                .as("вход поставлен: первый прогон собрал одну сделку").isEqualTo(1);

        Facts.deal("E-7-5-B", TENANT, midnightDaysAgo(SINGLE_DAY));
        recompute();

        assertThat(rows.count(DEAL_AGGREGATES))
                .as("строк по ключу зерна по-прежнему ОДНА, а не две").isEqualTo(1L);
        assertThat(onlyRow(aggregates(DEAL_GRAIN, TENANT).dealRows()).get(CLOSED_DEALS))
                .as("числа пересобраны целиком: строка знает обе сделки").isEqualTo(2);

        recompute();

        assertThat(rows.count(DEAL_AGGREGATES))
                .as("второй строки того же ключа не появилось ни при одном числе прогонов")
                .isEqualTo(1L);
        assertThat(onlyRow(aggregates(DEAL_GRAIN, TENANT).dealRows()).get(CLOSED_DEALS))
                .as("и число не выросло: прогон без новых фактов дельты не прибавляет — "
                        + "накопитель дал бы четыре").isEqualTo(2);
    }

    @Test
    @DisplayName("B7.6 — Пустые компоненты ключа находятся повторным прогоном")
    void emptyKeyComponentsAreFoundByTheSecondPass() {
        Facts.deal("E-7-6-A", TENANT, null, null, midnightDaysAgo(SINGLE_DAY));
        Facts.deal("E-7-6-B", TENANT, null, null, midnightDaysAgo(SINGLE_DAY));

        recompute();
        Map<String, Object> first = onlyRow(aggregates(DEAL_GRAIN, TENANT).dealRows());

        recompute();
        Map<String, Object> second = onlyRow(aggregates(DEAL_GRAIN, TENANT).dealRows());

        assertThat(first.get("strategyInternalId"))
                .as("вход поставлен: определение стратегии пусто").isNull();
        assertThat(first.get("resultCurrency"))
                .as("и расчётная валюта пуста тоже").isNull();
        assertThat(rows.count(DEAL_AGGREGATES))
                .as("строка с пустыми компонентами ключа ОДНА, а не две")
                .isEqualTo(1L);
        assertThat(second.get(CLOSED_DEALS))
                .as("числа не умножились на число прогонов")
                .isEqualTo(first.get(CLOSED_DEALS)).isEqualTo(2);
        assertThat(moment(second, ASSEMBLED_AT))
                .as("второй прогон НАШЁЛ её и переписал, а не вставил соседнюю: без клаузы "
                        + "`nulls not distinct` он вставил бы вторую строку")
                .isAfter(moment(first, ASSEMBLED_AT));
    }

    @Test
    @DisplayName("B7.7 — Сутки раньше начала ряда фактов проход не пишет")
    void daysStartingBeforeTheSeriesAreNotWritten() {
        // Самый ранний факт лежит в СЕРЕДИНЕ своих суток: ими ряд покрыт
        // частично, и собранная по ним строка заменила бы верные числа
        // частичными — молча, при свежем моменте сборки.
        Facts.deal("E-7-7-PARTIAL", TENANT, midnightDaysAgo(EARLIER_DAY).plusHours(12L));
        Facts.deal("E-7-7-WHOLE", TENANT, midnightDaysAgo(LATER_DAY));

        recompute();

        List<Map<String, Object>> handed = aggregates(DEAL_GRAIN, TENANT).dealRows();
        assertThat(handed).as("собраны ровно одни сутки — покрытые рядом целиком").hasSize(1);
        assertThat(handed.getFirst().get(BUCKET_DATE))
                .as("и это те, что начались ПОЗЖЕ первого факта").isEqualTo(day(LATER_DAY));
        assertThat(bucketDatesOf(handed))
                .as("частично покрытых суток нет: отбор охраняет от ухудшения, а не "
                        + "восстанавливает — строки, которой не собирал ни один прогон, "
                        + "просто нет")
                .doesNotContain(day(EARLIER_DAY), day(ANCHOR_DAY));
        assertThat(rows.count(DEAL_AGGREGATES))
                .as("и в базе строка тоже одна: страница не скрыла второй").isEqualTo(1L);
    }

    @Test
    @DisplayName("B7.8 — Сутки, начавшиеся ровно в момент первого факта, ряд покрывает целиком")
    void theDayStartingExactlyAtTheFirstFactIsCoveredWhole() {
        Facts.deal("E-7-8", TENANT, midnightDaysAgo(EARLIER_DAY));

        recompute();

        List<Map<String, Object>> handed = aggregates(DEAL_GRAIN, TENANT).dealRows();
        assertThat(handed).as("строка собрана: граница ВКЛЮЧАЮЩАЯ").hasSize(1);
        assertThat(handed.getFirst().get(BUCKET_DATE))
                .as("и это ровно те сутки, в полночь которых лёг первый факт")
                .isEqualTo(day(EARLIER_DAY));
        assertThat(rows.count(DEAL_AGGREGATES))
                .as("суток раньше них не появилось ни одних").isEqualTo(1L);
    }

    @Test
    @DisplayName("B7.9 — Охрана отбора своя у каждого зерна")
    void theSelectionGuardIsOwnToEachGrain() {
        Facts.incident("E-7-9-I1", TENANT, DEAL_OPENED, midnightDaysAgo(ANCHOR_DAY));
        Facts.incident("E-7-9-I2", TENANT, DEAL_OPENED, midnightDaysAgo(EARLIER_DAY));
        Facts.deal("E-7-9-DEAL", TENANT, midnightDaysAgo(LATER_DAY));

        recompute();

        List<Map<String, Object>> incidentRows = aggregates(INCIDENT_GRAIN, TENANT).incidentRows();
        List<Map<String, Object>> dealRows = aggregates(DEAL_GRAIN, TENANT).dealRows();
        assertThat(bucketDatesOf(incidentRows))
                .as("зерно происшествий собрано у ОБОИХ своих суток, хотя сделочный ряд "
                        + "начинается позже: общая охрана на два зерна запретила бы их")
                .containsExactlyInAnyOrder(day(ANCHOR_DAY), day(EARLIER_DAY));
        assertThat(dealRows).as("сделочных строк собраны одни сутки").hasSize(1);
        assertThat(dealRows.getFirst().get(BUCKET_DATE))
                .as("и это те, с которых начинается СВОЙ ряд").isEqualTo(day(LATER_DAY));
        assertThat(bucketDatesOf(dealRows))
                .as("у суток чужого ряда сделочных строк не появилось")
                .doesNotContain(day(ANCHOR_DAY), day(EARLIER_DAY));
    }

    @Test
    @DisplayName("B7.10 — Пустой ряд: суток не пишется ни одних, и это объявленный исход")
    void theEmptySeriesWritesNoDaysAtAll() {
        assertThat(rows.count(DEAL_FACTS)).as("вход поставлен: сделочных фактов нет").isZero();
        assertThat(rows.count(INCIDENT_FACTS)).as("и фактов происшествий нет").isZero();

        assertThatCode(this::recompute)
                .as("проход прошёл штатно: пустой ряд — исход, а не отказ")
                .doesNotThrowAnyException();

        assertThat(rows.count(DEAL_AGGREGATES))
                .as("сделочных строк не появилось ни одной").isZero();
        assertThat(rows.count(INCIDENT_AGGREGATES))
                .as("и строк происшествий тоже").isZero();
        assertThat(aggregates(DEAL_GRAIN, TENANT).status())
                .as("тот же исход даёт запрос страницы: вопрос принят").isEqualTo(200);
        assertThat(aggregates(DEAL_GRAIN, TENANT).dealRows())
                .as("и перечень при выбранном зерне пуст").isEmpty();
        assertThat(aggregates(INCIDENT_GRAIN, TENANT).incidentRows())
                .as("у второго зерна исход тот же").isEmpty();
    }

    /**
     * Счётчик закрытых сделок у строки названных суток; нет строки — нуль.
     *
     * @param handed   строки выдачи
     * @param daysBack сутки, отстоящие от нынешних на названное число
     */
    private static Integer closedDealsOf(List<Map<String, Object>> handed, Integer daysBack) {
        return handed.stream()
                .filter(row -> Objects.equals(day(daysBack), row.get(BUCKET_DATE)))
                .map(row -> (Integer) row.get(CLOSED_DEALS))
                .findFirst()
                .orElse(0);
    }

    /** Единственная строка выдачи; иное их число — падение клетки. */
    private static Map<String, Object> onlyRow(List<Map<String, Object>> handed) {
        assertThat(handed).as("ожидалась ровно одна строка выдачи").hasSize(1);
        return handed.getFirst();
    }

    /**
     * Строка выдачи без момента сборки — то есть ровно её ЧИСЛА.
     *
     * <p>Момент сборки у двух прогонов законно разный, и сравнение целых
     * строк мерило бы его, а не числа.
     *
     * @param row строка выдачи
     */
    private static Map<String, Object> numbersOf(Map<String, Object> row) {
        Map<String, Object> numbers = new LinkedHashMap<>(row);
        numbers.remove(ASSEMBLED_AT);
        return numbers;
    }
}
