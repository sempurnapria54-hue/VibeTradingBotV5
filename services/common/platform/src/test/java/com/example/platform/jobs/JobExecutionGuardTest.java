package com.example.platform.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * Охрана джобы от перекрытия: группы `U1`, `U2` и клетка `U14.8` документа
 * `.claude/tests/cases/platform-shared-logic.md`
 * (.claude/rules/codestyle.md §Джобы).
 *
 * <p><b>Проверяется именно ОДНОВРЕМЕННОСТЬ, а не повтор.</b> Замок
 * реентерабелен: вложенный вызов из того же потока прошёл бы, и проба на
 * нём охраны не мерила бы вовсе (`U1.5`). Перекрытие же по существу
 * приходит из второго потока — планировщик бьёт по CRON, не дожидаясь конца
 * предыдущего прохода. Синхронизация кейсов — защёлки, а не паузы: пауза
 * платит временем всегда и детерминированной не является.
 *
 * <p><b>Проба живёт у формы, а не у потребителя.</b> Прежде её копии
 * стоя́ли в тестовых деревьях {@code audit} и {@code statistics} — то есть
 * охрана охраны снималась бы вместе с чужим деревом, и ни один заход не
 * заводил бы её взамен (§«Решение: где живёт дерево прогона»). Имя джобы
 * здесь — ключ замка, а не предмет: у формы потребителей пятеро, и ни один
 * из них тут не назван.
 */
class JobExecutionGuardTest {

    private static final String JOB_NAME = "someJob";
    private static final String OTHER_JOB_NAME = "otherJob";

    private final JobExecutionGuard guard = new JobExecutionGuard();
    private final AtomicInteger runs = new AtomicInteger();
    private final ListAppender<ILoggingEvent> logged = new ListAppender<>();

    @BeforeEach
    void captureLog() {
        logged.start();
        guardLogger().addAppender(logged);
    }

    @AfterEach
    void releaseLog() {
        guardLogger().detachAppender(logged);
        logged.stop();
    }

    // --- U1: перекрытие, отпускание, наблюдаемость пропуска ---------------

    @Test
    @DisplayName("U1.1 — пока проход идёт, перекрывающий пропускается и уходит в лог")
    void u1_1_anOverlappingTickIsSkippedAndLogged() throws InterruptedException {
        Held held = holdTickInside(JOB_NAME);

        assertThatCode(() -> guard.runExclusively(JOB_NAME, runs::incrementAndGet))
                .as("перекрывающий тик возвращается нормально: исключения у пропуска нет")
                .doesNotThrowAnyException();
        held.releaseAndJoin();

        assertThat(runs.get())
                .as("такт CRON бьёт независимо от того, кончился ли предыдущий проход")
                .isEqualTo(1);
        assertThat(logged.list)
                .as("единственный след пропуска — запись лога с именем джобы")
                .anySatisfy(event -> {
                    assertThat(event.getLevel()).isEqualTo(Level.WARN);
                    assertThat(event.getFormattedMessage()).contains(JOB_NAME);
                });
    }

    @Test
    @DisplayName("U1.2 — проход кончился: следующий идёт, замок отпущен")
    void u1_2_theNextTickRunsAfterTheLockIsReleased() throws InterruptedException {
        guard.runExclusively(JOB_NAME, runs::incrementAndGet);
        onAnotherThread(JOB_NAME, runs::incrementAndGet);

        assertThat(runs.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("U1.3 — отказ тела уходит вызывающему нетронутым, а замок отпущен")
    void u1_3_aFailedTickReleasesTheLockAndKeepsTheFailure() throws InterruptedException {
        assertThatThrownBy(() -> guard.runExclusively(JOB_NAME, () -> {
            throw new IllegalStateException("проход упал");
        }))
                .as("охрана класса не подменяет и не глотает")
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("проход упал");

        onAnotherThread(JOB_NAME, runs::incrementAndGet);

        assertThat(runs.get())
                .as("незакрытый замок остался бы у потока, который его взял, и следующий "
                        + "такт планировщика не прошёл бы никогда")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("U1.4 — разные имена друг друга не держат: замок берётся по имени")
    void u1_4_differentJobNamesDoNotBlockEachOther() throws InterruptedException {
        Held held = holdTickInside(JOB_NAME);

        onAnotherThread(OTHER_JOB_NAME, runs::incrementAndGet);
        held.releaseAndJoin();

        assertThat(runs.get())
                .as("одно имя — один замок; разные джобы независимы")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("U1.5 — вложенный вызов того же имени проходит: замок реентерабелен")
    void u1_5_aNestedCallOfTheSameNamePasses() throws InterruptedException {
        guard.runExclusively(JOB_NAME, () -> {
            runs.incrementAndGet();
            guard.runExclusively(JOB_NAME, runs::incrementAndGet);
        });

        assertThat(runs.get())
                .as("охраной реентерабельность не мерится — предмет охраны приходит из второго потока")
                .isEqualTo(2);

        onAnotherThread(JOB_NAME, runs::incrementAndGet);
        assertThat(runs.get())
                .as("замок отпущен после выхода из обоих уровней")
                .isEqualTo(3);
    }

    @Test
    @DisplayName("U1.6 — пропуск вызывающему не наблюдается: метод объявлен void")
    void u1_6_theSkipIsInvisibleToTheCaller() throws Exception {
        Method runExclusively = JobExecutionGuard.class
                .getMethod("runExclusively", String.class, Runnable.class);
        Held held = holdTickInside(JOB_NAME);

        assertThatCode(() -> guard.runExclusively(JOB_NAME, runs::incrementAndGet))
                .doesNotThrowAnyException();
        held.releaseAndJoin();

        assertThat(runExclusively.getReturnType())
                .as("отличить пропуск от прохода можно только счётчиком тела, которого у фасада нет "
                        + "— подтверждение припаркованного долга об обещанном 409")
                .isEqualTo(void.class);
        assertThat(runs.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("U1.7 — пустое имя: отказ приходит ДО взятия замка")
    void u1_7_anEmptyJobNameFailsBeforeTheLockIsTaken() throws InterruptedException {
        assertThatThrownBy(() -> guard.runExclusively(null, runs::incrementAndGet))
                .isInstanceOf(NullPointerException.class);

        assertThat(runs.get()).as("тело не исполнено").isZero();
        onAnotherThread(JOB_NAME, runs::incrementAndGet);
        assertThat(runs.get())
                .as("замка не заведено ни одного — охрана не встала колом")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("U1.8 — пустое тело: отказ приходит ПОСЛЕ взятия замка, и замок отпущен")
    void u1_8_anEmptyTickFailsAfterTheLockIsTakenAndReleasesIt() throws InterruptedException {
        assertThatThrownBy(() -> guard.runExclusively(JOB_NAME, null))
                .isInstanceOf(NullPointerException.class);

        onAnotherThread(JOB_NAME, runs::incrementAndGet);

        assertThat(runs.get())
                .as("замок отпущен finally — следующий тик тем же именем проходит")
                .isEqualTo(1);
    }

    // --- U2: радиус замка и названные ограничения -------------------------

    @Test
    @DisplayName("U2.1 — два экземпляра охраны одного имени друг друга не держат")
    void u2_1_twoGuardInstancesDoNotShareTheLock() throws InterruptedException {
        Held held = holdTickInside(JOB_NAME);
        JobExecutionGuard secondInstance = new JobExecutionGuard();

        Thread other = new Thread(() -> secondInstance.runExclusively(JOB_NAME, runs::incrementAndGet));
        other.start();
        other.join();
        held.releaseAndJoin();

        assertThat(runs.get())
                .as("замок in-memory и на экземпляр: на нескольких репликах охраны нет — "
                        + "объявленное ограничение, а не дефект кейса")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("U2.3 — N тиков одного имени берут ОДИН замок, а не заводят себе новый")
    void u2_3_repeatedTicksOfOneNameShareASingleLock() throws InterruptedException {
        int ticksBeforeTheHeldOne = 4;
        for (int tick = 0; tick < ticksBeforeTheHeldOne; tick++) {
            guard.runExclusively(JOB_NAME, runs::incrementAndGet);
        }

        Held held = holdTickInside(JOB_NAME);
        guard.runExclusively(JOB_NAME, runs::incrementAndGet);
        held.releaseAndJoin();

        assertThat(runs.get())
                .as("все N тел исполнены, перекрывающий при удерживаемом N-м пропущен: "
                        + "новый замок каждым тиком пропустил бы его")
                .isEqualTo(ticksBeforeTheHeldOne + 1);
    }

    // --- U14.8: охрана не ждёт --------------------------------------------

    @Test
    @DisplayName("U14.8 — перекрывающий тик уходит сразу, а не ждёт освобождения замка")
    void u14_8_anOverlappingTickDoesNotWaitForTheLock() throws InterruptedException {
        Held held = holdTickInside(JOB_NAME);

        guard.runExclusively(JOB_NAME, runs::incrementAndGet);

        assertThat(held.release.getCount())
                .as("вызов вернулся, пока тело ещё удерживается: попытка неблокирующая")
                .isEqualTo(1);
        held.releaseAndJoin();
    }

    // --- оснастка ---------------------------------------------------------

    /** Тик, удерживаемый внутри тела, пока кейс его не отпустит. */
    private record Held(Thread thread, CountDownLatch release) {

        void releaseAndJoin() throws InterruptedException {
            release.countDown();
            thread.join();
        }
    }

    private Held holdTickInside(String jobName) throws InterruptedException {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread running = new Thread(() -> guard.runExclusively(jobName, () -> {
            runs.incrementAndGet();
            entered.countDown();
            awaitQuietly(release);
        }));
        running.start();
        entered.await();
        return new Held(running, release);
    }

    /**
     * Следующий такт идёт ДРУГИМ потоком, и это не оформление пробы.
     *
     * <p>Замок реентерабелен: повторный вызов из того же потока проходит
     * даже у невозвращённого замка, и проба на нём мерила бы reentrancy, а
     * не отпускание. Планировщик же бьёт своим потоком.
     */
    private void onAnotherThread(String jobName, Runnable tick) throws InterruptedException {
        Thread next = new Thread(() -> guard.runExclusively(jobName, tick));
        next.start();
        next.join();
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static ch.qos.logback.classic.Logger guardLogger() {
        return (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(JobExecutionGuard.class);
    }
}
