package com.example.statistics.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетка {@code B7.16} и третье ожидание {@code B7.17} — РАСШИРЕННОЕ окно
 * пересчёта (.claude/tests/cases/statistics.md §«B7 — Пересчёт: окно, порции
 * и охрана начала ряда»).
 *
 * <p><b>Ширина окна СДВИНУТА в другую сторону, и это вход обеих клеток.</b>
 * «Полный пересчёт» объявлен ЗНАЧЕНИЕМ окна, а не вторым исполнителем
 * ({@code AggregateRecomputeProperties}), и предъявить это можно только
 * окном, которого умолчание сервиса не даёт: на штатных семи сутках ряд,
 * лежащий дальше них, остался бы несобранным, и клетка мерила бы умолчание,
 * а не конфигурацию. Двенадцать суток накрывают весь ряд клетки и при этом
 * заметно отличаются от семи — иначе «расширено конфигурацией» было бы
 * неотличимо от «прочитано константой».
 *
 * <p><b>Первые два ожидания {@code B7.17} живут в соседнем классе</b>
 * ({@link NarrowedRecomputeWindowBoxTest}): они требуют окна, в которое
 * {@code D0} НЕ входит, а ось конфигурации связывается при подъёме контекста.
 * {@code D0} у обоих концов один — {@link Aggregates#LATE_DAY}.
 *
 * <p><b>Отрицание «второго исполнителя нет» читается двумя наблюдателями,
 * и ни один из них не есть чтение бинов контекста</b>
 * (.claude/decisions/test-contour-design-pass.md, решение 1): МОМЕНТ СБОРКИ
 * у всех строк прохода — один, и второй момент означал бы второй проход;
 * РЕСУРСЫ сервиса несут ровно одну конфигурацию, ровно одно объявление
 * блока пересчёта и ровно один такт — джоба без такта в конфигурации не
 * бьётся вовсе. Конфигурация читается здесь как ВХОД ящика — тем же родом,
 * что оси {@code @DynamicPropertySource}, — а не как его внутренность:
 * поверхности, отвечающей «сколько у меня джоб», у сервиса нет.
 *
 * <p><b>Ручной точки «пересчитать всё» эта клетка НЕ перебирает, и это не
 * пробел.</b> Носитель у отрицания уже есть, и он сильнее всякого перебора
 * имён: {@code B3.13} ({@link ReceptionStateTickBoxTest}) читает описание
 * поверхности и утверждает, что отображённый маршрут РОВНО ОДИН — то есть
 * закрывает тропу под любым именем, а не под угаданными. Второй носитель
 * той же истины был бы дублем (Д1828, .claude/rules/carrier-levels.md), и
 * к тому же более слабым.
 */
class WidenedRecomputeWindowBoxTest extends StatisticsBox {

    /** Краткое имя класса: из него строятся его группа и его тема. */
    private static final String SLUG = "b7-wide-window";

    /**
     * Ширина окна пересчёта у этого контекста.
     *
     * <p>Она накрывает весь ряд фактов клетки и заметно больше умолчания
     * сервиса: на умолчании дальние сутки ряда остались бы несобранными.
     */
    private static final Integer WIDE_WINDOW_DAYS = 12;

    /** Дальние сутки ряда: ими открывается ряд фактов. */
    private static final Integer FAR_DAY = 11;

    /** Ближние сутки ряда: они накрыты и умолчанием сервиса. */
    private static final Integer NEAR_DAY = 1;

    /** Сколько суток назад открывается окно ЧТЕНИЯ: шире окна пересчёта. */
    private static final Integer READ_SPAN = 13;

    /** Насколько в прошлом стои́т момент сборки положенной строки. */
    private static final Duration STALE_AGE = Duration.ofHours(3L);

    /** Имя конфигурации сервиса: от неё считается их число. */
    private static final String CONFIGURATION = "/application.yaml";

    /** Объявление блока пересчёта в конфигурации сервиса. */
    private static final String RECOMPUTE_BLOCK = "aggregate-recompute:";

    /** Объявление такта расписания в конфигурации сервиса. */
    private static final String SCHEDULE_DECLARATION = "cron:";

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        StatisticsSubstrate.registerOwn(registry, SLUG,
                Map.of(StatisticsSubstrate.RECOMPUTE_WINDOW_KEY, String.valueOf(WIDE_WINDOW_DAYS)));
    }

    @Test
    @DisplayName("B7.16 — «Полный пересчёт» — значение окна, а не второй исполнитель")
    void aFullRecomputeIsAWindowValueAndNotASecondExecutor() {
        Facts.deal("E-7-16-FAR", TENANT, midnightDaysAgo(FAR_DAY));
        Facts.deal("E-7-16-LATE", TENANT, midnightDaysAgo(Aggregates.LATE_DAY));
        Facts.deal("E-7-16-NEAR", TENANT, midnightDaysAgo(NEAR_DAY));

        recompute();

        List<Map<String, Object>> handed = read();
        assertThat(bucketDatesOf(handed))
                .as("строки собраны по ВСЕМУ ряду — включая сутки дальше умолчания сервиса "
                                + "в %s суток: полный пересчёт есть значение окна",
                        StatisticsSubstrate.RECOMPUTE_WINDOW_DAYS)
                .containsExactlyInAnyOrder(day(FAR_DAY), day(Aggregates.LATE_DAY), day(NEAR_DAY));
        assertThat(momentsOf(handed))
                .as("момент сборки у всех троих ОДИН: их собрал один проход одним тиком — "
                        + "второй момент означал бы второго исполнителя")
                .hasSize(1);

        assertThat(configurationResources())
                .as("отдельного профиля под полный пересчёт у сервиса нет: конфигурация одна")
                .containsExactly(CONFIGURATION.substring(1));
        assertThat(declarationsOf(RECOMPUTE_BLOCK))
                .as("и блок пересчёта в ней объявлен ровно один").isEqualTo(1L);
        assertThat(declarationsOf(SCHEDULE_DECLARATION))
                .as("такт расписания — тоже ровно один: второй джобы, бьющейся своим тактом, "
                        + "в конфигурации не объявлено").isEqualTo(1L);
    }

    @Test
    @DisplayName("B7.17 — Расширение окна до суток события доводит их числа следующим тактом")
    void wideningTheWindowBringsTheLateDayNumbersUp() {
        Facts.deal("E-7-17W-OLD", TENANT, midnightDaysAgo(Aggregates.LATE_DAY));
        Facts.deal("E-7-17W-LATE", TENANT, midnightDaysAgo(Aggregates.LATE_DAY).plusHours(12L));
        Aggregates.dealRow(TENANT, bucket(Aggregates.LATE_DAY), Aggregates.STALE_CLOSED_DEALS,
                momentsAgo(STALE_AGE));
        Map<String, Object> before = rowOf(read(), Aggregates.LATE_DAY);

        recompute();

        Map<String, Object> after = rowOf(read(), Aggregates.LATE_DAY);
        assertThat(before.get(CLOSED_DEALS))
                .as("вход поставлен: строка суток D0 знает одну сделку, а фактов там двое — "
                        + "опоздавший в числа не вошёл")
                .isEqualTo(Aggregates.STALE_CLOSED_DEALS);
        assertThat(after.get(CLOSED_DEALS))
                .as("расширенное окно накрыло D0, и следующий такт числа ДОВЁЛ: сутки "
                        + "пересобраны целиком, а не дополнены дельтой")
                .isEqualTo(2);
        assertThat(moment(after, ASSEMBLED_AT))
                .as("и момент сборки обновился: строку тронул этот проход")
                .isAfter(moment(before, ASSEMBLED_AT));
        assertThat(rows.count(DEAL_AGGREGATES))
                .as("строка ТА ЖЕ, а не вторая: upsert нашёл её по именованному ключу зерна")
                .isEqualTo(1L);
    }

    /** Строки сделочного зерна за окно чтения шире окна пересчёта. */
    private List<Map<String, Object>> read() {
        return aggregatesSince(DEAL_GRAIN, TENANT, READ_SPAN).dealRows();
    }

    /** Различные моменты сборки у строк выдачи. */
    private static Set<String> momentsOf(List<Map<String, Object>> handed) {
        return handed.stream().map(row -> String.valueOf(row.get(ASSEMBLED_AT)))
                .collect(Collectors.toSet());
    }

    /**
     * Сутки зерна, отстоящие от нынешних на названное число.
     *
     * @param daysBack сколько суток назад от нынешних
     */
    private static LocalDate bucket(Integer daysBack) {
        return LocalDate.now(ZoneOffset.UTC).minusDays(daysBack);
    }

    /**
     * Сколько раз конфигурация сервиса объявляет названное.
     *
     * @param declaration ключ объявления вместе с двоеточием
     */
    private Long declarationsOf(String declaration) {
        return configuration().lines()
                .filter(line -> line.strip().startsWith(declaration))
                .count();
    }

    /** Имена файлов конфигурации в ресурсах сервиса. */
    private List<String> configurationResources() {
        URL located = getClass().getResource(CONFIGURATION);
        try (Stream<Path> files = Files.list(Path.of(located.toURI()).getParent())) {
            return files.map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith("application") && name.endsWith(".yaml"))
                    .sorted()
                    .toList();
        } catch (IOException | URISyntaxException failure) {
            throw new IllegalStateException("Ресурсы сервиса не прочитались", failure);
        }
    }

    /** Конфигурация сервиса дословно. */
    private String configuration() {
        try (InputStream source = getClass().getResourceAsStream(CONFIGURATION)) {
            return new String(source.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new IllegalStateException("Конфигурация сервиса не прочиталась", failure);
        }
    }
}
