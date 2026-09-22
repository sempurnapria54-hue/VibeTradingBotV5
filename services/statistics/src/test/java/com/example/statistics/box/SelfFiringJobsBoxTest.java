package com.example.statistics.box;

import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетка {@code B12.7}, её ТАКТЫ — оба такта сервиса приходят конфигурацией
 * (.claude/tests/cases/statistics.md §«B12 — Конфигурация и схема как вход»).
 *
 * <p><b>Такта ящик здесь не подаёт, и в этом весь предмет.</b> Прочие клетки
 * прогона бьют обе джобы прямым вызовом метода — единственной точкой касания
 * бина (.claude/decisions/test-contour-design-pass.md, решение 6), — потому
 * что штатные оси выражены часовой паузой и моментом, до которого прогон не
 * доживает. Здесь наоборот: вызова нет ни одного, и работу производят САМИ
 * джобы. При штатных осях ни строк состояния, ни строк агрегатов за всё
 * время клеток не появилось бы.
 *
 * <p><b>Мало появления строк — нужен ПОВТОР такта.</b> Строки завела бы и
 * одиночная сработка при любом такте; что такт именно назначенный,
 * предъявляет момент: у строки состояния его пишет только тик, у строки
 * агрегата — только проход, и оба ставят момент своей работы. Момент,
 * сдвинувшийся внутри окна клетки, есть вторая сработка, случившаяся без
 * всякого участия кейса.
 *
 * <p><b>Тактов у сервиса ДВА, и выражены они разными формами</b> — паузой
 * между концами у тика приёма ({@code fixedDelayString}) и выражением
 * расписания у пересчёта ({@code cron}), — поэтому клеток здесь тоже две:
 * одна форма о другой не свидетельствует.
 *
 * <p><b>Своя группа и своя тема обязательны:</b> живой тик этого контекста
 * писал бы строки чужих пар, будь группа общей.
 *
 * <p><b>Контекст закрывается вместе с классом</b> ({@link DirtiesContext}), и
 * довод механический: контексты каркас теста кэширует и не закрывает, а обе
 * джобы этого контекста бьются каждую секунду до конца прогона — проход
 * пересчёта при этом читает таблицы фактов, которые соседние клетки у базы
 * на время ОТНИМАЮТ ({@link Rows#withoutTable}). Оставленный жить, он стал
 * бы чужой нагрузкой и чужим шумом в журнале. Закрытие ничего не теряет:
 * контекст у класса свой, и переиспользовать его некому.
 */
@DirtiesContext
class SelfFiringJobsBoxTest extends StatisticsBox {

    /** Краткое имя клеток: из него строятся их группа и их тема. */
    private static final String SLUG = "b12-7-tacts";

    /** Такт тика приёма этого контекста: полсекунды вместо часа. */
    private static final Duration TICK_INTERVAL = Duration.ofMillis(500);

    /** Выражение такта пересчёта этого контекста: каждую секунду. */
    private static final String RECOMPUTE_CRON = "*/1 * * * * *";

    /**
     * Окно наблюдения второй сработки.
     *
     * <p>Оно на порядки у́же штатных осей — часовой паузы и годового
     * выражения, — и сдвиг момента внутри него не объясним ничем, кроме
     * назначенного такта.
     */
    private static final Duration SELF_FIRING_WINDOW = Duration.ofSeconds(20);

    /** Сутки, в которых лежит факт клетки о такте пересчёта. */
    private static final Integer DAY = 1;

    /** Колонка суток зерна: ею читается, за какие сутки собрана строка. */
    private static final String BUCKET_COLUMN = "bucket_date";

    /** Колонка момента сборки: её пишет только проход. */
    private static final String ASSEMBLED_COLUMN = "assembled_at";

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        Map<String, String> axes = new LinkedHashMap<>();
        axes.put(StatisticsSubstrate.STATE_TICK_INTERVAL_KEY, TICK_INTERVAL.toMillis() + "ms");
        axes.put(StatisticsSubstrate.RECOMPUTE_CRON_KEY, RECOMPUTE_CRON);
        StatisticsSubstrate.registerOwn(registry, SLUG, axes);
    }

    @Test
    @DisplayName("B12.7 — Такт тика приёма приходит конфигурацией: удары идут без участия кейса")
    void theReceptionTickIntervalArrivesByConfigurationAndFiresOnItsOwn() {
        Awaitility.await()
                .atMost(SELF_FIRING_WINDOW)
                .pollInterval(POLL)
                .until(() -> Objects.equals(rows.count(RECEPTION_TABLE),
                        (long) subscription().size()));

        Object firstUpdate = pair(subject()).get(UPDATED_COLUMN);
        Awaitility.await()
                .atMost(SELF_FIRING_WINDOW)
                .pollInterval(POLL)
                .until(() -> isFalse(Objects.equals(firstUpdate,
                        pair(subject()).get(UPDATED_COLUMN))));

        assertThat(pairs())
                .as("строки пар завёл сам тик: такта кейс не подавал ни разу")
                .hasSize(subscription().size());
        assertThat(pair(subject()).get(UPDATED_COLUMN))
                .as("момент обновления сдвинут второй сработкой, случившейся по назначенной оси")
                .isNotEqualTo(firstUpdate);
    }

    @Test
    @DisplayName("B12.7 — Такт пересчёта приходит конфигурацией: проходы идут без участия кейса")
    void theRecomputeCronArrivesByConfigurationAndFiresOnItsOwn() {
        Facts.deal("E-B12-7-CRON", TENANT, midnightDaysAgo(DAY));

        Awaitility.await()
                .atMost(SELF_FIRING_WINDOW)
                .pollInterval(POLL)
                .until(() -> Objects.equals(rows.count(DEAL_AGGREGATES), 1L));

        Map<String, Object> assembled = rows.all(DEAL_AGGREGATES, BUCKET_COLUMN).getFirst();
        Object firstAssembly = assembled.get(ASSEMBLED_COLUMN);
        Awaitility.await()
                .atMost(SELF_FIRING_WINDOW)
                .pollInterval(POLL)
                .until(() -> isFalse(Objects.equals(firstAssembly,
                        rows.all(DEAL_AGGREGATES, BUCKET_COLUMN).getFirst().get(ASSEMBLED_COLUMN))));

        assertThat(String.valueOf(assembled.get(BUCKET_COLUMN)))
                .as("строку собрал сам проход: прохода кейс не подавал ни разу")
                .isEqualTo(bucket(DAY).toString());
        assertThat(rows.all(DEAL_AGGREGATES, BUCKET_COLUMN).getFirst().get(ASSEMBLED_COLUMN))
                .as("момент сборки сдвинут вторым проходом, случившимся по назначенному "
                        + "выражению такта")
                .isNotEqualTo(firstAssembly);
    }

    /** Тема, по строке которой клетка читает моменты ударов. */
    private String subject() {
        return StatisticsSubstrate.ownTopic(SLUG);
    }
}
