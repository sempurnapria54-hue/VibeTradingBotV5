package com.example.marketdata.box;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformerV2;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Ответ стаба, УДЕРЖАННЫЙ до хода кейса: запрос дошёл до площадки, а ответ
 * уходит только тогда, когда кейс его отпустит.
 *
 * <p><b>Им подаётся вход «посреди шага».</b> Шаг цикла загрузки читает
 * площадку между загрузкой группы и записью итога; удержанный ответ ставит
 * ход кейса ровно в это окно — детерминированно, а не гонкой с задержкой,
 * исход которой зависит от того, кто успел раньше.
 *
 * <p><b>Удержание ограничено.</b> Кейс, упавший до отпускания, не вешает
 * прогон: по истечении предела ответ уходит сам.
 *
 * <p><b>Состояние общее на JVM, как и расширение.</b> Каждое удержание
 * взводится заново ({@link #arm()}), и отпускание прежнего на новое не
 * действует.
 */
final class HeldAnswer implements ResponseDefinitionTransformerV2 {

    /** Имя, которым заготовка называет это расширение. */
    static final String NAME = "held-answer";

    /** Параметр заготовки: тело ответа, отдаваемого после отпускания. */
    static final String BODY = "body";

    /** Предел удержания: кейс, не отпустивший ответ, прогон не вешает. */
    private static final long HOLD_LIMIT_SECONDS = 30L;

    private static volatile CountDownLatch arrived = new CountDownLatch(1);

    private static volatile CountDownLatch released = new CountDownLatch(1);

    /** Взводит новое удержание: запрос ещё не пришёл, ответ не отпущен. */
    static void arm() {
        arrived = new CountDownLatch(1);
        released = new CountDownLatch(1);
    }

    /**
     * Ждёт, пока удержанный запрос дойдёт до стаба.
     *
     * @param timeoutSeconds предел ожидания
     * @return дошёл ли запрос в пределах ожидания
     */
    static Boolean awaitArrival(Long timeoutSeconds) {
        try {
            return arrived.await(timeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            return Boolean.FALSE;
        }
    }

    /** Отпускает удержанный ответ. */
    static void release() {
        released.countDown();
    }

    @Override
    public String getName() {
        return NAME;
    }

    /** Только заготовки, назвавшие расширение: прочие ответы стаба им не трогаются. */
    @Override
    public boolean applyGlobally() {
        return false;
    }

    @Override
    public ResponseDefinition transform(ServeEvent serveEvent) {
        CountDownLatch hold = released;
        arrived.countDown();
        try {
            hold.await(HOLD_LIMIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
        }
        return ResponseDefinitionBuilder.responseDefinition()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(serveEvent.getTransformerParameters().getString(BODY))
                .build();
    }
}
