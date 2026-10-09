package com.example.tests.e2e.smokelive;

import com.example.tests.e2e.Json;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.cert.Certificate;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import tools.jackson.databind.JsonNode;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

/**
 * Вход дыма — поверхность, доступная снаружи окружения: имя хоста,
 * маршрут {@code /api/v1} ингресса окружения, периметр
 * (.claude/tests/cases/smoke-live.md §«Чем дым действует и что читает»).
 *
 * <p><b>Другого хода в окружение у прогона нет:</b> ни прокидывания порта, ни
 * базы, ни брокера. Заголовок контекста тенанта прогон не ставит — его
 * проставляет периметр по токену (.claude/tests/cases/smoke-live.md, кейс
 * {@code E6.1}).
 */
final class Perimeter {

    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(60);

    private final HttpClient client = Stand.ingressClient();

    private final HolderToken holder;

    Perimeter(HolderToken holder) {
        this.holder = holder;
    }

    /** {@code GET} под токеном держателя. */
    Reply get(String path) {
        return call("GET", path, holder.current(), null);
    }

    /** {@code POST} под токеном держателя; {@code body} — JSON либо {@code null}. */
    Reply post(String path, String body) {
        return call("POST", path, holder.current(), body);
    }

    /** {@code PUT} под токеном держателя. */
    Reply put(String path, String body) {
        return call("PUT", path, holder.current(), body);
    }

    /**
     * Вызов названным предъявлением.
     *
     * @param method метод
     * @param path   путь от корня хоста, с запросом
     * @param token  токен; {@code null} — без предъявления
     * @param body   тело JSON либо {@code null}
     * @return ответ окружения
     */
    Reply call(String method, String path, String token, String body) {
        return call(method, path, token, body, Map.of());
    }

    /**
     * {@code GET} под токеном держателя с заголовком сверх предъявления — так
     * клиент пытается сам назвать тенанта, и периметр обязан заголовок
     * перезаписать (.claude/tests/cases/smoke-live.md, кейс {@code E6.1}).
     */
    Reply getWithHeader(String path, String header, String value) {
        return call("GET", path, holder.current(), null, Map.of(header, value));
    }

    private Reply call(String method, String path, String token, String body, Map<String, String> headers) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(Stand.origin() + path))
                .timeout(CALL_TIMEOUT)
                .method(method, isNull(body)
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body));
        if (nonNull(body)) {
            request.header("Content-Type", "application/json");
        }
        if (nonNull(token)) {
            request.header("Authorization", "Bearer " + token);
        }
        headers.forEach(request::header);
        try {
            HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
            return new Reply(response.statusCode(), response.body(), response.sslSession(),
                    response.headers().firstValue("Content-Type").orElse(""));
        } catch (IOException failure) {
            throw new IllegalStateException("Вход снаружи не ответил: " + method + " " + path
                    + " — таймаут либо отказ соединения, а не ответ окружения", failure);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Вызов прерван: " + method + " " + path, failure);
        }
    }

    /**
     * Ответ окружения.
     *
     * @param status      код ответа
     * @param body        тело
     * @param tls         сессия TLS соединения
     * @param contentType тип тела
     */
    record Reply(int status, String body, Optional<SSLSession> tls, String contentType) {

        /** Тело деревом JSON; пустое тело — пустой объект. */
        JsonNode json() {
            return body.isBlank() ? Json.tree("{}") : Json.tree(body);
        }

        /** Код единого формата отказа ({@code code}) либо пустая строка. */
        String errorCode() {
            return contentType.contains("json") ? json().path("code").asString("") : "";
        }

        /** Сертификаты сервера, предъявленные на соединении. */
        List<Certificate> serverCertificates() {
            return tls.map(session -> {
                try {
                    return List.of(session.getPeerCertificates());
                } catch (SSLPeerUnverifiedException failure) {
                    return List.<Certificate>of();
                }
            }).orElse(List.of());
        }

        @Override
        public String toString() {
            return status + " " + (body.length() > 400 ? body.substring(0, 400) + "…" : body);
        }
    }
}
