package com.example.connector.okx.box;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.connector.okx.util.OkxConstants;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Конфигурация как вход — группа {@code B9} документа
 * `.claude/tests/cases/connector-okx.md`.
 *
 * <p><b>Мерится ЧТЕНИЕ конфигурации процессом, а не её доставка.</b>
 * Переменные окружения манифеста, сетевые политики и образ — предмет
 * развёртывания, а не ящика
 * (.claude/decisions/test-contour-design-pass.md, решение 1).
 *
 * <p>Клетки {@code B9.1} и {@code B9.2} живут своими классами: у них
 * другое положение осей контекста.
 */
class ConfigurationInputBoxTest extends SharedConnectorBox {

    /** Имя секрета, похожего на свойство процесса: вход клетки {@code B9.3}. */
    private static final String PROCESS_LIKE_SECRET = "connector-okx";

    @Test
    @DisplayName("B9.3 — свойств процесса из хранилища коннектор не берёт")
    void b9_3_theConnectorTakesNoProcessPropertiesFromTheStore() {
        putProcessLikeSecret();
        exchange.answers(OkxConstants.INSTRUMENTS_PATH, Okx.ok(Okx.instrument(INSTRUMENT).text()));

        Answer answer = get(market("/instruments?externalInstrumentType=SWAP"));

        assertThat(answer.status()).isEqualTo(200);
        assertThat(exchange.single(OkxConstants.INSTRUMENTS_PATH)).isNotNull();
        assertThat(answer.body()).doesNotContain("hijacked.example");
    }

    @Test
    @DisplayName("B9.4 — секреты не выходят ни одним каналом")
    void b9_4_secretsLeaveByNoChannel() {
        String absent = "ACC-B9-4-absent";
        String incomplete = "ACC-B9-4-incomplete";
        secrets.remove(absent);
        secrets.put(incomplete, Map.of(
                "apiKey", secrets.apiKeyOf(incomplete),
                "secret", "",
                "passphrase", secrets.passphraseOf(incomplete),
                "contour", "LIVE"));
        exchange.answers(OkxConstants.ACCOUNT_POSITIONS_PATH, Okx.failure("50113", "signature invalid"));

        String refusedByStore = get(account(absent, "/positions")).body();
        String refusedByShape = get(account(incomplete, "/positions")).body();
        String refusedByExchange = get(account("/positions")).body();
        String signed = exchange.requests(OkxConstants.ACCOUNT_POSITIONS_PATH).getLast()
                .getHeader(OkxConstants.ACCESS_SIGN_HEADER);

        String journal = AppLog.text();
        for (String channel : new String[] {refusedByStore, refusedByShape, refusedByExchange, journal}) {
            assertThat(channel).doesNotContain(SecretStore.SECRET, SecretStore.PASSPHRASE,
                    SecretStore.API_KEY);
        }
        assertThat(journal).doesNotContain(signed);
    }

    /**
     * Кладёт в хранилище секрет, чьё имя совпадает с именем сервиса, а
     * поля — с ключами его конфигурации. Попади он в свойства процесса —
     * вызовы ушли бы на чужой адрес, и это наблюдалось бы молчанием стаба.
     */
    private void putProcessLikeSecret() {
        String body = "{\"okx.base-url\":\"http://hijacked.example\","
                + "\"platform.environment.name\":\"hijacked\"}";
        try {
            HttpResponse<String> answer = HttpClient.newHttpClient().send(HttpRequest.newBuilder()
                    .uri(URI.create(ConnectorSubstrate.vault().getHttpHostAddress()
                            + "/v1/" + ConnectorSubstrate.ENVIRONMENT + "/" + PROCESS_LIKE_SECRET))
                    .header("X-Vault-Token", ConnectorSubstrate.ROOT_TOKEN)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build(), HttpResponse.BodyHandlers.ofString());
            if (answer.statusCode() >= 300) {
                throw new IllegalStateException("Хранилище отвергло подготовку: " + answer.statusCode());
            }
        } catch (IOException failure) {
            throw new IllegalStateException("Хранилище субстрата не ответило", failure);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Ожидание ответа хранилища прервано", failure);
        }
    }
}
