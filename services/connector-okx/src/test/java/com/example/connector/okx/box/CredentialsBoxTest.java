package com.example.connector.okx.box;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.connector.okx.util.OkxConstants;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Ключи счёта: резолв, кэш, контур — группа {@code B1} документа
 * `.claude/tests/cases/connector-okx.md`.
 *
 * <p><b>Предмет группы — то единственное, что коннектор помнит между
 * вызовами.</b> Базы у него нет, и всё его состояние — короткий кэш
 * ключей; адрес ключей выводится из счёта в пути, а контур приезжает
 * вместе с ними и выражается заголовком запроса
 * ({@code docs/architecture/tenant-and-exchange.md} §Ключи).
 *
 * <p><b>Счёт у каждой клетки СВОЙ, и это несущее.</b> Контекст общий на
 * класс, кэш ключей живёт в нём, и клетка, считающая походы в хранилище,
 * на чужом счёте считала бы кэш соседа.
 *
 * <p>Клетки {@code B1.7}, {@code B1.9} и {@code B1.12} живут своими
 * классами: у них другое положение осей контекста.
 */
class CredentialsBoxTest extends SharedConnectorBox {

    private static final String POSITIONS = "/positions";

    @Test
    @DisplayName("B1.1 — приватный вызов берёт ключи по адресу счёта и подписывает запрос")
    void b1_1_aPrivateCallTakesKeysByAccountAddressAndSignsTheRequest() {
        String account = "ACC-B1-1";
        secrets.put(account, "LIVE");
        exchange.answers(OkxConstants.ACCOUNT_POSITIONS_PATH, Okx.ok());
        Integer readsBefore = secrets.readsOf(account);

        Answer answer = get(account(account, POSITIONS));

        assertThat(answer.status()).isEqualTo(200);
        assertThat(answer.asList()).isEmpty();
        LoggedRequest sent = exchange.single(OkxConstants.ACCOUNT_POSITIONS_PATH);
        assertThat(sent.getMethod().getName()).isEqualTo("GET");
        assertThat(sent.getUrl()).isEqualTo(OkxConstants.ACCOUNT_POSITIONS_PATH + "?instType=SWAP");
        assertThat(sent.getHeader(OkxConstants.ACCESS_KEY_HEADER)).isEqualTo(secrets.apiKeyOf(account));
        assertThat(sent.getHeader(OkxConstants.ACCESS_PASSPHRASE_HEADER))
                .isEqualTo(secrets.passphraseOf(account));
        assertThat(sent.getHeader(OkxConstants.ACCESS_TIMESTAMP_HEADER)).isNotBlank();
        assertThat(sent.getHeader(OkxConstants.ACCESS_SIGN_HEADER)).isEqualTo(Signatures.expected(
                secrets.secretOf(account), sent.getHeader(OkxConstants.ACCESS_TIMESTAMP_HEADER),
                "GET", sent.getUrl(), ""));
        assertThat(sent.containsHeader(OkxConstants.SIMULATED_HEADER)).isFalse();
        assertThat(secrets.readsOf(account) - readsBefore).isEqualTo(1);
        assertThat(exchange.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("B1.2 — ключей в хранилище нет")
    void b1_2_thereAreNoKeysInTheStore() {
        String account = "ACC-B1-2";
        secrets.remove(account);
        exchange.answers(OkxConstants.ACCOUNT_POSITIONS_PATH, Okx.ok());

        Answer first = get(account(account, POSITIONS));

        assertThat(first.errorCode()).isEqualTo("CREDENTIALS_UNAVAILABLE");
        assertThat(exchange.count()).isEqualTo(0);

        Integer readsBefore = secrets.readsOf(account);
        Answer second = get(account(account, POSITIONS));

        assertThat(second.errorCode()).isEqualTo("CREDENTIALS_UNAVAILABLE");
        assertThat(secrets.readsOf(account) - readsBefore).isEqualTo(1);
        assertThat(exchange.count()).isEqualTo(0);
    }

    @Test
    @DisplayName("B1.3 — неполный секрет — тот же отказ, что и его отсутствие")
    void b1_3_anIncompleteSecretRefusesLikeAnAbsentOne() {
        exchange.answers(OkxConstants.ACCOUNT_POSITIONS_PATH, Okx.ok());

        assertThat(refusalOnIncompleteSecret("ACC-B1-3-secret", "secret")).isEqualTo("CREDENTIALS_UNAVAILABLE");
        assertThat(refusalOnIncompleteSecret("ACC-B1-3-key", "apiKey")).isEqualTo("CREDENTIALS_UNAVAILABLE");
        assertThat(refusalOnIncompleteSecret("ACC-B1-3-pass", "passphrase")).isEqualTo("CREDENTIALS_UNAVAILABLE");
        assertThat(exchange.count()).isEqualTo(0);
    }

    @Test
    @DisplayName("B1.4 — демо-контур ключей выражается заголовком запроса")
    void b1_4_theDemoContourIsExpressedByARequestHeader() {
        String live = "ACC-B1-4-live";
        String demo = "ACC-B1-4-demo";
        secrets.put(live, "LIVE");
        secrets.put(demo, "DEMO");
        exchange.answers(OkxConstants.ACCOUNT_POSITIONS_PATH, Okx.ok());

        assertThat(get(account(demo, POSITIONS)).status()).isEqualTo(200);
        assertThat(get(account(live, POSITIONS)).status()).isEqualTo(200);

        LoggedRequest demoRequest = exchange.requests(OkxConstants.ACCOUNT_POSITIONS_PATH).getFirst();
        LoggedRequest liveRequest = exchange.requests(OkxConstants.ACCOUNT_POSITIONS_PATH).getLast();
        assertThat(demoRequest.getHeader(OkxConstants.SIMULATED_HEADER)).isEqualTo(OkxConstants.SIMULATED_ON);
        assertThat(liveRequest.containsHeader(OkxConstants.SIMULATED_HEADER)).isFalse();
        assertThat(demoRequest.getHeader(OkxConstants.ACCESS_KEY_HEADER)).isEqualTo(secrets.apiKeyOf(demo));
        assertThat(liveRequest.getHeader(OkxConstants.ACCESS_KEY_HEADER)).isEqualTo(secrets.apiKeyOf(live));
    }

    @Test
    @DisplayName("B1.5 — контур в секрете обязателен и умолчания не имеет")
    void b1_5_theContourIsMandatoryAndHasNoDefault() {
        String account = "ACC-B1-5";
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("apiKey", secrets.apiKeyOf(account));
        fields.put("secret", secrets.secretOf(account));
        fields.put("passphrase", secrets.passphraseOf(account));
        secrets.put(account, fields);
        exchange.answers(OkxConstants.ACCOUNT_POSITIONS_PATH, Okx.ok());

        Answer answer = get(account(account, POSITIONS));

        assertThat(answer.errorCode()).isEqualTo("CREDENTIALS_UNAVAILABLE");
        assertThat(exchange.count()).isEqualTo(0);
    }

    /**
     * Ожидание взято из дома: перечень классов границы закрыт, и «негодного
     * входа» в нём нет — испорчено содержимое ХРАНИЛИЩА, а запрос
     * вызывающего корректен.
     */
    @Test
    @DisplayName("B1.6 — испорченное значение контура отказывает классом отказа границы")
    void b1_6_aBrokenContourValueRefusesWithABoundaryFailureClass() {
        String account = "ACC-B1-6";
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("apiKey", secrets.apiKeyOf(account));
        fields.put("secret", secrets.secretOf(account));
        fields.put("passphrase", secrets.passphraseOf(account));
        fields.put("contour", "PAPER");
        secrets.put(account, fields);
        exchange.answers(OkxConstants.ACCOUNT_POSITIONS_PATH, Okx.ok());

        Answer answer = get(account(account, POSITIONS));

        assertThat(exchange.count()).isEqualTo(0);
        assertThat(answer.errorCode()).isEqualTo("CREDENTIALS_UNAVAILABLE");
    }

    @Test
    @DisplayName("B1.8 — ключи кэшируются на короткий срок")
    void b1_8_keysAreCachedForAShortWhile() {
        String account = "ACC-B1-8";
        secrets.put(account, "LIVE");
        exchange.answers(OkxConstants.ACCOUNT_POSITIONS_PATH, Okx.ok());
        Integer readsBefore = secrets.readsOf(account);

        assertThat(get(account(account, POSITIONS)).status()).isEqualTo(200);
        assertThat(get(account(account, POSITIONS)).status()).isEqualTo(200);

        assertThat(secrets.readsOf(account) - readsBefore).isEqualTo(1);
        assertThat(exchange.requests(OkxConstants.ACCOUNT_POSITIONS_PATH)).hasSize(2);
        exchange.requests(OkxConstants.ACCOUNT_POSITIONS_PATH).forEach(request ->
                assertThat(request.getHeader(OkxConstants.ACCESS_SIGN_HEADER)).isNotBlank());
    }

    @Test
    @DisplayName("B1.10 — отказ хранилища не кэшируется")
    void b1_10_aStoreRefusalIsNotCached() {
        String account = "ACC-B1-10";
        secrets.remove(account);
        exchange.answers(OkxConstants.ACCOUNT_POSITIONS_PATH, Okx.ok());

        Answer refused = get(account(account, POSITIONS));
        assertThat(refused.errorCode()).isEqualTo("CREDENTIALS_UNAVAILABLE");

        secrets.put(account, "LIVE");
        Answer accepted = get(account(account, POSITIONS));

        assertThat(accepted.status()).isEqualTo(200);
        assertThat(exchange.single(OkxConstants.ACCOUNT_POSITIONS_PATH)
                .getHeader(OkxConstants.ACCESS_KEY_HEADER)).isEqualTo(secrets.apiKeyOf(account));
    }

    @Test
    @DisplayName("B1.11 — два счёта — две подписи")
    void b1_11_twoAccountsMeanTwoSignatures() {
        String first = "ACC-B1-11-a";
        String second = "ACC-B1-11-b";
        secrets.put(first, "LIVE");
        secrets.put(second, "LIVE");
        exchange.answers(OkxConstants.ACCOUNT_POSITIONS_PATH, Okx.ok());
        Integer firstReadsBefore = secrets.readsOf(first);
        Integer secondReadsBefore = secrets.readsOf(second);

        assertThat(get(account(first, POSITIONS)).status()).isEqualTo(200);
        assertThat(get(account(second, POSITIONS)).status()).isEqualTo(200);

        LoggedRequest firstRequest = exchange.requests(OkxConstants.ACCOUNT_POSITIONS_PATH).getFirst();
        LoggedRequest secondRequest = exchange.requests(OkxConstants.ACCOUNT_POSITIONS_PATH).getLast();
        assertThat(firstRequest.getHeader(OkxConstants.ACCESS_KEY_HEADER)).isEqualTo(secrets.apiKeyOf(first));
        assertThat(secondRequest.getHeader(OkxConstants.ACCESS_KEY_HEADER)).isEqualTo(secrets.apiKeyOf(second));
        assertThat(firstRequest.getHeader(OkxConstants.ACCESS_SIGN_HEADER))
                .isNotEqualTo(secondRequest.getHeader(OkxConstants.ACCESS_SIGN_HEADER));
        assertThat(secrets.readsOf(first) - firstReadsBefore).isEqualTo(1);
        assertThat(secrets.readsOf(second) - secondReadsBefore).isEqualTo(1);
    }

    /** Кладёт секрет с одним пустым полем и отдаёт класс отказа поверхности. */
    private String refusalOnIncompleteSecret(String account, String emptyField) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("apiKey", secrets.apiKeyOf(account));
        fields.put("secret", secrets.secretOf(account));
        fields.put("passphrase", secrets.passphraseOf(account));
        fields.put("contour", "LIVE");
        fields.put(emptyField, "");
        secrets.put(account, fields);

        Answer answer = get(account(account, POSITIONS));
        assertThat(answer.body()).doesNotContain(SecretStore.SECRET, SecretStore.PASSPHRASE);
        return answer.errorCode();
    }
}
