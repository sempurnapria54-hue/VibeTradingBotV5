package com.example.statistics.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетки {@code B7.13}, {@code B7.14} и {@code B7.18} — ФОРМА ОДНОГО
 * ПРОХОДА: из чего он сложен, чем помечен и чем НЕ управляется
 * (.claude/tests/cases/statistics.md §«B7 — Пересчёт: окно, порции и охрана
 * начала ряда»).
 *
 * <p><b>Класс равен КОНФИГУРАЦИИ КОНТЕКСТА</b>: все три клетки берут штатное
 * положение осей — окно величиной субстрата, выключатель не снят, такт, до
 * которого прогон не доживает, — и расходятся только тем, какие факты каждая
 * себе кладёт. Сдвига оси не требует ни одна: предмет здесь не ширина окна,
 * а то, чем проход эту ширину отрабатывает.
 *
 * <p><b>Три клетки складываются в одно утверждение с трёх сторон.</b>
 * {@code B7.13} говорит, что работа РЕЖЕТСЯ сутками; {@code B7.14} — что
 * помечена она при этом ОДНИМ моментом на весь проход; {@code B7.18} — что
 * выбор суток берётся от нынешних, а не от отметки о прошлом проходе. Первая
 * и вторая противоположны по наблюдаемому и обе верны: номер транзакции у
 * строк разных суток РАЗНЫЙ (порции), момент сборки — ОДИН (проход).
 *
 * <p><b>Образцы текста запросов живут у {@link StatisticsBox}</b>: их читает
 * и клетка {@code B7.19}, утверждающая ОТСУТСТВИЕ этих запросов при снятом
 * выключателе, а вторая запись образца разошлась бы с первой при первой же
 * правке запроса (.claude/rules/carrier-levels.md).
 *
 * <p><b>Наблюдатель запросов живёт в СУБСТРАТЕ, а не в ящике</b>
 * ({@link StatisticsSubstrate}: третья подгружаемая библиотека базы). Ожидание {@code B7.13}
 * — «к базе ушёл запрос группировки на каждые сутки окна, а не один
 * диапазонный на всё окно» — наблюдаемого у ящика не имеет ни одного: по
 * строкам агрегатов один диапазонный запрос и семь посуточных дают ровно
 * один и тот же исход. Счёт исполнений и объём их выдачи знает сама база, и
 * берётся он снаружи; ни один бин сервиса при этом не подменён.
 *
 * <p><b>{@code B7.14} несёт и отрицание СОСЕДНЕЙ клетки — «второго момента
 * нет» у {@code B7.16}</b> ({@link WidenedRecomputeWindowBoxTest}).
 * Контрольный прогон уронил обе клетки одной осью — момент, поставленный
 * порцией вместо прохода, — то есть обе утверждали одно; сильнее здешняя:
 * она мерит единственность момента по ЧЕТЫРЁМ строкам ДВУХ таблиц, а соседняя
 * мерила по трём строкам одной. Слабейший носитель снят
 * (.claude/rules/carrier-levels.md).
 *
 * <p><b>Ряд фактов открывается САМЫМИ СТАРШИМИ сутками окна намеренно.</b>
 * Охрана начала ряда ({@code B7.7}) гасит запрос у суток, покрытых рядом
 * частично, — и число запросов тогда зависело бы от того, где лежит первый
 * факт, а не от ширины окна. Ряд, открытый полночью самых старших суток
 * окна, делает покрытыми ВСЕ его сутки, и счёт запросов становится равен
 * ширине окна дословно.
 *
 * <p><b>Клетка {@code B7.18} мерит ОТСУТСТВИЕ, и мерится оно составом
 * схемы.</b> Отметка последнего пересчёта обязана быть durable — проход
 * переживает перезапуск сервиса, — а единственное durable-место сервиса это
 * его база: колонки под отметку нет ни в одной таблице, и хранить её больше
 * негде. Ключ конфигурации сам по себе отметкой не является: он выбирал бы
 * ПОВЕДЕНИЕ при отметке, которой не существует.
 */
class RecomputePassShapeBoxTest extends StatisticsBox {

    /** Краткое имя класса: из него строятся его группа и его тема. */
    private static final String SLUG = "b7-pass";

    /**
     * Имена, которыми называлась бы колонка отметки последнего пересчёта.
     *
     * <p><b>Перечень — образцы ИМЕНИ, и клейма полноты на нём нет:</b>
     * колонка, названная иначе, им не берётся. Утверждение клетки держится
     * не только им, но и поведением двух тактов подряд: отметка, которая
     * есть, но не выбирает окна, предмета не меняет.
     */
    private static final List<String> MARK_NAMES =
            List.of("recompute", "progress", "watermark", "checkpoint", "high_water");

    /** Сутки, открывающие ряд фактов у клетки о счёте запросов: старшие в окне. */
    private static final Integer SERIES_DAY = StatisticsSubstrate.RECOMPUTE_WINDOW_DAYS - 1;

    /** Сутки, в которых лежит НЕСКОЛЬКО фактов одного ключа зерна. */
    private static final Integer CROWDED_DAY = 2;

    /** Сколько фактов лежит в этих сутках: все одного ключа. */
    private static final Integer CROWD_SIZE = 4;

    /** Ранние сутки пары, которой мерится единственность момента прохода. */
    private static final Integer EARLIER_DAY = 3;

    /** Поздние сутки той же пары. */
    private static final Integer LATER_DAY = 1;

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        StatisticsSubstrate.registerOwn(registry, SLUG);
    }

    @Test
    @DisplayName("B7.13 — Проход идёт посуточными порциями, а не одним запросом на окно")
    void thePassGoesInDailyPortionsAndNotInOneQueryOverTheWindow() {
        Facts.deal("E-7-13-SERIES", TENANT, midnightDaysAgo(SERIES_DAY));
        Facts.incident("E-7-13-SERIES-I", TENANT, DEAL_OPENED, midnightDaysAgo(SERIES_DAY));
        for (int index = 0; index < CROWD_SIZE; index++) {
            Facts.deal("E-7-13-CROWD-" + index, TENANT, midnightDaysAgo(CROWDED_DAY));
        }
        Long windowDays = StatisticsSubstrate.RECOMPUTE_WINDOW_DAYS.longValue();
        rows.resetStatementCounters();

        recompute();

        assertThat(rows.statementCalls(DEAL_GRAIN_QUERY))
                .as("запрос группировки сделочного зерна исполнен СТОЛЬКО РАЗ, сколько "
                        + "суток в окне (%s): один диапазонный запрос на всё окно дал бы "
                        + "единицу", windowDays)
                .isEqualTo(windowDays);
        assertThat(rows.statementCalls(INCIDENT_GRAIN_QUERY))
                .as("у второго зерна счёт тот же: порция спрашивает ОБА зерна своих суток")
                .isEqualTo(windowDays);
        assertThat(rows.statementRows(DEAL_GRAIN_QUERY))
                .as("а объём выдачи ВСЕХ его исполнений — две строки: по одной на ключ "
                        + "зерна суток. Фактов при этом %s, и вычитанные в память они дали "
                        + "бы %s строк: группировка выполнена в базе",
                        CROWD_SIZE + 1, CROWD_SIZE + 1)
                .isEqualTo(2L);
        assertThat(rows.count(DEAL_FACTS))
                .as("вход поставлен: фактов действительно %s", CROWD_SIZE + 1)
                .isEqualTo(CROWD_SIZE + 1L);
        List<Map<String, Object>> handed = aggregates(DEAL_GRAIN, TENANT).dealRows();
        assertThat(rowOf(handed, CROWDED_DAY).get(CLOSED_DEALS))
                .as("и собранная строка людных суток знает все свои факты").isEqualTo(CROWD_SIZE);
        String seriesVersion = rows.rowVersion(DEAL_AGGREGATES, "bucket_date", bucket(SERIES_DAY));
        assertThat(seriesVersion)
                .as("строка старших суток окна собрана: у пустого места номера транзакции "
                        + "нет вовсе, и сравнение с пустотой разошлось бы впустую")
                .isNotNull();
        assertThat(rows.rowVersion(DEAL_AGGREGATES, "bucket_date", bucket(CROWDED_DAY)))
                .as("а строки РАЗНЫХ суток положены РАЗНЫМИ транзакциями: порция есть сутки, "
                        + "а не проход — одна транзакция на всё окно держала бы блокировки "
                        + "на всех пересчитываемых строках")
                .isNotEqualTo(seriesVersion);
    }

    @Test
    @DisplayName("B7.14 — Момент сборки едет той же транзакцией, что и числа")
    void theAssemblyMomentTravelsInTheSameTransactionAsTheNumbers() {
        Facts.deal("E-7-14-DEAL-EARLY", TENANT, midnightDaysAgo(EARLIER_DAY));
        Facts.deal("E-7-14-DEAL-LATE", TENANT, midnightDaysAgo(LATER_DAY));
        Facts.incident("E-7-14-INC-EARLY", TENANT, DEAL_OPENED, midnightDaysAgo(EARLIER_DAY));
        Facts.incident("E-7-14-INC-LATE", TENANT, HOLD_RAISED, midnightDaysAgo(LATER_DAY));

        recompute();

        List<Map<String, Object>> handed = aggregates(DEAL_GRAIN, TENANT).dealRows();
        List<Map<String, Object>> incidents = aggregates(INCIDENT_GRAIN, TENANT).incidentRows();
        List<Map<String, Object>> collected = Stream.concat(handed.stream(), incidents.stream())
                .toList();
        assertThat(handed).as("сделочных строк собрано двое").hasSize(2);
        assertThat(incidents).as("и строк происшествий двое").hasSize(2);
        assertThat(moment(handed.getFirst(), ASSEMBLED_AT))
                .as("выдача отдаёт момент сборки РЯДОМ с числами — читатель видит лаг "
                        + "проекции, а не догадывается о нём")
                .isNotNull();
        assertThat(distinctMomentsOf(collected))
                .as("и у ВСЕХ четырёх строк он один и тот же, хотя порции у них разные и "
                        + "таблицы разные: момент ставит ПРОХОД, а не запрос и не порция")
                .hasSize(1);
        assertThat(distinctMomentsOf(incidents))
                .as("второе зерно едет с тем же моментом: проход у двух зёрен один")
                .isEqualTo(distinctMomentsOf(handed));
    }

    @Test
    @DisplayName("B7.18 — Отметки последнего пересчёта нет, и окно ею не выбирается")
    void thereIsNoLastRecomputeMarkAndTheWindowIsNotChosenByIt() {
        Facts.deal("E-7-18-EARLY", TENANT, midnightDaysAgo(EARLIER_DAY));
        Facts.deal("E-7-18-LATE", TENANT, midnightDaysAgo(LATER_DAY));

        assertThat(markColumns())
                .as("колонки, хранящей прогресс пересчёта, нет ни в одной таблице схемы — "
                        + "а хранить отметку сервису больше негде: durable-место у него "
                        + "одно")
                .isEmpty();

        recompute();
        Map<String, Object> firstEarly = rowOf(dealRows(), EARLIER_DAY);
        Map<String, Object> firstLate = rowOf(dealRows(), LATER_DAY);

        // Факт кладётся ПОСЛЕ прохода, а момент его лежит в сутках, которые
        // проход уже собрал: так выражается «записан позже своего момента».
        Facts.deal("E-7-18-LAGGARD", TENANT, midnightDaysAgo(EARLIER_DAY).plusHours(6L));

        recompute();

        Map<String, Object> secondEarly = rowOf(dealRows(), EARLIER_DAY);
        assertThat(moment(rowOf(dealRows(), LATER_DAY), ASSEMBLED_AT))
                .as("второй такт пересчитал и те сутки, которые первый уже собрал: окно "
                        + "считается от нынешних суток, а не от отметки о прошлом проходе — "
                        + "иначе момент этой строки застыл бы")
                .isAfter(moment(firstLate, ASSEMBLED_AT));
        assertThat(secondEarly.get(CLOSED_DEALS))
                .as("и факт, записанный ПОЗЖЕ своего момента, из пересчёта не выпал: его "
                        + "сутки пересобраны целиком")
                .isEqualTo(2);
        assertThat(firstEarly.get(CLOSED_DEALS))
                .as("вход поставлен: до его записи те же сутки знали одну сделку")
                .isEqualTo(1);
    }

    /** Строки сделочного зерна, как их отдаёт поверхность чтения. */
    private List<Map<String, Object>> dealRows() {
        return aggregates(DEAL_GRAIN, TENANT).dealRows();
    }

    /**
     * Колонки всех таблиц схемы, чьё имя похоже на отметку пересчёта.
     *
     * <p>Перечень берётся из схемы целиком, а не из названных таблиц:
     * отметка, заведённая в СОСЕДНЕЙ таблице, была бы отметкой ровно так же.
     */
    private List<String> markColumns() {
        return rows.tableNames().stream()
                .flatMap(table -> rows.columnNames(table).stream()
                        .filter(column -> MARK_NAMES.stream()
                                .anyMatch(mark -> column.toLowerCase().contains(mark)))
                        .map(column -> table + "." + column))
                .toList();
    }
}
