package com.example.connector.okx.box;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import com.example.connector.okx.util.OkxConstants;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.boot.json.JsonParserFactory;

/**
 * Живая площадка OKX СНАРУЖИ ящика: свой клиент набора, а не клиент
 * коннектора.
 *
 * <p><b>Зачем у ящика второй канал к площадке.</b> Коннектор — предмет, и
 * его чтение есть мнение проверяемой системы о площадке. Проверка конца,
 * цена неисполнимой заявки и сырые ответы для стаба берутся у самой площадки
 * (.claude/tests/case-material/connector-okx.md §«2. Инвариант
 * восстановления состояния stateful-кейса», §«3. Наблюдение → находка →
 * правка апидоков»).
 *
 * <p><b>Подпись тест считает сам по форме дома</b> ({@link Signatures};
 * {@code docs/integrations/okx/rules/request-signing.md}). Заголовок
 * {@code User-Agent} ставится явно: без него пограничный фильтр площадки
 * отвечает телом не в JSON (.claude/skills/local-stand.md §«Тестовые данные
 * стенда»).
 *
 * <p><b>Контур — заголовком на каждом запросе к счёту.</b> Ключи — demo, и
 * приватный запрос без заголовка демо-контура площадка отвергает: этим
 * отказом и стои́т контроль {@code B11.1-D}. Публичное чтение уходит в двух
 * формах: без заголовка — как его шлёт публичный клиент коннектора (рыночные
 * данные production), с заголовком — цены того контура, где заявка встанет.
 */
final class DemoExchange {

    /** Базовый адрес живой площадки: тот же, что у стенда. */
    static final String BASE_URL = "https://www.okx.com";

    /** Имя клиента набора в заголовке {@code User-Agent}. */
    private static final String USER_AGENT = "vibetrading-connector-okx-demo-box";

    /** Код ответа площадки «превышен лимит частоты». */
    static final String RATE_LIMIT_CODE = "50011";

    /** HTTP-код ответа о превышении лимита. */
    private static final Integer TOO_MANY_REQUESTS = 429;

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private DemoExchange() {
    }

    /** Публичное чтение без заголовка контура — как у публичного клиента коннектора. */
    static Raw publicGet(String pathWithQuery) {
        return send(() -> base(pathWithQuery).GET());
    }

    /** Публичное чтение demo-контура: цены там, где встанет заявка. */
    static Raw demoPublicGet(String pathWithQuery) {
        return send(() -> base(pathWithQuery)
                .header(OkxConstants.SIMULATED_HEADER, OkxConstants.SIMULATED_ON)
                .GET());
    }

    /** Подписанное чтение счёта в demo-контуре. */
    static Raw signedGet(DemoKeys keys, String pathWithQuery) {
        return send(() -> signed(keys, "GET", pathWithQuery, "")
                .header(OkxConstants.SIMULATED_HEADER, OkxConstants.SIMULATED_ON)
                .GET());
    }

    /** Подписанное чтение БЕЗ заголовка контура: контроль, что площадка его различает. */
    static Raw signedGetWithoutContour(DemoKeys keys, String pathWithQuery) {
        return send(() -> signed(keys, "GET", pathWithQuery, "").GET());
    }

    /** Подписанная команда счёта в demo-контуре: только принудительная зачистка. */
    static Raw signedPost(DemoKeys keys, String path, String body) {
        return send(() -> signed(keys, "POST", path, body)
                .header(OkxConstants.SIMULATED_HEADER, OkxConstants.SIMULATED_ON)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)));
    }

    private static HttpRequest.Builder base(String pathWithQuery) {
        return HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + pathWithQuery))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json");
    }

    /**
     * Запрос с подписью. Метка времени ставится на каждую попытку: повтор
     * после backoff со старой меткой площадка отвергла бы как просроченный.
     */
    private static HttpRequest.Builder signed(DemoKeys keys, String method, String pathWithQuery, String body) {
        String timestamp = TIMESTAMP.format(Instant.now());
        return base(pathWithQuery)
                .header(OkxConstants.ACCESS_KEY_HEADER, keys.apiKey())
                .header(OkxConstants.ACCESS_SIGN_HEADER,
                        Signatures.expected(keys.secret(), timestamp, method, pathWithQuery, body))
                .header(OkxConstants.ACCESS_TIMESTAMP_HEADER, timestamp)
                .header(OkxConstants.ACCESS_PASSPHRASE_HEADER, keys.passphrase());
    }

    private static Raw send(Supplier<HttpRequest.Builder> request) {
        return Funnel.paced(() -> exchange(request.get().build()), Raw::rateLimited);
    }

    private static Raw exchange(HttpRequest request) {
        try {
            HttpResponse<String> answer = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            return new Raw(request.uri().getRawPath(), answer.statusCode(), answer.body());
        } catch (IOException failure) {
            throw new IllegalStateException("Площадка не ответила: " + request.uri().getRawPath(), failure);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Ожидание ответа площадки прервано", failure);
        }
    }

    /**
     * Ответ площадки дословно.
     *
     * @param path   путь запроса — для сообщений отказа
     * @param status HTTP-код
     * @param body   тело дословно
     */
    record Raw(String path, Integer status, String body) {

        /** Конверт ответа; тело не объект — отказ с телом в сообщении. */
        Map<String, Object> envelope() {
            try {
                return JsonParserFactory.getJsonParser().parseMap(body);
            } catch (RuntimeException notJson) {
                throw new IllegalStateException("Ответ площадки на " + path + " не JSON: " + status + " " + body);
            }
        }

        /** Код конверта. */
        String code() {
            return String.valueOf(envelope().get("code"));
        }

        /** Записи конверта. */
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data() {
            Object data = envelope().get("data");
            return isNull(data) ? List.of() : (List<Map<String, Object>>) data;
        }

        /** Успех конверта, иначе отказ: читатель без успеха не меряет ничего. */
        Raw required() {
            if (Objects.equals(OkxConstants.SUCCESS_CODE, code())) {
                return this;
            }
            throw new IllegalStateException("Площадка отказала на " + path + ": " + status + " " + body);
        }

        /** Ответ о превышении лимита: HTTP 429 либо код конверта. */
        Boolean rateLimited() {
            if (Objects.equals(TOO_MANY_REQUESTS, status)) {
                return Boolean.TRUE;
            }
            return nonNull(body) && body.contains("\"code\":\"" + RATE_LIMIT_CODE + "\"");
        }
    }
}
