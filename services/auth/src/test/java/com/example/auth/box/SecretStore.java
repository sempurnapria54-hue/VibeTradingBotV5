package com.example.auth.box;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.boot.json.JsonParserFactory;
import org.testcontainers.vault.VaultContainer;

/**
 * Наблюдение хранилища секретов СНАРУЖИ ящика: своим запросом к
 * контейнеру, а не {@code VaultTemplate} контекста.
 *
 * <p><b>Радиус наблюдения — префикс окружения</b>, и он назван, потому
 * что отрицание «новых записей не появилось» без радиуса не проверяемо:
 * хранилище общее на прогон, и соседние кейсы кладут в него свои
 * секреты. Отсюда форма ассерта — РАЗНОСТНАЯ (снимок до входа против
 * снимка после), а не абсолютная
 * (.claude/decisions/test-contour-design-pass.md §«Отрицание содержимого
 * общего субстрата пишется РАЗНОСТНОЙ формой»).
 *
 * <p><b>Ключи ящик читает, а не пишет:</b> запись есть выход предмета —
 * `auth` единственное место платформы, где ключи счёта пишутся.
 */
final class SecretStore {

    private static final String ACCOUNTS_SEGMENT = "exchange-accounts";

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final String address;
    private final String token;

    private SecretStore(VaultContainer<?> container, String token) {
        this.address = container.getHttpHostAddress();
        this.token = token;
    }

    /** Наблюдатель общего хранилища под корневым токеном. */
    static SecretStore shared() {
        return new SecretStore(AuthSubstrate.vault(), AuthSubstrate.ROOT_TOKEN);
    }

    /** Наблюдатель названного хранилища под корневым токеном. */
    static SecretStore of(VaultContainer<?> container) {
        return new SecretStore(container, AuthSubstrate.ROOT_TOKEN);
    }

    /**
     * Имена секретов под префиксом счетов окружения; пустой перечень,
     * когда префикса ещё нет.
     *
     * @param environment имя окружения — первый сегмент пути
     * @return имена секретов
     */
    @SuppressWarnings("unchecked")
    List<String> accountNames(String environment) {
        HttpResponse<String> answer = send(HttpRequest.newBuilder()
                .uri(URI.create(address + "/v1/" + environment + "/" + ACCOUNTS_SEGMENT + "?list=true"))
                .header("X-Vault-Token", token)
                .GET());
        if (answer.statusCode() != 200) {
            return List.of();
        }
        Map<String, Object> body = JsonParserFactory.getJsonParser().parseMap(answer.body());
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        return ((List<Object>) data.get("keys")).stream().map(String::valueOf).toList();
    }

    /**
     * Поля секрета счёта; пустая карта, когда секрета нет.
     *
     * @param environment       имя окружения
     * @param accountInternalId идентичность счёта
     * @return поля секрета
     */
    @SuppressWarnings("unchecked")
    Map<String, Object> account(String environment, String accountInternalId) {
        HttpResponse<String> answer = send(HttpRequest.newBuilder()
                .uri(URI.create(address + "/v1/" + environment + "/" + ACCOUNTS_SEGMENT
                        + "/" + accountInternalId))
                .header("X-Vault-Token", token)
                .GET());
        if (answer.statusCode() != 200) {
            return Map.of();
        }
        Map<String, Object> body = JsonParserFactory.getJsonParser().parseMap(answer.body());
        return (Map<String, Object>) body.get("data");
    }

    /**
     * Имена секретов, лежащих в корне смонтированного префикса: ими
     * наблюдается ожидание «секрет не лёг в корень хранилища».
     *
     * @param mount смонтированный префикс
     * @return имена секретов корня
     */
    @SuppressWarnings("unchecked")
    List<String> mountRootNames(String mount) {
        HttpResponse<String> answer = send(HttpRequest.newBuilder()
                .uri(URI.create(address + "/v1/" + mount + "?list=true"))
                .header("X-Vault-Token", token)
                .GET());
        if (answer.statusCode() != 200) {
            return List.of();
        }
        Map<String, Object> body = JsonParserFactory.getJsonParser().parseMap(answer.body());
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        return ((List<Object>) data.get("keys")).stream().map(String::valueOf).toList();
    }

    private HttpResponse<String> send(HttpRequest.Builder request) {
        try {
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException failure) {
            throw new IllegalStateException("Хранилище субстрата не ответило", failure);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Ожидание ответа хранилища прервано", failure);
        }
    }
}
