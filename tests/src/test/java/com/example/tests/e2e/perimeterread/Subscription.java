package com.example.tests.e2e.perimeterread;

import com.example.tests.e2e.Json;
import com.example.tests.e2e.Party;
import com.example.tests.e2e.Trail;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.awaitility.Awaitility;
import tools.jackson.databind.JsonNode;

import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;

/**
 * Подписка браузера на поток периметра — прогон играет браузер
 * (.claude/tests/cases/e2e-perimeter-read.md §«Новая ось формы — ТРОПА НИЧЕГО
 * НЕ МЕНЯЕТ, А ВТОРОЙ ЕЁ КОНЕЦ НЕ СЕРВИС»): держит соединение, копит записи
 * в порядке прихода и рвёт соединение сам.
 *
 * <p><b>Ответ открытия приходит вместе с первой записью</b> — пока периметр в
 * поток ничего не написал, заголовков на сокете нет (находка {@code F-11}
 * ящика периметра). Поэтому открытие асинхронно, и всякий вопрос об ответе
 * ждёт его ограниченным потолком.
 *
 * <p><b>Чем подписка кончилась — часть ожидания:</b> открыта, закрыта
 * клиентом ({@link #close()}) либо сервером.
 */
final class Subscription implements AutoCloseable {

    static final String PULSE = "PERIMETER_PULSE";

    static final String GAP = "PERIMETER_GAP";

    private static final String STREAM = "/api/v1/bff/stream?ticket=";

    private static final String LAST_EVENT_ID = "Last-Event-ID";

    private static final Duration WAIT = Duration.ofSeconds(60);

    private final HttpClient client;
    private final CompletableFuture<HttpResponse<InputStream>> pending;
    private final List<Frame> frames = Collections.synchronizedList(new ArrayList<>());
    private final AtomicBoolean reading = new AtomicBoolean(Boolean.FALSE);
    private final AtomicBoolean answered = new AtomicBoolean(Boolean.FALSE);
    private Integer status;
    private String contentType;
    private InputStream body;

    private Subscription(HttpClient client, CompletableFuture<HttpResponse<InputStream>> pending) {
        this.client = client;
        this.pending = pending;
    }

    /**
     * Открывает подписку билетом — первое подключение, без идентичности
     * последней записи.
     *
     * @param trail  тропа периметра
     * @param ticket билет подписки
     */
    static Subscription open(Trail trail, String ticket) {
        return open(trail, ticket, null);
    }

    /**
     * Открывает подписку билетом, передав идентичность последней полученной
     * записи заголовком — пересоздание подписки.
     *
     * @param trail       тропа периметра
     * @param ticket      билет подписки
     * @param lastEventId идентичность последней полученной записи; пусто — первое подключение
     */
    static Subscription open(Trail trail, String ticket, String lastEventId) {
        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create(trail.side(Party.BFF).baseUrl() + STREAM
                        + URLEncoder.encode(ticket, StandardCharsets.UTF_8)))
                .header("Accept", "text/event-stream");
        if (nonNull(lastEventId)) {
            request.header(LAST_EVENT_ID, lastEventId);
        }
        return new Subscription(client, client.sendAsync(request.GET().build(), HttpResponse.BodyHandlers.ofInputStream()));
    }

    /** Код ответа открытия; ждёт ответа потолком. */
    Integer status() {
        answer();
        return status;
    }

    /** Провод ли это: тип содержимого — поток событий. */
    Boolean carriesStream() {
        answer();
        return nonNull(contentType) && contentType.startsWith("text/event-stream");
    }

    /** Открыта ли подписка: чтение провода не кончилось. */
    Boolean isOpen() {
        answer();
        return reading.get();
    }

    /** Записи, пришедшие в провод, в порядке прихода. */
    List<Frame> frames() {
        synchronized (frames) {
            return List.copyOf(frames);
        }
    }

    /** Записи фактов — всё, кроме записей периметра, в порядке прихода. */
    List<Frame> facts() {
        return frames().stream().filter(frame -> isFalse(frame.isPerimeter())).toList();
    }

    /** Ждёт записи названного класса и отдаёт первую такую. */
    Frame awaitType(String type) {
        answer();
        Awaitility.await("запись класса " + type)
                .atMost(WAIT)
                .pollInterval(Duration.ofMillis(100))
                .until(() -> frames().stream().anyMatch(frame -> Objects.equals(type, frame.type())));
        return frames().stream().filter(frame -> Objects.equals(type, frame.type())).findFirst().orElseThrow();
    }

    /** Ждёт записи названной идентичности. */
    Frame awaitId(String id) {
        answer();
        Awaitility.await("запись идентичности " + id)
                .atMost(WAIT)
                .pollInterval(Duration.ofMillis(100))
                .until(() -> frames().stream().anyMatch(frame -> Objects.equals(id, frame.id())));
        return frames().stream().filter(frame -> Objects.equals(id, frame.id())).findFirst().orElseThrow();
    }

    /** Закрывает подписку со стороны клиента — обрыв, как у ушедшего браузера. */
    @Override
    public void close() {
        if (isFalse(answered.get())) {
            pending.cancel(Boolean.TRUE);
            client.shutdownNow();
            return;
        }
        try {
            body.close();
        } catch (IOException alreadyClosed) {
            // Закрытое соединение закрывать повторно нечем: исход тот же.
        }
        client.shutdownNow();
    }

    private synchronized void answer() {
        if (answered.get()) {
            return;
        }
        HttpResponse<InputStream> response;
        try {
            response = pending.get(WAIT.toSeconds(), TimeUnit.SECONDS);
        } catch (TimeoutException notAnswered) {
            throw new AssertionError("Точка подписки не ответила за " + WAIT
                    + ": заголовки провода уходят с первой записью, а записи не было", notAnswered);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Ожидание ответа точки подписки прервано", interrupted);
        } catch (ExecutionException failure) {
            throw new IllegalStateException("Точка подписки отказала", failure);
        }
        answered.set(Boolean.TRUE);
        status = response.statusCode();
        contentType = response.headers().firstValue("Content-Type").orElse(null);
        body = response.body();
        if (isFalse(carriesStream())) {
            return;
        }
        reading.set(Boolean.TRUE);
        Thread.ofVirtual().name("e2e-subscription").start(this::read);
    }

    private void read() {
        try (BufferedReader lines = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
            String id = null;
            String type = null;
            StringBuilder data = new StringBuilder();
            String line = lines.readLine();
            while (nonNull(line)) {
                if (line.isEmpty()) {
                    if (nonNull(type)) {
                        frames.add(new Frame(id, type, data.toString(), Instant.now()));
                    }
                    id = null;
                    type = null;
                    data.setLength(0);
                } else if (line.startsWith("id:")) {
                    id = line.substring("id:".length());
                } else if (line.startsWith("event:")) {
                    type = line.substring("event:".length());
                } else if (line.startsWith("data:")) {
                    data.append(line.substring("data:".length()));
                }
                line = lines.readLine();
            }
        } catch (IOException closed) {
            // Обрыв чтения есть штатный конец подписки — закрытой нами либо сервером.
        } finally {
            reading.set(Boolean.FALSE);
        }
    }

    /**
     * Запись, пришедшая в провод.
     *
     * @param id      поле идентичности записи; пусто — поля не было
     * @param type    класс записи
     * @param data    содержимое поля данных дословно — запись в форме периметра
     * @param arrived момент, когда прогон дочитал запись
     */
    record Frame(String id, String type, String data, Instant arrived) {

        /** Запись в форме периметра целиком. */
        JsonNode record() {
            return Json.tree(data);
        }

        /** Содержимое записи. */
        JsonNode content() {
            return record().path("content");
        }

        /** Запись порождена периметром, а не фактом. */
        Boolean isPerimeter() {
            return Objects.equals(PULSE, type) || Objects.equals(GAP, type);
        }
    }
}
