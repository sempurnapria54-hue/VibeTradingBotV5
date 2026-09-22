package com.example.statistics.box;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетки {@code B7.11} и {@code B7.12} — ГРАНИЦА ПОРЦИИ пересчёта: что
 * внутри неё неделимо и что между порциями не стои́т ничего
 * (.claude/tests/cases/statistics.md §«B7 — Пересчёт: окно, порции и охрана
 * начала ряда»).
 *
 * <p><b>Обе клетки начинаются словами «запись отказывает», и отказ этот —
 * ВХОД, которого тропа ящика не производит.</b> Строка агрегата пишется
 * только проходом, а проход отказывает лишь тогда, когда отказывает база;
 * подменить писателя значило бы заглянуть внутрь ящика. Поэтому отказ
 * ставится в СУБСТРАТЕ — таблица второго зерна уезжает на время тела
 * ({@link Rows#withoutTable}), — и это та же форма, которой соседняя группа
 * ставит отказ посреди такта приёма.
 *
 * <p><b>Уезжает таблица ВТОРОГО зерна, и выбор этот несущий.</b> Порция
 * пишет обе таблицы одним ходом, сначала сделочную; отказ на второй — это
 * ровно то состояние, в котором половина работы уже сделана. Отказ на
 * ПЕРВОЙ не различал бы «порция откатилась» от «порция не начиналась».
 *
 * <p><b>Порция середины окна выбирается ФАКТАМИ, а не порядковым номером
 * такта.</b> Проход трогает вторую таблицу только у тех суток, где зерно
 * происшествий непусто: у прочих перечень пуст, и упавшая таблица им
 * безразлична. Отсюда раскладка входа {@code B7.12}: сделочные факты — в
 * свежих сутках и в самых старых, факт происшествия — ровно в средних, и
 * отказ приходит ровно на них.
 *
 * <p><b>Направление прохода названо домом</b> ({@code AggregateRecomputeJob}:
 * от свежих суток к старым), и вход клетки на него опирается: «пройденные
 * до отказа» есть сутки СВЕЖЕЕ средних, «непройденные» — СТАРШЕ них.
 *
 * <p><b>Строка старших суток кладётся ПРЯМОЙ ЗАПИСЬЮ и кладётся
 * НЕВЕРНОЙ</b> ({@link Aggregates}): непройденные сутки обязаны остаться с
 * ПРЕЖНИМИ числами, а строки, которой не собирал ни один проход, не
 * существует вовсе — отсутствие её было бы неотличимо от отсутствия по
 * любой другой причине. Неверные числа делают клетку неспособной пройти
 * впустую: проход, дотянувшийся до этих суток, обязан их изменить.
 *
 * <p><b>Ассерт по ЧИСЛУ строк идёт колонками, а по содержимому —
 * поверхностью</b>, как у соседних классов группы
 * (.claude/tests/cases/statistics.md §«Чем достаются выходы»). Пока таблица
 * второго зерна уехала, поверхность его зерна не спрашивается вовсе: вопрос
 * к ней мерил бы отсутствие таблицы, а не исход прохода.
 */
class RecomputePortionBoundaryBoxTest extends StatisticsBox {

    /** Краткое имя класса: из него строятся его группа и его тема. */
    private static final String SLUG = "b7-portion";

    /** Сутки, в которых лежат факты ОБОИХ зёрен у клетки о неделимости порции. */
    private static final Integer BOTH_GRAINS_DAY = 1;

    /** Сутки, пройденные до отказа: свежее средних. */
    private static final Integer PASSED_DAY = 1;

    /** Сутки, на которых проход отказывает: в них лежит факт происшествия. */
    private static final Integer FAILING_DAY = 3;

    /** Сутки, до которых проход не дошёл: старше отказавших. */
    private static final Integer UNREACHED_DAY = 5;

    /** Насколько в прошлом стои́т момент сборки положенной строки. */
    private static final Duration STALE_AGE = Duration.ofHours(3L);

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        StatisticsSubstrate.registerOwn(registry, SLUG);
    }

    @Test
    @DisplayName("B7.11 — Оба зерна пишутся одной транзакцией порции")
    void bothGrainsAreWrittenInOnePortionTransaction() {
        // Оба ряда открываются ПОЛНОЧЬЮ этих суток: охрана начала ряда (B7.7)
        // погасила бы сутки, покрытые рядом частично, и порция не началась бы
        // вовсе — то есть клетка краснела бы по ней, а не по границе порции.
        Facts.deal("E-7-11-DEAL", TENANT, midnightDaysAgo(BOTH_GRAINS_DAY));
        Facts.incident("E-7-11-INCIDENT", TENANT, DEAL_OPENED, midnightDaysAgo(BOTH_GRAINS_DAY));

        rows.withoutTable(INCIDENT_AGGREGATES, () -> {
            assertThatThrownBy(this::recompute)
                    .as("отказ записи второго зерна уходит наружу, а не глушится проходом")
                    .isInstanceOf(RuntimeException.class);
            assertThat(rows.count(DEAL_AGGREGATES))
                    .as("и сделочной строки этих суток не появилось тоже, хотя зерно её "
                            + "собрано и записано ПЕРВЫМ: порция откатилась целиком, и "
                            + "половины её — свежие сделочные числа при прежних счётчиках "
                            + "происшествий — не наблюдается")
                    .isZero();
        });

        assertThat(rows.count(INCIDENT_AGGREGATES))
                .as("строк второго зерна нет тоже: таблица вернулась на место пустой")
                .isZero();

        recompute();

        assertThat(rows.count(DEAL_AGGREGATES))
                .as("без отказа порция пишет сделочную строку").isEqualTo(1L);
        assertThat(rows.count(INCIDENT_AGGREGATES))
                .as("и строку зерна происшествий — тех же суток").isEqualTo(1L);
        String dealVersion = rows.rowVersion(DEAL_AGGREGATES, "bucket_date", bucket(BOTH_GRAINS_DAY));
        assertThat(dealVersion)
                .as("и собраны они у ТЕХ САМЫХ суток: у пустого места номера транзакции "
                        + "нет вовсе, и сравнение двух пустот сошлось бы впустую")
                .isNotNull();
        assertThat(rows.rowVersion(INCIDENT_AGGREGATES, "bucket_date", bucket(BOTH_GRAINS_DAY)))
                .as("обе положены ОДНОЙ транзакцией: номер транзакции, записавшей "
                        + "нынешнюю версию строки, у них совпал")
                .isEqualTo(dealVersion);
    }

    @Test
    @DisplayName("B7.12 — Обрыв МЕЖДУ порциями не стои́т ничего")
    void aBreakBetweenPortionsCostsNothing() {
        // Сделочный ряд открывается непройденными сутками, ряд происшествий —
        // отказавшими: обе охраны начала ряда пропускают свои сутки, и предмет
        // клетки остаётся границей порции.
        Facts.deal("E-7-12-OLD-A", TENANT, midnightDaysAgo(UNREACHED_DAY));
        Facts.deal("E-7-12-OLD-B", TENANT, midnightDaysAgo(UNREACHED_DAY).plusHours(3L));
        Facts.deal("E-7-12-PASSED", TENANT, midnightDaysAgo(PASSED_DAY));
        Facts.incident("E-7-12-INCIDENT", TENANT, DEAL_OPENED, midnightDaysAgo(FAILING_DAY));
        Aggregates.dealRow(TENANT, bucket(UNREACHED_DAY), Aggregates.STALE_CLOSED_DEALS,
                momentsAgo(STALE_AGE));
        Map<String, Object> before = rowOf(dealRows(), UNREACHED_DAY);

        rows.withoutTable(INCIDENT_AGGREGATES, () -> {
            assertThatThrownBy(this::recompute)
                    .as("проход ОСТАНОВИЛСЯ на отказавшей порции, а не проглотил отказ")
                    .isInstanceOf(RuntimeException.class);

            List<Map<String, Object>> handed = dealRows();
            assertThat(bucketDatesOf(handed))
                    .as("сутки, пройденные до отказа, пересчитаны: их порция закоммичена "
                            + "и отказом следующей не отменена")
                    .contains(day(PASSED_DAY));
            assertThat(moment(rowOf(handed, PASSED_DAY), ASSEMBLED_AT))
                    .as("и несут свежий момент сборки")
                    .isAfter(moment(before, ASSEMBLED_AT));
            assertThat(rowOf(handed, UNREACHED_DAY))
                    .as("а непройденные остались с ПРЕЖНИМИ числами и прежним моментом, "
                            + "хотя фактов в них двое, а строка знает одну")
                    .isEqualTo(before);
        });

        assertThat(rows.count(INCIDENT_AGGREGATES))
                .as("строки отказавших суток не появилось: её порция откатилась").isZero();

        recompute();

        List<Map<String, Object>> handed = dealRows();
        assertThat(rowOf(handed, UNREACHED_DAY).get(CLOSED_DEALS))
                .as("второй такт прошёл окно ЦЕЛИКОМ: числа непройденных суток доведены")
                .isEqualTo(2);
        assertThat(moment(rowOf(handed, UNREACHED_DAY), ASSEMBLED_AT))
                .as("и момент их сборки обновлён")
                .isAfter(moment(before, ASSEMBLED_AT));
        assertThat(bucketDatesOf(incidentRows()))
                .as("строка зерна происшествий у отказавших суток собрана тем же тактом: "
                        + "обрыв между порциями не стоил ничего — догонять его нечем, кроме "
                        + "следующего такта")
                .containsExactly(day(FAILING_DAY));
        assertThat(rows.count(DEAL_AGGREGATES))
                .as("сделочных строк по-прежнему двое: повтор прохода строк не задваивает")
                .isEqualTo(2L);
    }

    /** Строки сделочного зерна, как их отдаёт поверхность чтения. */
    private List<Map<String, Object>> dealRows() {
        return aggregates(DEAL_GRAIN, TENANT).dealRows();
    }

    /** Строки зерна происшествий той же поверхностью. */
    private List<Map<String, Object>> incidentRows() {
        return aggregates(INCIDENT_GRAIN, TENANT).incidentRows();
    }
}
