package com.example.tests.e2e.perimeterread;

import com.example.tests.e2e.Json;
import com.example.tests.e2e.Party;
import com.example.tests.e2e.Trail;
import com.example.tests.e2e.Trail.Answer;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static java.util.Objects.nonNull;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Кейс {@code E5.5} группы {@code E5} тропы периметра: потолок подписок
 * считается по тенанту (.claude/tests/cases/e2e-perimeter-read.md §«E5.5 —
 * Потолок подписок считается по тенанту, а не по субъекту и не по
 * процессу»). Прочие клетки группы читают строки пролога — класс
 * {@code TenantRadiusPathTest}.
 *
 * <p><b>Браузера играет прогон</b>: подписка открыта, пока открыт поток
 * ответа, и закрывается закрытием этого потока. Открытие ждётся до заголовков
 * ответа — тело потока не кончается никогда.
 *
 * <p><b>Красна по построению</b> тем же долгом, что клетка потолка у ящика
 * периметра: отказ точки подписки не рендерится — точка объявляет провод
 * потока, и единый формат отказа не принимается (.claude/tests/cases/bff.md,
 * находка {@code F-12}).
 */
@Tag("e2e")
@DisplayName("E5 — Радиус тенанта на обеих тропах")
class SubscriptionCeilingPathTest {

    private static final String CEILING_KEY = "perimeter.stream.max-subscriptions-per-tenant";

    private static final String PULSE_KEY = "perimeter.stream.pulse-interval";

    private static final Duration OPEN_LIMIT = Duration.ofSeconds(20);

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private static Trail trail;

    @BeforeAll
    static void openTrail() {
        trail = Trail.openPerimeter("p5");
    }

    @AfterAll
    static void closeTrail() {
        if (nonNull(trail)) {
            trail.close();
        }
    }

    @Test
    @Tag("debt")
    @DisplayName("E5.5 — Потолок подписок считается по тенанту, а не по субъекту и не по процессу")
    void e5_5_theSubscriptionCeilingIsCountedPerTenant() {
        trail.stop(Party.BFF);
        trail.side(Party.BFF).set(CEILING_KEY, "2");
        trail.side(Party.BFF).set(PULSE_KEY, "1s");
        trail.start(Party.BFF);
        String first = trail.identity().browserToken("subject-s1", "Trader One");
        String second = trail.identity().browserToken("subject-s2", "Trader Two");
        trail.callWith(first, Party.BFF, "GET", "/api/v1/bff/context", null, null);
        trail.callWith(second, Party.BFF, "GET", "/api/v1/bff/context", null, null);
        String firstTicket = ticketOf(first);
        trail.forgetTraces();

        HttpResponse<InputStream> a = open(firstTicket);
        HttpResponse<InputStream> b = open(firstTicket);
        HttpResponse<InputStream> beyond = open(firstTicket);
        String refusal = bodyOf(beyond);
        HttpResponse<InputStream> otherTenant = open(ticketOf(second));

        assertThat(a.statusCode()).as("E5.5: первая подписка тенанта открыта").isEqualTo(200);
        assertThat(b.statusCode()).as("E5.5: вторая подписка тенанта открыта").isEqualTo(200);
        assertThat(otherTenant.statusCode()).as("E5.5: у второго тенанта потолок свой").isEqualTo(200);
        close(a);
        Awaitility.await("E5.5: закрытие одной подписки освобождает место — не раньше ближайшего пульса")
                .atMost(OPEN_LIMIT)
                .pollInterval(Duration.ofSeconds(1))
                .until(() -> {
                    HttpResponse<InputStream> again = open(firstTicket);
                    Boolean opened = again.statusCode() == 200;
                    close(again);
                    return opened;
                });
        for (Party owner : List.of(Party.TRADING_CORE, Party.STRATEGIES, Party.AUDIT, Party.STATISTICS)) {
            assertThat(trail.accesses(owner)).as("E5.5: потолок — предмет периметра, к " + owner.module()
                    + " обращений нет").isEmpty();
        }
        close(b);
        close(otherTenant);
        assertThat(beyond.statusCode()).as("E5.5: сверх потолка — отказ «слишком много запросов»: " + refusal)
                .isEqualTo(429);
        assertThat(Json.object(refusal)).as("E5.5: отказ единым форматом").containsKeys("code", "occurredAt");
    }

    private static String ticketOf(String token) {
        Answer answer = trail.callWith(token, Party.BFF, "POST", "/api/v1/bff/stream-tickets", null, "");
        assertThat(answer.status()).as("предусловие — билет выдан: " + answer.body()).isEqualTo(200);
        return String.valueOf(Json.object(answer.body()).get("ticket"));
    }

    /** Открывает подписку и ждёт заголовков ответа: тело потока не кончается. */
    private static HttpResponse<InputStream> open(String ticket) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(trail.side(Party.BFF).baseUrl() + "/api/v1/bff/stream?ticket="
                        + URLEncoder.encode(ticket, StandardCharsets.UTF_8)))
                .header("Accept", "text/event-stream")
                .GET()
                .build();
        try {
            return CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
                    .get(OPEN_LIMIT.toSeconds(), TimeUnit.SECONDS);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Открытие подписки прервано", failure);
        } catch (ExecutionException | TimeoutException failure) {
            throw new IllegalStateException("Подписка не ответила заголовками", failure);
        }
    }

    private static String bodyOf(HttpResponse<InputStream> response) {
        if (response.statusCode() == 200) {
            close(response);
            return "";
        }
        try (InputStream body = response.body()) {
            return new String(body.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static void close(HttpResponse<InputStream> response) {
        try {
            response.body().close();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
