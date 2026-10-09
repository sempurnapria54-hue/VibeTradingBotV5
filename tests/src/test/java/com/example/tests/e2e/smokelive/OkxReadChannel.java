package com.example.tests.e2e.smokelive;

import com.example.tests.e2e.Json;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import tools.jackson.databind.JsonNode;

import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * Канал чтения площадки ключом только чтения того же demo-счёта — мимо
 * системы (.claude/tests/cases/smoke-live.md §«Чем дым действует и что
 * читает»; довод — .claude/decisions/smoke-account-return-authority.md).
 *
 * <p><b>Перечень операций закрыт, и все они {@code GET}:</b> права и режимы
 * счёта, плечо по инструменту, заявка по клиентскому идентификатору, живые
 * заявки, живые условные заявки, позиции. Другого метода у класса нет —
 * торговать каналом нельзя и кодом, а не только правами ключа.
 *
 * <p><b>Права ключа канал проверяет сам, первым ходом</b>
 * ({@link #requireReadOnly()}): {@code perm} у {@code GET /api/v5/account/config}
 * равен ровно {@code read_only}, иначе — отказ прогона до первого хода.
 *
 * <p><b>Подпись — своя, вне коннектора</b>
 * (docs/integrations/okx/rules/request-signing.md): {@code timestamp + method
 * + requestPath + body}, HMAC-SHA256 секретом, base64; параметры {@code GET}
 * входят в путь. Заголовок демо-контура стоит на каждом запросе
 * (docs/integrations/okx/contracts/service-urls.md §«Demo trading»), а
 * {@code User-Agent} — потому что без него пограничный фильтр площадки
 * отвечает запросу с хоста не-JSON (.claude/skills/local-stand.md §«Тестовые
 * данные стенда»).
 *
 * <p><b>Чтений мало намеренно:</b> лимиты {@code Read} площадка считает по
 * пользователю, общему с коннектором.
 */
final class OkxReadChannel {

    /** Права ключа, с которыми канал открывается. */
    static final String READ_ONLY = "read_only";

    /** Семьи условных заявок, которые читаются по одной: {@code ordType} у живого среза обязателен. */
    static final List<String> ALGO_FAMILIES = List.of("conditional", "oco", "move_order_stop", "trigger");

    private static final String INSTRUMENT_TYPE = "SWAP";

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(30);

    private static final String USER_AGENT = "vibetrading-smoke/1";

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(CALL_TIMEOUT).build();

    private final String apiKey;

    private final String secret;

    private final String passphrase;

    OkxReadChannel() {
        JsonNode key = Stand.readKey();
        this.apiKey = key.path("apiKey").asString();
        this.secret = key.path("secret").asString();
        this.passphrase = key.path("passphrase").asString();
    }

    /**
     * Открывает канал: файл ключа на месте, и ключ читает, но не торгует.
     *
     * @return открытый канал
     * @throws IllegalStateException файла нет либо права ключа шире чтения — отказ прогона
     */
    static OkxReadChannel open() {
        OkxReadChannel channel = new OkxReadChannel();
        channel.requireReadOnly();
        return channel;
    }

    /** {@code GET /api/v5/account/config} — права ключа, уровень счёта, режим позиций. */
    JsonNode accountConfig() {
        return single(read("/api/v5/account/config"));
    }

    /** {@code GET /api/v5/account/leverage-info} по инструменту и режиму маржи. */
    JsonNode leverage(String instrument, String marginMode) {
        return read("/api/v5/account/leverage-info?instId=" + encoded(instrument) + "&mgnMode=" + encoded(marginMode));
    }

    /**
     * {@code GET /api/v5/trade/order} по клиентскому идентификатору.
     *
     * @return заявка; пустой узел — площадка заявку не нашла
     */
    JsonNode order(String instrument, String clientOrderId) {
        JsonNode data = readTolerating("/api/v5/trade/order?instId=" + encoded(instrument)
                + "&clOrdId=" + encoded(clientOrderId));
        return data.isArray() && data.size() > 0 ? data.get(0) : Json.tree("{}");
    }

    /** {@code GET /api/v5/trade/orders-pending} по инструменту. */
    JsonNode pendingOrders(String instrument) {
        return read("/api/v5/trade/orders-pending?instType=" + INSTRUMENT_TYPE + "&instId=" + encoded(instrument));
    }

    /** {@code GET /api/v5/trade/orders-algo-pending} по инструменту — все семьи, вызовом на семью. */
    List<JsonNode> pendingAlgoOrders(String instrument) {
        List<JsonNode> pending = new ArrayList<>();
        for (String family : ALGO_FAMILIES) {
            read("/api/v5/trade/orders-algo-pending?ordType=" + family + "&instType=" + INSTRUMENT_TYPE
                    + "&instId=" + encoded(instrument)).forEach(pending::add);
        }
        return pending;
    }

    /** {@code GET /api/v5/account/positions} по инструменту — только строки с ненулевым размером. */
    List<JsonNode> openPositions(String instrument) {
        List<JsonNode> open = new ArrayList<>();
        read("/api/v5/account/positions?instType=" + INSTRUMENT_TYPE + "&instId=" + encoded(instrument))
                .forEach(position -> {
                    String size = position.path("pos").asString("");
                    if (isNotBlank(size) && new BigDecimal(size).signum() != 0) {
                        open.add(position);
                    }
                });
        return open;
    }

    /** Права ключа — множество значений {@code perm}. */
    List<String> permissions() {
        return Arrays.stream(accountConfig().path("perm").asString("").split(","))
                .map(String::trim)
                .filter(value -> isFalse(value.isEmpty()))
                .collect(Collectors.toList());
    }

    private void requireReadOnly() {
        List<String> permissions = permissions();
        if (isFalse(List.of(READ_ONLY).equals(permissions))) {
            throw new IllegalStateException("ОТКАЗ ПРОГОНА: права ключа канала чтения — " + permissions
                    + ", а не ровно " + READ_ONLY + ": ключом, которым можно торговать, дым не стартует"
                    + " (.claude/decisions/smoke-account-return-authority.md)");
        }
    }

    private JsonNode read(String requestPath) {
        JsonNode answer = signedGet(requestPath);
        if (isFalse("0".equals(answer.path("code").asString("")))) {
            throw new IllegalStateException("ОТКАЗ ПРОГОНА: площадка отвергла чтение " + requestPath + " — code="
                    + answer.path("code").asString("") + ", msg=" + answer.path("msg").asString(""));
        }
        return answer.path("data");
    }

    /**
     * Чтение, у которого «не найдено» — исход, а не отказ: площадка отвечает
     * на ненайденную заявку кодом ошибки (docs/integrations/okx/contracts/order.md,
     * «Order details»), и кейс обязан отличить его от отвергнутой подписи.
     */
    private JsonNode readTolerating(String requestPath) {
        JsonNode answer = signedGet(requestPath);
        String code = answer.path("code").asString("");
        if ("0".equals(code)) {
            return answer.path("data");
        }
        if (code.startsWith("501")) {
            throw new IllegalStateException("ОТКАЗ ПРОГОНА: площадка отвергла креды канала — code=" + code
                    + ", msg=" + answer.path("msg").asString(""));
        }
        return Json.tree("[]");
    }

    private JsonNode signedGet(String requestPath) {
        String timestamp = TIMESTAMP.format(Instant.now());
        HttpRequest request = HttpRequest.newBuilder(URI.create(Stand.okxBaseUrl() + requestPath))
                .timeout(CALL_TIMEOUT)
                .header("OK-ACCESS-KEY", apiKey)
                .header("OK-ACCESS-SIGN", sign(timestamp + "GET" + requestPath))
                .header("OK-ACCESS-TIMESTAMP", timestamp)
                .header("OK-ACCESS-PASSPHRASE", passphrase)
                .header("x-simulated-trading", "1")
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .GET()
                .build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return Json.tree(response.body());
        } catch (IOException failure) {
            throw new IllegalStateException("ОТКАЗ ПРОГОНА: площадка не ответила на чтение " + requestPath, failure);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Чтение площадки прервано: " + requestPath, failure);
        } catch (RuntimeException failure) {
            throw new IllegalStateException("ОТКАЗ ПРОГОНА: ответ площадки на " + requestPath + " не JSON", failure);
        }
    }

    private String sign(String prehash) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(prehash.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException failure) {
            throw new IllegalStateException("Подпись канала чтения не собралась", failure);
        }
    }

    private static JsonNode single(JsonNode data) {
        if (isFalse(data.isArray()) || data.size() != 1) {
            throw new IllegalStateException("ОТКАЗ ПРОГОНА: площадка вернула не одну строку конфигурации счёта");
        }
        return data.get(0);
    }

    private static String encoded(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    @Override
    public String toString() {
        return "OkxReadChannel[" + Stand.okxBaseUrl() + ", demo]";
    }
}
