package com.example.statistics.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетки {@code B7.1} и {@code B7.15}, а также первые два ожидания
 * {@code B7.17} — СУЖЕННОЕ окно пересчёта
 * (.claude/tests/cases/statistics.md §«B7 — Пересчёт: окно, порции и охрана
 * начала ряда»).
 *
 * <p><b>Ширина окна СДВИНУТА, и это вход клеток, а не подкрутка ради
 * скорости.</b> Ожидание {@code B7.1} говорит, что ширина взята ВЕЛИЧИНОЙ
 * КОНФИГУРАЦИИ, а не константой кода; на штатных семи сутках оно не
 * выразимо вовсе — они совпадают с умолчанием сервиса дословно, и клетка
 * прошла бы на реализации, читающей константу. Тот же довод, что у
 * допустимого возраста строки состояния ({@link StaleStateRowBoxTest}).
 * Двое суток выбраны так, чтобы между окном и умолчанием осталась
 * РАЗЛИЧАЮЩАЯ полоса: сутки, лежащие внутри семи и снаружи двух, и есть
 * то, на чём константа отличается от конфигурации.
 *
 * <p><b>Третье ожидание {@code B7.17} живёт в соседнем классе</b>
 * ({@link WidenedRecomputeWindowBoxTest}), и деление это вынужденное:
 * «расширение окна конфигурацией до {@code D0} и следующий такт числа
 * доводят» требует ВТОРОЙ ширины окна, а ось конфигурации связывается при
 * подъёме контекста и внутри класса не сдвигается. Оба конца при этом
 * строят один и тот же {@code D0} — {@link Aggregates#LATE_DAY}, — иначе
 * половины утверждали бы о разных сутках.
 *
 * <p><b>Строка суток вне окна кладётся ПРЯМОЙ ЗАПИСЬЮ</b>
 * ({@link Aggregates}): тропой ящика её не производится ни при каком числе
 * тактов, и ровно в этом предмет обеих клеток; довод — у самого помощника.
 *
 * <p><b>Такт подаёт сама клетка</b> ({@link #recompute()}), и ждать после
 * него нечего: строка агрегата асинхронным следом не является. Ждётся
 * только след ПРИЁМА у {@code B7.17} — там событие едет проводом, потому
 * что предмет клетки и есть опоздавшее событие.
 *
 * <p><b>Ассерт идёт ПОВЕРХНОСТЬЮ, а не колонками</b>, потому что у строк
 * агрегатов объявленный читатель есть и читает он их именно ею
 * (.claude/tests/cases/statistics.md §«Чем достаются выходы»). Окно чтения
 * при этом взято ШИРЕ окна пересчёта ({@link #aggregatesSince}): у́же него
 * «проход не собрал» и «чтение не спросило» стали бы неразличимы.
 */
class NarrowedRecomputeWindowBoxTest extends StatisticsBox {

    /** Краткое имя класса: из него строятся его группа и его тема. */
    private static final String SLUG = "b7-narrow-window";

    /**
     * Ширина окна пересчёта у этого контекста.
     *
     * <p>Она и есть сдвинутая ось: величина заметно меньше умолчания
     * сервиса, и сутки, накрытые умолчанием и не накрытые ею, отличают
     * конфигурацию от константы.
     */
    private static final Integer NARROW_WINDOW_DAYS = 2;

    /** Сутки ВНУТРИ суженного окна: ими читается, что проход работу сделал. */
    private static final Integer IN_WINDOW_DAY = 1;

    /**
     * Сутки СНАРУЖИ суженного окна и ВНУТРИ умолчания сервиса.
     *
     * <p>Они и есть различающая полоса: строка, появившаяся у них, означала
     * бы, что ширина прочитана константой кода, а не конфигурацией.
     */
    private static final Integer BEYOND_WINDOW_DAY = 5;

    /** Сколько суток назад открывается окно ЧТЕНИЯ: шире всех меряемых суток. */
    private static final Integer READ_SPAN = Aggregates.LATE_DAY + 1;

    /** Насколько в прошлом стои́т момент сборки положенной строки. */
    private static final Duration STALE_AGE = Duration.ofHours(3L);

    /** Идентичность опоздавшего события. */
    private static final String LATE_EVENT = "E-7-17-LATE";

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        StatisticsSubstrate.registerOwn(registry, SLUG,
                Map.of(StatisticsSubstrate.RECOMPUTE_WINDOW_KEY,
                        String.valueOf(NARROW_WINDOW_DAYS)));
    }

    @Test
    @DisplayName("B7.1 — Тик пересчитывает скользящее окно последних суток")
    void theTickRecomputesTheSlidingWindowOfTheLastDays() {
        // Ряд открывается сутками РАНЬШЕ обеих меряемых: охрана начала ряда
        // (B7.7) погасила бы сутки, покрытые рядом частично, и отсутствие их
        // строк читалось бы как исход окна, которым оно не является.
        Facts.deal("E-7-1-SERIES", TENANT, midnightDaysAgo(Aggregates.LATE_DAY));
        Facts.deal("E-7-1-BEYOND", TENANT, midnightDaysAgo(BEYOND_WINDOW_DAY));
        Facts.deal("E-7-1-INSIDE", TENANT, midnightDaysAgo(IN_WINDOW_DAY));

        recompute();

        List<Map<String, Object>> handed = read();
        assertThat(rows.count(DEAL_FACTS))
                .as("вход поставлен: фактов трое, и лежат они в трёх разных сутках")
                .isEqualTo(3L);
        assertThat(bucketDatesOf(handed))
                .as("строка собралась у суток ВНУТРИ окна")
                .contains(day(IN_WINDOW_DAY));
        assertThat(bucketDatesOf(handed))
                .as("а у суток на %s назад её нет, хотя УМОЛЧАНИЕ сервиса в %s суток их "
                                + "накрывает: ширина взята величиной конфигурации (%s суток), "
                                + "а не константой кода",
                        BEYOND_WINDOW_DAY, StatisticsSubstrate.RECOMPUTE_WINDOW_DAYS,
                        NARROW_WINDOW_DAYS)
                .doesNotContain(day(BEYOND_WINDOW_DAY));
        assertThat(bucketDatesOf(handed))
                .as("и у суток начала ряда её нет тоже: окно скользит от нынешних суток назад")
                .doesNotContain(day(Aggregates.LATE_DAY));
        assertThat(rows.count(DEAL_AGGREGATES))
                .as("строка в базе ровно одна: страница не скрыла ни одной собранной")
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("B7.15 — Строка вне окна не пересчитывается, и её момент застывает")
    void theRowOutsideTheWindowIsNotRecomputedAndItsMomentFreezes() {
        Facts.deal("E-7-15-OLD", TENANT, midnightDaysAgo(Aggregates.LATE_DAY));
        Facts.deal("E-7-15-ADDED", TENANT, midnightDaysAgo(Aggregates.LATE_DAY).plusHours(1L));
        Facts.deal("E-7-15-INSIDE", TENANT, midnightDaysAgo(IN_WINDOW_DAY));
        Aggregates.dealRow(TENANT, bucket(Aggregates.LATE_DAY), Aggregates.STALE_CLOSED_DEALS,
                momentsAgo(STALE_AGE));
        Map<String, Object> before = rowOf(read(), Aggregates.LATE_DAY);

        recompute();

        Map<String, Object> after = rowOf(read(), Aggregates.LATE_DAY);
        assertThat(before.get(CLOSED_DEALS))
                .as("вход поставлен: числа строки расходятся с рядом её суток — фактов там "
                        + "двое, а строка знает одну, и проход, дотянувшийся до этих суток, "
                        + "обязан был бы их изменить")
                .isEqualTo(Aggregates.STALE_CLOSED_DEALS);
        assertThat(after)
                .as("строка суток вне окна не изменилась ни одним числом и ни одним моментом")
                .isEqualTo(before);
        Map<String, Object> inside = rowOf(read(), IN_WINDOW_DAY);
        assertThat(moment(inside, ASSEMBLED_AT))
                .as("а проход прошёл и работу сделал: у суток внутри окна момент сборки свежий")
                .isAfter(moment(before, ASSEMBLED_AT));
        assertThat(moment(after, ASSEMBLED_AT))
                .as("два состояния различает именно МОМЕНТ: у строки вне окна он застыл на "
                        + "прежнем — «пересчитывать нечего», а не «устарело»")
                .isEqualTo(moment(before, ASSEMBLED_AT));
    }

    @Test
    @DisplayName("B7.17 — Событие, приехавшее с опозданием больше окна, свои сутки не двигает")
    void anEventLaterThanTheWindowDoesNotMoveItsDay() {
        OffsetDateTime lateMoment = midnightDaysAgo(Aggregates.LATE_DAY).plusHours(12L);
        Facts.deal("E-7-17-OLD", TENANT, midnightDaysAgo(Aggregates.LATE_DAY));
        Aggregates.dealRow(TENANT, bucket(Aggregates.LATE_DAY), Aggregates.STALE_CLOSED_DEALS,
                momentsAgo(STALE_AGE));
        Map<String, Object> before = rowOf(read(), Aggregates.LATE_DAY);

        publish(LATE_EVENT, DEAL_CLOSED, lateMoment,
                Bodies.dealClosed(Facts.ACCOUNT, Facts.STRATEGY));
        awaitDealFactCount(2L);

        recompute();

        Map<String, Object> late = rows.row(DEAL_FACTS, "event_id", LATE_EVENT);
        assertThat(late)
                .as("факт записан: приёму опоздание безразлично — момент едет конвертом, "
                        + "а не часами приёма")
                .isNotEmpty();
        assertThat(instant(late, CLOSED_COLUMN))
                .as("и ось времени у него та, что приехала конвертом: сутки его — D0")
                .isEqualTo(lateMoment.toInstant());
        assertThat(rowOf(read(), Aggregates.LATE_DAY))
                .as("а строка суток D0 не изменилась и момента сборки не обновила: "
                        + "опоздание больше окна проход не догоняет, и цена эта названа домом")
                .isEqualTo(before);
        assertThat(rows.count(DEAL_AGGREGATES))
                .as("второй строки тех же суток приём тоже не завёл: агрегат событием "
                        + "не двигается вовсе")
                .isEqualTo(1L);
    }

    /** Строки сделочного зерна за окно чтения шире окна пересчёта. */
    private List<Map<String, Object>> read() {
        return aggregatesSince(DEAL_GRAIN, TENANT, READ_SPAN).dealRows();
    }
}
