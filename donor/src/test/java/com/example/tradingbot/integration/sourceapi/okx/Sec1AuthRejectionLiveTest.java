package com.example.tradingbot.integration.sourceapi.okx;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.config.OkxProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * SEC1. Перечень auth-отказов источника — грунт S0 шага 9 фазы 1
 * (`.claude/work/backlog.md`, S0). Резолвер отказа кредов обязан отличать
 * «источник отверг наши креды» от прочих отказов по <b>фактическому</b> коду
 * источника, а не по правдоподобному; до сбора этого перечня он не пишется
 * (docs/rules/runtime-error-classification.md).
 *
 * <p><b>Почему кейс не идёт через {@code /api/proxy/okx/raw}, как остальные.</b>
 * Прокси подписывает вызов кредами поднятого app — то есть <b>верными</b>.
 * Предмет кейса ровно обратный: ответ источника на <b>испорченные</b> креды,
 * поэтому запросы собираются здесь напрямую, повторяя схему подписи границы
 * ({@link com.example.tradingbot.integration.service.okx.OkxSigningInterceptor}):
 * prehash = timestamp + method + requestPath + body, подпись =
 * base64(HMAC-SHA256(secret, prehash)).
 *
 * <p><b>Контроль обязателен.</b> Первым идёт вызов с ВЕРНЫМИ кредами: без него
 * перечень отказов не отличим от «контур сломан» — любой код был бы принят за
 * отказ прав. Контроль обязан вернуть {@code code=0}.
 *
 * <p>Эндпоинт пробы — {@code GET /api/v5/account/balance}: приватный,
 * read-only, состояния счёта не меняет, teardown'а не требует.
 */
@Order(90)
class Sec1AuthRejectionLiveTest extends OkxSourceApiLiveTestBase {

    private static final String BALANCE_PATH = "/api/v5/account/balance";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);
    /** Пауза между обращениями к источнику — тот же смысл, что у троттла базы. */
    private static final Duration PROBE_GAP = Duration.ofMillis(1200);
    /** Заведомо неизвестный источнику ключ — форма ключа OKX (uuid-подобная). */
    private static final String UNKNOWN_KEY = "00000000-0000-4000-8000-000000000000";

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    @Autowired
    private OkxProperties okx;

    private final List<String> observed = new ArrayList<>();

    @Test
    @Order(10)
    @DisplayName("SEC1.1 Перечень auth-отказов источника — контроль плюс четыре формы порчи кредов")
    void sec1_1_authRejectionDictionary() throws Exception {
        Probe control = probe("контроль — верные креды", okx.getApiKey(), okx.getSecret(),
                okx.getPassphrase(), true, true);

        assertThat(control.code)
                .as("контроль: на верных кредах источник обязан ответить code=0, иначе перечень "
                        + "отказов не отличим от сломанного контура — " + control)
                .isEqualTo("0");

        Probe unknownKey = probe("неизвестный ключ", UNKNOWN_KEY, okx.getSecret(),
                okx.getPassphrase(), true, true);
        Probe brokenSign = probe("испорченная подпись", okx.getApiKey(), okx.getSecret() + "x",
                okx.getPassphrase(), true, true);
        Probe wrongPassphrase = probe("неверная passphrase", okx.getApiKey(), okx.getSecret(),
                okx.getPassphrase() + "x", true, true);
        Probe malformedKey = probe("ключ неверной формы", "bogus-key-not-a-uuid", okx.getSecret(),
                okx.getPassphrase(), true, true);
        Probe wrongEnvironment = probe("ключ не того окружения", okx.getApiKey(), okx.getSecret(),
                okx.getPassphrase(), true, false);
        Probe missingKeyHeader = probe("заголовок ключа отсутствует", null, okx.getSecret(),
                okx.getPassphrase(), true, true);
        Probe staleTimestamp = probeStaleTimestamp();

        // Каждая форма порчи обязана дать НЕнулевой код: нулевой означал бы, что
        // источник порчу не заметил, и признак отказа на нём не строится.
        for (Probe rejection : List.of(unknownKey, brokenSign, wrongPassphrase, malformedKey,
                wrongEnvironment, missingKeyHeader, staleTimestamp)) {
            assertThat(rejection.code)
                    .as("форма порчи кредов обязана быть отвергнута источником — " + rejection)
                    .isNotEqualTo("0");
            assertThat(rejection.code)
                    .as("код отказа обязан быть непуст: пустой код признаком не служит — " + rejection)
                    .isNotBlank();
        }

        persistObservation("SEC1.1", "коды auth-отказа источника на " + BALANCE_PATH, observed);
    }

    /**
     * Один обращение к источнику с заданными кредами. {@code apiKey == null} —
     * заголовок ключа не ставится вовсе (форма «заголовок отсутствует»);
     * {@code simulated == false} — заголовок демо-контура снят, то есть
     * демо-ключ предъявляется боевому контуру.
     */
    private Probe probe(String shape, String apiKey, String secret, String passphrase,
                        boolean signed, boolean simulated) throws Exception {
        Thread.sleep(PROBE_GAP.toMillis());
        String timestamp = TIMESTAMP_FORMAT.format(Instant.now());
        String prehash = timestamp + "GET" + BALANCE_PATH;
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(okx.getBaseUrl() + BALANCE_PATH))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .GET();
        if (apiKey != null) {
            builder.header("OK-ACCESS-KEY", apiKey);
        }
        if (signed) {
            builder.header("OK-ACCESS-SIGN", sign(secret, prehash));
            builder.header("OK-ACCESS-TIMESTAMP", timestamp);
            builder.header("OK-ACCESS-PASSPHRASE", passphrase);
        }
        if (simulated) {
            builder.header("x-simulated-trading", "1");
        }

        HttpResponse<String> response = CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        JsonNode body = JSON.readTree(response.body());
        Probe result = new Probe(shape, response.statusCode(),
                body.path("code").asText(""), body.path("msg").asText(""));
        observed.add(result.toString());
        log.info("[SEC1.1] {}", result);
        return result;
    }

    /**
     * Отдельная форма: креды ВЕРНЫЕ, устарел только timestamp подписи. Нужна
     * границе класса — «отвергнуты креды» против «запрос собран неверно»:
     * второе лечится нашей стороной (часы, сборка запроса), первое повтором не
     * лечится вовсе. Без этой формы обе тропы неразличимы, потому что обе дают
     * HTTP 401 и код семейства 501xx.
     */
    private Probe probeStaleTimestamp() throws Exception {
        Thread.sleep(PROBE_GAP.toMillis());
        String timestamp = TIMESTAMP_FORMAT.format(Instant.now().minusSeconds(120));
        String prehash = timestamp + "GET" + BALANCE_PATH;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(okx.getBaseUrl() + BALANCE_PATH))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .header("OK-ACCESS-KEY", okx.getApiKey())
                .header("OK-ACCESS-SIGN", sign(okx.getSecret(), prehash))
                .header("OK-ACCESS-TIMESTAMP", timestamp)
                .header("OK-ACCESS-PASSPHRASE", okx.getPassphrase())
                .header("x-simulated-trading", "1")
                .GET()
                .build();

        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode body = JSON.readTree(response.body());
        Probe result = new Probe("верные креды, устаревший timestamp", response.statusCode(),
                body.path("code").asText(""), body.path("msg").asText(""));
        observed.add(result.toString());
        log.info("[SEC1.1] {}", result);
        return result;
    }

    private String sign(String secret, String prehash) throws Exception {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(secret.getBytes(UTF_8), HMAC_ALGORITHM));
        return Base64.getEncoder().encodeToString(mac.doFinal(prehash.getBytes(UTF_8)));
    }

    /** Наблюдение одной формы: что портили, что ответил источник. */
    private record Probe(String shape, int httpStatus, String code, String msg) {

        @Override
        public String toString() {
            return "форма=" + shape + " · HTTP=" + httpStatus + " · code=" + code + " · msg=" + msg;
        }
    }
}
