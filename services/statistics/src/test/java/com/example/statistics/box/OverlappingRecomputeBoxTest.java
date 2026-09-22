package com.example.statistics.box;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетка {@code B7.20} — перекрывающий запуск пересчёта гасится
 * (.claude/tests/cases/statistics.md §«B7 — Пересчёт: окно, порции и охрана
 * начала ряда»).
 *
 * <p><b>Класс свой, и делит его не ось конфигурации, а СОСТОЯНИЕ
 * СУБСТРАТА</b> — тот же довод, что у клеток порции
 * ({@link RecomputePortionBoundaryBoxTest}): удержание прохода ставится
 * снаружи, замком таблицы, и держать его вместе с клетками, идущими штатно,
 * значило бы скрыть, чем клетка платит.
 *
 * <p><b>Предмет клетки — ВОЗВРАТ второго такта, а не исход первого.</b>
 * Охрана здесь есть по механическому признаку правила: такт задан CRON, и он
 * бьёт независимо от того, кончился ли предыдущий проход
 * (.claude/rules/codestyle.md §Джобы). Проход же длителен по построению —
 * порций в нём столько, сколько суток в окне, — поэтому перекрытие у этой
 * джобы не умозрительно.
 *
 * <p><b>Удержание ставится замком таблицы, а не долгим проходом.</b> Проход,
 * растянутый объёмом фактов, оставил бы клетку гонкой: она была бы зелена,
 * пока машина прогона медленнее порога, и краснела бы на быстрой. Замок
 * останавливает проход ТАМ, где он уже внутри охраны, и отпускает его ходом
 * клетки.
 *
 * <p><b>Что проходов было ровно два, а работы — на один, читается счётчиком
 * запросов базы.</b> По строкам агрегатов «один проход» и «два прохода,
 * второй впустую» совпадают дословно: строка была бы одна и та же. Счёт
 * исполнений запроса группировки их различает, и берётся он снаружи — ни
 * один бин сервиса не подменён ({@link StatisticsSubstrate}).
 *
 * <p><b>Ряд фактов открыт полночью НЫНЕШНИХ суток намеренно.</b> Проход идёт
 * от свежих суток к старым, а охрана начала ряда ({@code B7.7}) гасит сутки,
 * покрытые рядом частично: так порция у прохода ровно одна, и запереть её
 * значит запереть весь проход.
 */
class OverlappingRecomputeBoxTest extends StatisticsBox {

    /** Краткое имя клетки: из него строятся её группа и её тема. */
    private static final String SLUG = "b7-20";

    /**
     * Потолок ожидания перекрывающего такта.
     *
     * <p>Ограничен, потому что предмет клетки и есть ВОЗВРАТ: такт,
     * дождавшийся идущего прохода, охраной не пропущен, и различает их
     * ровно срок.
     */
    private static final Duration SKIP_WINDOW = Duration.ofSeconds(15);

    /** Запись охраны о пропущенном такте — единственный её наблюдаемый след. */
    private static final String OVERLAP_SKIPPED = "aggregateRecomputeJob is already running";

    /** Сколько потоков нужно клетке: удерживаемый проход и перекрывающий такт. */
    private static final Integer PASSES = 2;

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        StatisticsSubstrate.registerOwn(registry, SLUG);
    }

    @Test
    @DisplayName("B7.20 — Перекрывающий запуск пересчёта гасится")
    void anOverlappingRecomputeRunIsSkipped() {
        Facts.deal("E-7-20-DEAL", TENANT, midnightDaysAgo(0));
        rows.resetStatementCounters();
        AtomicReference<Future<?>> held = new AtomicReference<>();
        ExecutorService passes = Executors.newFixedThreadPool(PASSES);
        Integer logMark = AppLog.mark();

        try {
            rows.withTableLocked(DEAL_AGGREGATES, () -> {
                held.set(passes.submit(this::recompute));
                awaitHeldOnPortion();
                Future<?> overlapping = passes.submit(this::recompute);

                assertThatCode(() -> overlapping.get(SKIP_WINDOW.toMillis(), TimeUnit.MILLISECONDS))
                        .as("перекрывающий такт вернулся, не дождавшись идущего прохода")
                        .doesNotThrowAnyException();
                assertThat(AppLog.alarmsSince(logMark))
                        .as("и вернулся он ПРОПУСКОМ: по строкам агрегатов оба исхода "
                                + "совпали бы — пересчёт идемпотентен")
                        .anyMatch(alarm -> alarm.contains(OVERLAP_SKIPPED));
                assertThat(rows.statementCalls(DEAL_GRAIN_QUERY))
                        .as("а работы перекрывающий такт не сделал ни на один запрос: "
                                + "группировку исполнил только удерживаемый проход")
                        .isEqualTo(1L);
            });
            Awaitility.await().atMost(RECEPTION_WAIT).pollInterval(POLL).until(held.get()::isDone);
            assertThatCode(() -> held.get().get())
                    .as("отпущенный проход дошёл до конца: перекрывающий такт его не тронул")
                    .doesNotThrowAnyException();
        } finally {
            passes.shutdownNow();
        }

        List<Map<String, Object>> handed = aggregates(DEAL_GRAIN, TENANT).dealRows();
        assertThat(handed)
                .as("строка собрана одна: двумя проходами одновременно её не переписывали")
                .hasSize(1);
        assertThat(rowOf(handed, 0).get(CLOSED_DEALS))
                .as("и числа в ней от одного прохода, а не сложены двумя").isEqualTo(1);
        assertThat(rows.statementCalls(DEAL_GRAIN_QUERY))
                .as("за всю клетку запрос группировки исполнен единожды: тактов было два, "
                        + "проход — один")
                .isEqualTo(1L);
    }

    /** Ждёт, пока проход не встанет за замком порции: раньше мерилась бы гонка. */
    private void awaitHeldOnPortion() {
        Awaitility.await()
                .atMost(RECEPTION_WAIT)
                .pollInterval(POLL)
                .until(() -> rows.waitingLocksOn(DEAL_AGGREGATES) > 0L);
    }
}
