package com.example.auth.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Счета тенанта — группа {@code B4} документа
 * `.claude/tests/cases/auth.md`.
 *
 * <p><b>Радиус выборки — операнд пути</b>, а не контекст предъявителя:
 * `auth` принимает тенанта путём либо телом (`docs/architecture/contracts.md`
 * §«Контекст тенанта в вызове»).
 *
 * <p><b>Кейса {@code B4.2} здесь нет:</b> ни один дом не различает «у
 * тенанта нет счетов» и «тенанта нет» — ожидание не выдумывается, и
 * клетка ждёт ответа владельца на находку {@code F-4}. Вторую сторону той
 * же границы несёт {@code B4.4}, у которого дом есть.
 */
class TenantExchangeAccountsBoxTest extends SharedAuthBox {

    @Test
    @DisplayName("B4.1 — радиус выборки — операнд пути")
    void b4_1_theSelectionRadiusIsThePathOperand() {
        String first = provisionTenant("user-b4-1-a");
        String second = provisionTenant("user-b4-1-b");
        String firstToken = identity.browserToken("user-b4-1-a");
        post(EXCHANGE_ACCOUNTS, firstToken, Bodies.registration(first).body());
        post(EXCHANGE_ACCOUNTS, firstToken, Bodies.registration(first).with("label", "reserve").body());
        post(EXCHANGE_ACCOUNTS, identity.browserToken("user-b4-1-b"),
                Bodies.registration(second).body());

        Answer answer = get(EXCHANGE_ACCOUNTS + "/tenant/" + first, firstToken);

        assertThat(answer.status()).isEqualTo(200);
        List<Map<String, Object>> accounts = answer.asList();
        assertThat(accounts).hasSize(2);
        assertThat(accounts).allSatisfy(row ->
                assertThat(row.get("tenantInternalId")).isEqualTo(first));
        assertThat(answer.body()).doesNotContain(second);
    }

    @Test
    @Tag("debt")
    @DisplayName("B4.3 — счета тенанта без предъявленного принципала")
    void b4_3_theTenantAccountsAreNotGivenWithoutAPrincipal() {
        String tenant = provisionTenant("user-b4-3");
        post(EXCHANGE_ACCOUNTS, identity.browserToken("user-b4-3"),
                Bodies.registration(tenant).body());

        Answer answer = get(EXCHANGE_ACCOUNTS + "/tenant/" + tenant);

        assertThat(answer.status()).isEqualTo(401);
        assertThat(answer.body()).doesNotContain("internalId", "exchangeCode");
        assertThat(answer.carriesErrorDto()).isTrue();
    }

    @Test
    @DisplayName("B4.4 — существующий тенант без счетов — пустой перечень, а не отказ")
    void b4_4_anExistingTenantWithoutAccountsGetsAnEmptyList() {
        String withAccounts = provisionTenant("user-b4-4-a");
        post(EXCHANGE_ACCOUNTS, identity.browserToken("user-b4-4-a"),
                Bodies.registration(withAccounts).body());
        String withoutAccounts = provisionTenant("user-b4-4-b");

        Answer answer = get(EXCHANGE_ACCOUNTS + "/tenant/" + withoutAccounts,
                identity.browserToken("user-b4-4-b"));

        assertThat(answer.status()).isEqualTo(200);
        assertThat(answer.asList()).isEmpty();
        assertThat(answer.body()).doesNotContain(withAccounts);
    }
}
