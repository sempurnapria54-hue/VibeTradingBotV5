package com.example.auth.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Контур доступа и форма отказа — группа {@code B5} документа
 * `.claude/tests/cases/auth.md`.
 *
 * <p><b>Предмет группы — сама поверхность как контур:</b> что открыто,
 * что закрыто умолчанием и какой формой отвечает отказ, произведённый не
 * нашим кодом, а контейнером (`docs/rules/api-access-policy.md`,
 * `docs/rules/error-handling-policy.md`).

 */
class AccessContourBoxTest extends SharedAuthBox {

    /** Таблица следа отказов доступа. */
    private static final String DENIALS_TABLE = "access_denials";

    /** Путь клетки следа: поверхности неизвестен, строку своей попытки выделяет. */
    private static final String TRACE_PATH = "/api/v1/auth/b5-7-trace";

    @Test
    @DisplayName("B5.1 — проба живости открыта")
    void b5_1_theLivenessProbeIsOpen() {
        Answer answer = get("/actuator/health");

        assertThat(answer.status()).isEqualTo(200);
        assertThat(answer.body()).contains("UP");
        assertThat(answer.body()).doesNotContain("tenant", "exchangeAccount", "internalId");
    }

    @Test
    @DisplayName("B5.2 — съём метрик у `auth` не открыт")
    void b5_2_theMetricsScrapeIsNotOpen() {
        Answer answer = get("/actuator/prometheus");

        assertThat(answer.status()).isNotEqualTo(200);
        assertThat(answer.body()).doesNotContain("jvm_", "http_server_requests");
    }

    @Test
    @DisplayName("B5.3 — машинное описание поверхности закрыто умолчанием")
    void b5_3_theMachineReadableSurfaceDescriptionIsClosed() {
        Answer answer = get("/v3/api-docs");

        assertThat(answer.status()).isEqualTo(401);
        assertThat(answer.body()).doesNotContain(MEMBERSHIPS_SELF, EXCHANGE_ACCOUNTS);
    }

    @Test
    @DisplayName("B5.4 — неизвестный путь отвечает отказом, а не «не найдено»")
    void b5_4_anUnknownPathAnswersWithRefusal() {
        Answer answer = get("/api/v1/auth/whatever");

        assertThat(answer.status()).isEqualTo(401);
    }

    /**
     * Статус написан контейнером, подменяется только тело — его подменяет
     * {@code GlobalExceptionHandler}, наследующий обработку отказов
     * контейнера.
     */
    @Test
    @DisplayName("B5.5 — неподдержанный метод отвечает 405 единым error-DTO")
    void b5_5_anUnsupportedMethodAnswersWithTheSharedErrorDto() {
        Long tenantsBefore = rows.count("tenants");
        Long membershipsBefore = rows.count("memberships");

        Answer answer = get(MEMBERSHIPS_SELF, identity.browserToken("user-b5-5"));

        assertThat(answer.status()).isEqualTo(405);
        assertThat(answer.carriesErrorDto()).isTrue();
        assertThat(rows.count("tenants")).isEqualTo(tenantsBefore);
        assertThat(rows.count("memberships")).isEqualTo(membershipsBefore);
    }

    @Test
    @DisplayName("B5.6 — неразбираемое тело запроса отвечает 400 единым error-DTO")
    void b5_6_anUnparseableBodyAnswersWithTheSharedErrorDto() {
        provisionTenant("user-b5-6");
        Long accountsBefore = rows.count("exchange_accounts");
        Integer secretsBefore = secrets.accountNames(AuthSubstrate.ENVIRONMENT).size();

        Answer answer = post(EXCHANGE_ACCOUNTS, identity.browserToken("user-b5-6"), "{");

        assertThat(answer.status()).isEqualTo(400);
        assertThat(answer.carriesErrorDto()).isTrue();
        assertThat(rows.count("exchange_accounts")).isEqualTo(accountsBefore);
        assertThat(secrets.accountNames(AuthSubstrate.ENVIRONMENT)).hasSize(secretsBefore);
    }

    @Test
    @DisplayName("B5.8 — браузерное описание поверхности закрыто умолчанием")
    void b5_8_theBrowserSurfaceDescriptionIsClosed() {
        Answer answer = get("/swagger-ui/index.html");

        assertThat(answer.status()).isEqualTo(401);
        assertThat(answer.body()).doesNotContain(MEMBERSHIPS_SELF, EXCHANGE_ACCOUNTS, "swagger-ui");
    }

    /**
     * След отказа у сервиса со своей базой — строка до ответа
     * (docs/rules/api-access-policy.md §«След отказа пишет тот, у кого есть
     * база»; docs/models/domain/other/AccessDenial.md).
     *
     * <p><b>Путь клетки свой</b>, неизвестный поверхности: строки у ящика
     * между клетками не опустошаются, и отказы соседних клеток лежат в той
     * же таблице. Своя поверхность выделяет строку этой попытки, а
     * неизвестный путь отвергается тем же отказом, что закрытая точка
     * ({@code B5.4}).
     */
    @Test
    @DisplayName("B5.7 — след отказа доступа")
    void b5_7_anAccessRefusalLeavesItsTrace() {
        Answer answer = get(TRACE_PATH);

        assertThat(answer.status()).isEqualTo(401);
        List<Map<String, Object>> denials = rows.rowsWhere(DENIALS_TABLE, "surface", "GET " + TRACE_PATH);
        assertThat(denials).as("попытка была одна, и строка у неё одна").hasSize(1);
        Map<String, Object> denial = denials.getFirst();
        assertThat(denial.get("outcome")).isEqualTo("PRINCIPAL_ABSENT");
        assertThat(denial.get("principal")).as("принятого принципала у отказа нет").isNull();
        assertThat(denial.get("created_at")).as("момент строки проставлен").isNotNull();
    }
}
