package com.example.auth.box;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Отсутствие выходов — группа {@code B7} документа
 * `.claude/tests/cases/auth.md`.
 *
 * <p><b>Ожидание о субстрате, которого у ящика НЕТ, живёт ОДНИМ домом.</b>
 * Брокера и стабов соседей прогону не поднимают вовсе, поэтому «ни одного
 * сообщения ни в одной теме» и «стабы не получили ни одного запроса»
 * прогону не предъявляются ничем — тема у процесса без продюсера не
 * возникает по построению
 * (.claude/decisions/test-contour-design-pass.md §«Ожидание о субстрате,
 * которого у ящика НЕТ, снимается, а не переписывается»). Наблюдаема у
 * этих отрицаний только СТРУКТУРНАЯ половина, и она здесь и мерится:
 * чего нет в classpath, в схеме и в конфигурации сервиса.
 *
 * <p><b>Структурная половина читается по артефактам сервиса</b> — его
 * classpath и его {@code application.yaml}, — и это названная цена, а не
 * умолчание: поверхности, отвечающей «каких клиентов я объявляю», у
 * сервиса нет, а чтение бинов контекста было бы касанием внутренности,
 * которого форма ящика не допускает (решение 1).
 */
class AbsentOutputsBoxTest extends SharedAuthBox {

    @Test
    @DisplayName("B7.1 — сервис не публикует событий")
    void b7_1_theServicePublishesNoEvents() {
        String tenant = provisionTenant("user-b7-1");

        Answer registered = post(EXCHANGE_ACCOUNTS, identity.browserToken("user-b7-1"),
                Bodies.registration(tenant).body());

        assertThat(registered.status()).isEqualTo(201);
        assertThat(rows.tableNames()).noneSatisfy(name -> assertThat(name).contains("outbox"));
        assertThatThrownBy(() -> Class.forName("org.springframework.kafka.core.KafkaTemplate"))
                .isInstanceOf(ClassNotFoundException.class);
    }

    @Test
    @DisplayName("B7.2 — сервис не зовёт соседей")
    void b7_2_theServiceCallsNoNeighbours() {
        String tenant = provisionTenant("user-b7-2");
        String token = identity.browserToken("user-b7-2");

        Answer registered = post(EXCHANGE_ACCOUNTS, token, Bodies.registration(tenant).body());
        Answer registry = get(EXCHANGE_ACCOUNTS, token);
        Answer ofTenant = get(EXCHANGE_ACCOUNTS + "/tenant/" + tenant, token);

        assertThat(registered.status()).isEqualTo(201);
        assertThat(registry.status()).isEqualTo(200);
        assertThat(ofTenant.status()).isEqualTo(200);
        assertThat(configuration()).doesNotContain("neighbours");
    }

    @Test
    @DisplayName("B7.3 — сервис не ходит в чужие базы")
    void b7_3_theServiceReadsNoForeignDatabase() {
        String tenant = provisionTenant("user-b7-3");
        String token = identity.browserToken("user-b7-3");

        Answer registered = post(EXCHANGE_ACCOUNTS, token, Bodies.registration(tenant).body());
        Answer registry = get(EXCHANGE_ACCOUNTS, token);

        assertThat(registered.status()).isEqualTo(201);
        assertThat(registry.status()).isEqualTo(200);
        assertThat(configuration()).doesNotContain("second-datasource", "datasource-");
    }

    @Test
    @DisplayName("B7.4 — иных рёбер статуса поверхность не производит")
    void b7_4_theSurfaceProducesNoOtherStatusEdges() {
        String tenant = provisionTenant("user-b7-4");
        String token = identity.browserToken("user-b7-4");

        post(MEMBERSHIPS_SELF, token, "");
        Answer registered = post(EXCHANGE_ACCOUNTS, token,
                Bodies.registration(tenant).with("label", "b7-4").body());
        get(EXCHANGE_ACCOUNTS, token);
        get(EXCHANGE_ACCOUNTS + "/tenant/" + tenant, token);
        get("/actuator/health");

        assertThat(registered.status()).isEqualTo(201);
        assertThat(rows.row("tenants", "internal_id", tenant).get("status")).isEqualTo("ACTIVE");
        assertThat(rows.rowsWhere("exchange_accounts", "tenant_id", tenant))
                .isNotEmpty()
                .allSatisfy(row -> assertThat(row.get("status")).isEqualTo("ACTIVE"));
    }

    private String configuration() {
        try (InputStream source = getClass().getResourceAsStream("/application.yaml")) {
            return new String(source.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new IllegalStateException("Конфигурация сервиса не прочиталась", failure);
        }
    }
}
