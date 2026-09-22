package com.example.bff.box;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.commons.lang3.BooleanUtils;
import org.awaitility.Awaitility;
import org.springframework.boot.json.JsonParserFactory;

/**
 * Живая подписка на поток — ВЫХОД, которого у семи предшественников не
 * было: не ответ и не строка, а провод, который держится открытым
 * (.claude/tests/cases/bff.md §«Новая ось формы — ПОТОК В БРАУЗЕР»).
 *
 * <p><b>Соединение настоящее, и это не педантизм.</b> Наблюдаемое здесь
 * имеет две собственные оси сверх содержимого — ПОРЯДОК записей и
 * МОМЕНТ (запись, пришедшая после закрытия подписки, не приходит
 * никуда), — и ни та, ни другая не выражается ответом, собранным
 * целиком. Поэтому тело читается потоком байтов, а записи копятся по
 * мере прихода.
 *
 * <p><b>Чем подписка кончилась — часть каждого ожидания.</b> Отсюда три
 * наблюдаемых состояния: открыта ({@link #isOpen()}), закрыта сервером
 * ({@link #awaitClosed}) и закрыта нами ({@link #close()}). Клетка,
 * называющая только «что уехало», оставляла бы второе и третье
 * неразличимыми.
 *
 * <p><b>Отказ открытия — тоже исход подписки, а не её отсутствие.</b>
 * Негодный билет отвечает обычным документом отказа, и клетка обязана
 * утверждать, что тип содержимого НЕ {@code text/event-stream}: иначе
 * «поток не открылся» держалось бы кодом ответа, а код отказа билета
 * этот предмет не мерит.
 *
 * <p><b>ОТВЕТ ОТКРЫТИЯ ПРИХОДИТ НЕ СРАЗУ, и это наблюдённое свойство
 * построенного, а не свойство оснастки.</b> Заголовки ответа провода
 * уходят клиенту вместе с ПЕРВОЙ записью: пока периметр в поток ничего
 * не написал, на сокете нет ни строки — предъявлено сырым соединением
 * (находка F-11 документа кейсов). Отсюда форма: открытие идёт
 * асинхронно, а всякий вопрос об ответе — код, тип содержимого,
 * записи — ждёт его ограниченным потолком. Клетка, утверждающая, что
 * провод открылся, обязана поэтому предъявить ДОЕХАВШУЮ запись:
 * открытый сокет сам по себе о раздаче не говорит ничего.
 */
final class Subscription implements AutoCloseable {

    /** Потолок ожидания записи в проводе: «не упало» проверкой не является. */
    private static final Duration WAIT = Duration.ofSeconds(30);

    private final HttpClient client;
    private final CompletableFuture<HttpResponse<InputStream>> pending;
    private final List<Frame> frames = Collections.synchronizedList(new ArrayList<>());
    private final AtomicBoolean reading = new AtomicBoolean(Boolean.FALSE);
    private final AtomicBoolean answered = new AtomicBoolean(Boolean.FALSE);

    private Integer status;
    private String contentType;
    private String errorBody;
    private InputStream body;

    private Subscription(HttpClient client, CompletableFuture<HttpResponse<InputStream>> pending) {
        this.client = client;
        this.pending = pending;
    }

    /**
     * Открывает подписку по ОТПРАВЛЕННОМУ запросу, ответа не дожидаясь.
     *
     * @param client  клиент ЭТОЙ подписки; закрывается вместе с ней
     * @param pending ответ точки подписки, который ещё не пришёл
     */
    static Subscription of(HttpClient client, CompletableFuture<HttpResponse<InputStream>> pending) {
        return new Subscription(client, pending);
    }

    /** Код ответа на открытие; ждёт ответа потолком. */
    Integer status() {
        answer();
        return status;
    }

    /** Тип содержимого ответа: им читается, открылся ли провод вообще. */
    String contentType() {
        answer();
        return contentType;
    }

    /** Провод ли это: тип содержимого — поток событий. */
    Boolean carriesStream() {
        answer();
        return Objects.nonNull(contentType) && contentType.startsWith("text/event-stream");
    }

    /** Тело отказа дословно; у открытого провода пусто. */
    String errorBody() {
        answer();
        return errorBody;
    }

    /**
     * Дожидается ответа открытия и запускает чтение провода.
     *
     * <p>Вызывается всяким вопросом об ответе; повторный вызов ничего не
     * делает. Потолок здесь не перестраховка: ответа у провода, в
     * который ничего не написано, нет вовсе (см. шапку класса), и
     * бесконечное ожидание висело бы ровно там, где раздача не
     * состоялась.
     */
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
        if (Objects.nonNull(contentType) && contentType.startsWith("text/event-stream")) {
            reading.set(Boolean.TRUE);
            Thread.ofVirtual().name("box-subscription").start(this::read);
            return;
        }
        errorBody = readWhole();
    }

    /** Класс отказа единого error-DTO: тело отказа открытия — обычный документ. */
    String errorCode() {
        return String.valueOf(errorObject().get("code"));
    }

    /** Пояснение отказа: им клетка о неразличимости сверяет тексты. */
    String errorMessage() {
        return String.valueOf(errorObject().get("message"));
    }

    /** Несёт ли тело отказа единый error-DTO: класс отказа и момент. */
    Boolean carriesErrorDto() {
        Map<String, Object> parsed = errorObject();
        return parsed.containsKey("code") && parsed.containsKey("occurredAt");
    }

    private Map<String, Object> errorObject() {
        if (Objects.isNull(errorBody) || errorBody.isBlank()) {
            return Map.of();
        }
        // Тело, которое объектом не является, формы не несёт — и это
        // ОТВЕТ клетки, а не её отказ: иначе красная клетка падала бы
        // разбором и переставала называть, чем ожидание не сошлось.
        try {
            return JsonParserFactory.getJsonParser().parseMap(errorBody);
        } catch (RuntimeException notAnObject) {
            return Map.of();
        }
    }

    /** Открыта ли подписка: чтение тела не кончилось. */
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

    /** Классы пришедших записей в порядке прихода. */
    List<String> types() {
        return frames().stream().map(Frame::type).toList();
    }

    /** Идентичности пришедших записей в порядке прихода; пустая — у записей периметра. */
    List<String> ids() {
        return frames().stream().map(Frame::id).toList();
    }

    /**
     * Ждёт, пока в проводе окажется названное число записей.
     *
     * <p>Ограниченным потолком: бесконечное ожидание висело бы ровно
     * там, где раздача не состоялась.
     *
     * @param count сколько записей обязано прийти
     */
    Subscription awaitFrames(Integer count) {
        answer();
        Awaitility.await("записей в проводе: " + count)
                .atMost(WAIT)
                .pollInterval(Duration.ofMillis(100))
                .until(() -> frames().size() >= count);
        return this;
    }

    /** Ждёт записи названного класса. */
    Subscription awaitType(String type) {
        answer();
        Awaitility.await("запись класса " + type)
                .atMost(WAIT)
                .pollInterval(Duration.ofMillis(100))
                .until(() -> types().contains(type));
        return this;
    }

    /** Ждёт, пока сервер закроет подписку сам. */
    Subscription awaitClosed(Duration atMost) {
        answer();
        Awaitility.await("подписка закрыта сервером")
                .atMost(atMost)
                .pollInterval(Duration.ofMillis(100))
                .until(() -> BooleanUtils.isFalse(reading.get()));
        return this;
    }

    /** Закрывает подписку со стороны клиента — обрыв, как у ушедшего браузера. */
    @Override
    public void close() {
        if (BooleanUtils.isFalse(answered.get())) {
            // Ответа не было: закрывать нечего, и ждать его незачем —
            // отменённый запрос и есть ушедший браузер.
            pending.cancel(Boolean.TRUE);
            client.shutdownNow();
            return;
        }
        try {
            body.close();
        } catch (IOException alreadyClosed) {
            // Закрытое соединение закрывать повторно нечем: исход тот же.
        }
        if (reading.get()) {
            Awaitility.await("чтение провода прекращено")
                    .atMost(WAIT)
                    .pollInterval(Duration.ofMillis(50))
                    .until(() -> BooleanUtils.isFalse(reading.get()));
        }
        // Клиент подписки закрывается вместе с ней: соединение, брошенное
        // посреди незаконченного ответа, в пул не возвращается и хвоста
        // своих байтов следующему запросу не отдаёт.
        client.shutdownNow();
    }

    private void read() {
        try (BufferedReader lines = new BufferedReader(
                new InputStreamReader(body, StandardCharsets.UTF_8))) {
            String id = null;
            String type = null;
            StringBuilder data = new StringBuilder();
            String line = lines.readLine();
            while (Objects.nonNull(line)) {
                if (line.isEmpty()) {
                    if (Objects.nonNull(type)) {
                        frames.add(new Frame(id, type, data.toString()));
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
            // Обрыв чтения есть штатный конец подписки — закрытой нами
            // либо сервером; различает их тот, кто закрывал.
        } finally {
            reading.set(Boolean.FALSE);
        }
    }

    private String readWhole() {
        try (InputStream content = body) {
            return new String(content.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new IllegalStateException("Тело отказа подписки не прочитано", failure);
        }
    }

    /**
     * Запись, пришедшая в провод.
     *
     * @param id   идентичность записи; пусто — поля не было (записи
     *             периметра идентичности не несут)
     * @param type класс записи
     * @param data содержимое дословно
     */
    record Frame(String id, String type, String data) {

        /** Содержимое как объект. */
        Map<String, Object> content() {
            return JsonParserFactory.getJsonParser().parseMap(data);
        }

        /** Несёт ли запись поле идентичности. */
        Boolean carriesId() {
            return Objects.nonNull(id);
        }
    }
}
