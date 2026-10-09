package com.example.connector.okx.box;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Воронка мишени {@code -D}: пауза-троттл между вызовами и backoff на ответе
 * о превышении лимита (.claude/tests/case-material/connector-okx.md §«2.
 * Инвариант восстановления состояния stateful-кейса», цена прогона).
 *
 * <p><b>Через неё идёт ВСЁ, что набор шлёт к площадке:</b> вызовы поверхности
 * ящика, которые коннектор переводит в запрос площадке, и собственные чтения
 * и зачистки набора. Лимиты площадки считаются по ключу и по адресу, а не по
 * отправителю, и у demo-счёта стенда тот же ключ читают живые стратегии
 * ядра: набор, выбравший лимит, мешал бы им.
 *
 * <p><b>Ответ о лимите — пауза и повтор, а не отказ кейса</b> ({@code B11.4-D}).
 * Пауза растёт вдвое до потолка; повторы ограничены бюджетом кейса, и
 * исчерпанный бюджет — отказ с названной причиной, а не бесконечное ожидание.
 * Пауза здесь и есть предмет, поэтому она — сон, а не {@code Awaitility}:
 * ждать нечего, кроме окна лимита.
 *
 * <p><b>Ответы о лимите считаются</b>: {@code B11.4-D} утверждает, что backoff
 * случился, и без счёта серия, прошедшая под лимитом, была бы неотличима от
 * серии, у которой backoff сработал.
 */
final class Funnel {

    /** Пауза между вызовами площадки: держит набор ниже самых узких лимитов чтения. */
    private static final Duration PACE = Duration.ofMillis(300);

    /** Первая пауза после ответа о лимите: окно лимита площадки — две секунды. */
    private static final Duration FIRST_BACKOFF = Duration.ofSeconds(1);

    /** Потолок паузы. */
    private static final Duration MAX_BACKOFF = Duration.ofSeconds(8);

    /** Сдвиг паузы на номер вызова серии: одновременные повторы не идут шеренгой. */
    private static final Duration STAGGER = Duration.ofMillis(50);

    /** Бюджет повторов одного вызова — таймаут кейса. */
    static final Duration BUDGET = Duration.ofSeconds(90);

    private static final Object LOCK = new Object();

    private static final AtomicInteger LIMITED = new AtomicInteger();

    private static Instant lastCall = Instant.EPOCH;

    private Funnel() {
    }

    /** Вызов с паузой-троттлом и backoff. */
    static <T> T paced(Supplier<T> call, Predicate<T> limited) {
        return withBackoff(() -> {
            pace();
            return call.get();
        }, limited, 0);
    }

    /**
     * Вызов с backoff, но без троттла: так идёт серия, которой лимит и нужно
     * достать.
     *
     * @param call    вызов
     * @param limited признак ответа о превышении лимита
     * @param stagger номер вызова в серии: сдвигает паузу
     */
    static <T> T withBackoff(Supplier<T> call, Predicate<T> limited, Integer stagger) {
        Instant deadline = Instant.now().plus(BUDGET);
        Duration backoff = FIRST_BACKOFF;
        T answer = call.get();
        while (limited.test(answer)) {
            LIMITED.incrementAndGet();
            Duration pause = backoff.plus(STAGGER.multipliedBy(stagger));
            if (Instant.now().plus(pause).isAfter(deadline)) {
                throw new AssertionError("Ответ о превышении лимита держится дольше бюджета кейса " + BUDGET);
            }
            sleep(pause);
            backoff = backoff.multipliedBy(2).compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : backoff.multipliedBy(2);
            answer = call.get();
        }
        return answer;
    }

    /** Сколько ответов о лимите набор встретил с начала прогона. */
    static Integer limitedAnswers() {
        return LIMITED.get();
    }

    private static void pace() {
        synchronized (LOCK) {
            Duration wait = Duration.between(Instant.now(), lastCall.plus(PACE));
            if (wait.isPositive()) {
                sleep(wait);
            }
            lastCall = Instant.now();
        }
    }

    private static void sleep(Duration pause) {
        try {
            Thread.sleep(pause);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Пауза воронки прервана", interrupted);
        }
    }
}
