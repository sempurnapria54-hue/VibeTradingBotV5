package com.example.auditstatistics;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.auditstatistics.domain.jobs.JobExecutionGuard;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Охрана джобы от перекрытия (.claude/rules/codestyle.md §Джобы).
 *
 * <p><b>Проверяется именно ОДНОВРЕМЕННОСТЬ, а не повтор.</b> Замок
 * реентерабелен: вложенный вызов из того же потока прошёл бы, и проба на
 * нём охраны не мерила бы вовсе. Перекрытие же по существу приходит из
 * второго потока — планировщик бьёт по CRON, не дожидаясь конца
 * предыдущего прохода.
 */
class JobExecutionGuardTest {

    private static final String JOB_NAME = "journalCleanupJob";

    private final JobExecutionGuard guard = new JobExecutionGuard();

    @Test
    @DisplayName("Пока проход идёт, перекрывающий пропускается")
    void anOverlappingTickIsSkipped() throws InterruptedException {
        AtomicInteger runs = new AtomicInteger();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread running = new Thread(() -> guard.runExclusively(JOB_NAME, () -> {
            runs.incrementAndGet();
            entered.countDown();
            awaitQuietly(release);
        }));
        running.start();
        entered.await();

        guard.runExclusively(JOB_NAME, runs::incrementAndGet);
        release.countDown();
        running.join();

        assertThat(runs.get())
                .as("такт CRON бьёт независимо от того, кончился ли предыдущий проход, а проход чистки "
                        + "длителен по построению")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Проход кончился — следующий идёт: замок отпускается")
    void theNextTickRunsAfterTheLockIsReleased() throws InterruptedException {
        AtomicInteger runs = new AtomicInteger();

        guard.runExclusively(JOB_NAME, runs::incrementAndGet);
        onAnotherThread(runs::incrementAndGet);

        assertThat(runs.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("Отказ тела замок отпускает: иначе джоба встала бы навсегда после первого падения")
    void aFailedTickReleasesTheLock() throws InterruptedException {
        AtomicInteger runs = new AtomicInteger();

        try {
            guard.runExclusively(JOB_NAME, () -> {
                throw new IllegalStateException("проход упал");
            });
        } catch (IllegalStateException expected) {
            runs.incrementAndGet();
        }
        onAnotherThread(runs::incrementAndGet);

        assertThat(runs.get())
                .as("незакрытый замок остался бы у ПОТОКА, который его взял, и следующий такт "
                        + "планировщика не прошёл бы никогда")
                .isEqualTo(2);
    }

    /**
     * Следующий такт идёт ДРУГИМ потоком, и это не оформление пробы.
     *
     * <p>Замок реентерабелен: повторный вызов из того же потока проходит
     * даже у невозвращённого замка, и проба на нём мерила бы reentrancy, а
     * не отпускание. Планировщик же бьёт своим потоком.
     */
    private void onAnotherThread(Runnable tick) throws InterruptedException {
        Thread next = new Thread(() -> guard.runExclusively(JOB_NAME, tick));
        next.start();
        next.join();
    }

    private void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
