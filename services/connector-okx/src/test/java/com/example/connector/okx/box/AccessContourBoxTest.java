package com.example.connector.okx.box;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.connector.okx.util.OkxConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Контур доступа и форма отказа — группа {@code B8} документа
 * `.claude/tests/cases/connector-okx.md`.
 *
 * <p><b>Вызывающий здесь — сервис, а не человек.</b> Коннектор стои́т
 * ярусом ниже домена, и членство на этой границе не резолвится: тенант
 * приезжает операндом вызова ({@code docs/architecture/contracts.md}
 * §«Контекст тенанта в вызове»). Отсюда и оси токена, которые контур
 * обязан проверять, — подпись, срок, издатель, и ничего сверх.
 *
 * <p><b>Числа, написанные НЕ нами, пиньнуты, а наши — нет.</b> {@code 401}
 * непредъявившемуся ставит точка входа контура, {@code 405} неподдержанному
 * методу — контейнер, и дом называет оба контрактом; собственные коды
 * отказов дом объявляет провизорными и здесь не мерятся.
 */
class AccessContourBoxTest extends SharedConnectorBox {

    private static final String POSITIONS = "/positions";

    private static final String INSTRUMENTS = "/instruments?externalInstrumentType=SWAP";

    @Test
    @DisplayName("B8.1 — проба живости открыта")
    void b8_1_theLivenessProbeIsOpen() {
        Answer answer = getAnonymously("/actuator/health");

        assertThat(answer.status()).isEqualTo(200);
        assertThat(answer.body()).contains("UP");
        assertThat(answer.body()).doesNotContain("apiKey", "accounts", "okx.com", "vault");
    }

    /**
     * Тело отказа доступа собирает общий энфорсер точек входа цепочки
     * ({@code docs/rules/error-handling-policy.md} §«Отказ доступа — тот же
     * контракт, что и прочие ошибки»).
     */
    @Test
    @DisplayName("B8.2 — умолчание контура закрыто")
    void b8_2_theContourDefaultIsClosed() {
        exchange.answers(OkxConstants.ACCOUNT_POSITIONS_PATH, Okx.ok());
        exchange.answers(OkxConstants.INSTRUMENTS_PATH, Okx.ok());
        Integer readsBefore = secrets.reads();

        Answer privateCall = getAnonymously(account(POSITIONS));
        Answer publicCall = getAnonymously(market(INSTRUMENTS));

        assertThat(privateCall.status()).isEqualTo(401);
        assertThat(publicCall.status()).isEqualTo(401);
        assertThat(exchange.count()).isEqualTo(0);
        assertThat(secrets.reads() - readsBefore).isEqualTo(0);
        assertThat(privateCall.carriesErrorDto()).isTrue();
    }

    @Test
    @DisplayName("B8.3 — токен проверяется подписью, сроком и издателем")
    void b8_3_theTokenIsCheckedBySignatureLifetimeAndIssuer() {
        exchange.answers(OkxConstants.ACCOUNT_POSITIONS_PATH, Okx.ok());
        Integer readsBefore = secrets.reads();

        assertThat(getWith(account(POSITIONS), identity.foreignKeyToken()).status()).isEqualTo(401);
        assertThat(getWith(account(POSITIONS), identity.expiredToken()).status()).isEqualTo(401);
        assertThat(getWith(account(POSITIONS), identity.foreignIssuerToken()).status()).isEqualTo(401);

        assertThat(exchange.count()).isEqualTo(0);
        assertThat(secrets.reads() - readsBefore).isEqualTo(0);
    }

    @Test
    @DisplayName("B8.4 — съёма метрик у коннектора не открыто")
    void b8_4_theMetricsScrapeIsNotOpen() {
        Answer answer = getAnonymously("/actuator/prometheus");

        assertThat(answer.status()).isNotEqualTo(200);
        assertThat(answer.body()).doesNotContain("jvm_", "http_server_requests");
    }

    @Test
    @DisplayName("B8.5 — описание поверхности закрыто")
    void b8_5_theSurfaceDescriptionIsClosed() {
        Answer machine = getAnonymously("/v3/api-docs");
        Answer browser = getAnonymously("/swagger-ui/index.html");

        assertThat(machine.status()).isEqualTo(401);
        assertThat(browser.status()).isEqualTo(401);
        assertThat(machine.body()).doesNotContain("/api/v1/accounts", "/api/v1/market");
    }

    /**
     * Статус {@code 405} пишет контейнер, а тело подменяет глобальный
     * обработчик, наследующий обработку отказов контейнера.
     */
    @Test
    @DisplayName("B8.6 — неизвестный путь и неподдержанный метод отвечают отказом доступа")
    void b8_6_anUnknownPathAndAnUnsupportedMethodAnswerWithRefusal() {
        Answer unknown = getAnonymously(account("/nothing"));

        assertThat(unknown.status()).isEqualTo(401);

        Answer unsupported = delete(account("/orders"));

        assertThat(unsupported.status()).isEqualTo(405);
        assertThat(unsupported.carriesErrorDto()).isTrue();
    }

    @Test
    @DisplayName("B8.7 — строк отказа доступа коннектор не пишет")
    void b8_7_theConnectorWritesNoAccessDenialRows() {
        Integer readsBefore = secrets.reads();

        getAnonymously(account(POSITIONS));
        getAnonymously(market(INSTRUMENTS));
        getWith(account(POSITIONS), identity.expiredToken());

        assertThat(secrets.reads() - readsBefore).isEqualTo(0);
        assertThat(neighbour.count()).isEqualTo(0);
        assertThat(exchange.count()).isEqualTo(0);
    }
}
