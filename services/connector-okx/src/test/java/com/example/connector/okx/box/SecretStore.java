package com.example.connector.okx.box;

import com.example.tradingbot.domain.util.ExchangeAccountKeyPath;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Хранилище секретов СНАРУЖИ ящика: своим запросом к контейнеру, а не
 * {@code VaultTemplate} контекста.
 *
 * <p><b>Здесь оно и вход, и наблюдатель, и роли эти разные.</b> Ключи
 * счёта — ВХОД кейса: коннектор их только читает, писать их право имеет
 * `auth`, и кладёт их сюда сам прогон. Число походов за ними — ВЫХОД, и
 * наблюдается оно журналом аудита хранилища: изнутри процесса счётчика
 * нет, а подменить бин резолвера значило бы перестать быть ящиком.
 *
 * <p><b>Счёт чтений — РАЗНОСТНЫЙ.</b> Хранилище общее на прогон, журнал
 * аудита пишется с его старта, и абсолютное число говорило бы о прогоне, а
 * не о клетке: снимок берётся до входа и после
 * (.claude/decisions/test-contour-design-pass.md §«Отрицание содержимого
 * общего субстрата пишется РАЗНОСТНОЙ формой»).
 *
 * <p><b>Путь ключей собирается общей формой</b>
 * ({@link ExchangeAccountKeyPath}) — той же, которой его вычисляет сам
 * коннектор и по которой кладёт ключи `auth`. Свой литерал здесь был бы
 * вторым носителем формы адреса и разошёлся бы с ней молча, оставив кейс
 * зелёным на неверном пути.
 */
final class SecretStore {

    /** Узнаваемое значение API-ключа: им наблюдается его невыход наружу. */
    static final String API_KEY = "api-KEY-MARKER";

    /** Узнаваемое значение секрета ключа. */
    static final String SECRET = "secret-MARKER";

    /** Узнаваемое значение passphrase. */
    static final String PASSPHRASE = "pass-MARKER";

    private static final SecretStore INSTANCE = new SecretStore();

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final String address = ConnectorSubstrate.vault().getHttpHostAddress();

    private SecretStore() {
    }

    static SecretStore shared() {
        return INSTANCE;
    }

    /** Кладёт полный секрет счёта: четыре поля, контур назван. */
    void put(String accountInternalId, String contour) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("apiKey", API_KEY + "-" + accountInternalId);
        fields.put("secret", SECRET + "-" + accountInternalId);
        fields.put("passphrase", PASSPHRASE + "-" + accountInternalId);
        fields.put("contour", contour);
        put(accountInternalId, fields);
    }

    /** Кладёт секрет счёта дословно названными полями: ими подаются неполные входы. */
    void put(String accountInternalId, Map<String, String> fields) {
        StringBuilder body = new StringBuilder("{");
        fields.forEach((name, value) -> {
            if (body.length() > 1) {
                body.append(',');
            }
            body.append('"').append(name).append("\":\"").append(value).append('"');
        });
        body.append('}');
        send(HttpRequest.newBuilder()
                .uri(URI.create(address + "/v1/" + path(accountInternalId)))
                .header("X-Vault-Token", ConnectorSubstrate.ROOT_TOKEN)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString())));
    }

    /** Убирает секрет счёта: вход клеток «ключей в хранилище нет». */
    void remove(String accountInternalId) {
        send(HttpRequest.newBuilder()
                .uri(URI.create(address + "/v1/" + path(accountInternalId)))
                .header("X-Vault-Token", ConnectorSubstrate.ROOT_TOKEN)
                .DELETE());
    }

    /** Значение API-ключа счёта — ровно то, которым обязан быть подписан его запрос. */
    String apiKeyOf(String accountInternalId) {
        return API_KEY + "-" + accountInternalId;
    }

    /** Значение секрета счёта: им тест считает ожидаемую подпись сам. */
    String secretOf(String accountInternalId) {
        return SECRET + "-" + accountInternalId;
    }

    /** Значение passphrase счёта. */
    String passphraseOf(String accountInternalId) {
        return PASSPHRASE + "-" + accountInternalId;
    }

    /**
     * Сколько раз хранилище отдало ключи этого счёта с начала прогона.
     *
     * <p>Считается по журналу аудита: путь запроса лежит в нём открытым
     * текстом, значения — под HMAC. Записей на операцию две (запрос и
     * ответ), и считается только запрос — иначе число удваивалось бы.
     */
    Integer readsOf(String accountInternalId) {
        String marker = "\"path\":\"" + path(accountInternalId) + "\"";
        Integer reads = 0;
        for (String line : ConnectorSubstrate.auditLog().split("\n")) {
            if (line.contains("\"type\":\"request\"") && line.contains(marker)
                    && line.contains("\"operation\":\"read\"")) {
                reads++;
            }
        }
        return reads;
    }

    /** Сколько чтений ключей хранилище отдало всем счетам с начала прогона. */
    Integer reads() {
        Integer reads = 0;
        for (String line : ConnectorSubstrate.auditLog().split("\n")) {
            if (line.contains("\"type\":\"request\"") && line.contains("\"operation\":\"read\"")
                    && line.contains("\"path\":\"" + ConnectorSubstrate.ENVIRONMENT + "/exchange-accounts/")) {
                reads++;
            }
        }
        return reads;
    }

    private static String path(String accountInternalId) {
        return ExchangeAccountKeyPath.of(ConnectorSubstrate.ENVIRONMENT, accountInternalId);
    }

    private void send(HttpRequest.Builder request) {
        try {
            HttpResponse<String> answer = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (answer.statusCode() >= 300) {
                throw new IllegalStateException("Хранилище субстрата отвергло подготовку: "
                        + answer.statusCode() + " " + answer.body());
            }
        } catch (IOException failure) {
            throw new IllegalStateException("Хранилище субстрата не ответило", failure);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Ожидание ответа хранилища прервано", failure);
        }
    }
}
