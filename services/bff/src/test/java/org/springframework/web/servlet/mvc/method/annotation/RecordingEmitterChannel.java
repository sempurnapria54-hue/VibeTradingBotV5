package org.springframework.web.servlet.mvc.method.annotation;

import static java.util.Objects.nonNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import org.springframework.http.MediaType;

/**
 * Контейнер, подменённый тестом: единственная точка, из которой видно,
 * что подписка получила.
 *
 * <p><b>Почему подменяется контейнер, а не сам {@code SseEmitter}.</b>
 * Реестр подписок создаёт эмиттер САМ ({@code StreamRegistry#open}), и
 * двойник эмиттера в него не передаётся ни одним аргументом — подменять
 * там нечего. Настоящий же эмиттер вне контейнера ничего не предъявляет:
 * до {@code initialize(handler)} записи копятся в его собственной
 * очереди, а зарегистрированные {@code onCompletion} / {@code onTimeout}
 * / {@code onError} зовёт контейнер, а не он. Отсюда форма: эмиттер —
 * настоящий, подменён его СОБЕСЕДНИК, и подменён он ровно тем
 * интерфейсом, которым с ним говорит каркас.
 *
 * <p><b>Класс лежит в пакете каркаса, и это не удобство:</b>
 * {@code ResponseBodyEmitter.Handler} и {@code initialize} пакетно
 * видимы, то есть реализовать первый и позвать второй можно только
 * отсюда. Другого входа у наблюдения нет.
 *
 * <p><b>Записи, отданные ДО подключения, не теряются:</b> каркас
 * выталкивает свою очередь в обработчик тем же {@code initialize}.
 * Поэтому переигрывание, идущее внутри {@code open}, наблюдается
 * подключением после него — но приходит одной пачкой, а не пофреймово:
 * границы фреймов восстанавливает читатель ({@code SseFrame}).
 */
public class RecordingEmitterChannel implements ResponseBodyEmitter.Handler {

    /** Всё, что ушло в провод, в порядке отдачи. */
    private final List<ResponseBodyEmitter.DataWithMediaType> written = new ArrayList<>();

    /** Сколько раз каркас просили записать — включая отказавшие попытки. */
    private Integer attempts = 0;

    /** Отказ доставки по требованию кейса; пусто — доставка проходит. */
    private IOException failure;

    private Runnable timeoutCallback;
    private Consumer<Throwable> errorCallback;
    private Runnable completionCallback;

    private Boolean completed = Boolean.FALSE;
    private Throwable completedWith;

    /**
     * Подключить наблюдателя к эмиттеру.
     *
     * @param emitter эмиттер, выданный реестром
     * @return наблюдатель, в который каркас будет писать
     */
    public static RecordingEmitterChannel attachedTo(SseEmitter emitter) {
        RecordingEmitterChannel channel = new RecordingEmitterChannel();
        try {
            emitter.initialize(channel);
        } catch (IOException failure) {
            throw new IllegalStateException("Наблюдатель не подключился к эмиттеру", failure);
        }
        return channel;
    }

    /** Отказывать на каждой следующей записи — вход группы `U9`. */
    public void failWith(IOException failure) {
        this.failure = failure;
    }

    /** Всё, что ушло в провод, в порядке отдачи. */
    public List<ResponseBodyEmitter.DataWithMediaType> written() {
        return written;
    }

    /** Число попыток записи — включая отказавшие. */
    public Integer attempts() {
        return attempts;
    }

    /** Завершена ли подписка каркасом либо самим реестром. */
    public Boolean completed() {
        return completed;
    }

    /** Чем завершена подписка; пусто — завершения с ошибкой не было. */
    public Throwable completedWith() {
        return completedWith;
    }

    /** Позвать обратный вызов завершения — то, что делает контейнер. */
    public void fireCompletion() {
        completionCallback.run();
    }

    /** Позвать обратный вызов истечения срока соединения. */
    public void fireTimeout() {
        timeoutCallback.run();
    }

    /** Позвать обратный вызов ошибки. */
    public void fireError(Throwable failure) {
        errorCallback.accept(failure);
    }

    @Override
    public void send(Object data, MediaType mediaType) throws IOException {
        send(Set.of(new ResponseBodyEmitter.DataWithMediaType(data, mediaType)));
    }

    @Override
    public void send(Set<ResponseBodyEmitter.DataWithMediaType> items) throws IOException {
        attempts++;
        if (nonNull(failure)) {
            throw failure;
        }
        written.addAll(items);
    }

    @Override
    public void complete() {
        completed = Boolean.TRUE;
    }

    @Override
    public void completeWithError(Throwable failure) {
        completed = Boolean.TRUE;
        completedWith = failure;
    }

    @Override
    public void onTimeout(Runnable callback) {
        this.timeoutCallback = callback;
    }

    @Override
    public void onError(Consumer<Throwable> callback) {
        this.errorCallback = callback;
    }

    @Override
    public void onCompletion(Runnable callback) {
        this.completionCallback = callback;
    }
}
