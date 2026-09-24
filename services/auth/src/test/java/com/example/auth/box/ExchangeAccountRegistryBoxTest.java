package com.example.auth.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Реестр счетов целиком — группа {@code B3} документа
 * `.claude/tests/cases/auth.md`.
 *
 * <p><b>Читатель у этой точки объявленный — торговое ядро</b>
 * (`docs/architecture/contracts.md` §«Синхронные вызовы»), и читает оно
 * реестр НЕ по тенанту: перечня тенантов у ядра нет и быть не должно.
 *
 * <p><b>Клетка {@code B3.2} живёт своим классом</b>
 * ({@link EmptyRegistryBoxTest}) по второму основанию собственного
 * контейнера: предмет её — состояние таблицы ЦЕЛИКОМ, и разностной формой
 * он не выражается вовсе
 * (.claude/decisions/test-contour-design-pass.md §«Оснований брать свой
 * контейнер ДВА…»).
 */
class ExchangeAccountRegistryBoxTest extends SharedAuthBox {

    @Test
    @DisplayName("B3.1 — чтение реестра ядром")
    void b3_1_theCoreReadsTheWholeRegistry() {
        String firstTenant = provisionTenant("user-b3-1-a");
        String secondTenant = provisionTenant("user-b3-1-b");
        post(EXCHANGE_ACCOUNTS, identity.browserToken("user-b3-1-a"),
                Bodies.registration(firstTenant).body());
        post(EXCHANGE_ACCOUNTS, identity.browserToken("user-b3-1-b"),
                Bodies.registration(secondTenant).body());
        String serviceToken = identity.token(identity.builder()
                .subject("trading-core")
                .authorizedParty(IdentityStub.SERVICE_CLIENT_ID));

        Answer answer = get(EXCHANGE_ACCOUNTS, serviceToken);

        assertThat(answer.status()).isEqualTo(200);
        List<Map<String, Object>> registry = answer.asList();
        assertThat(registry).extracting(row -> row.get("tenantInternalId"))
                .contains(firstTenant, secondTenant);
        assertThat(registry).allSatisfy(row -> assertThat(row).containsOnlyKeys("internalId",
                "tenantInternalId", "exchangeCode", "label", "contour", "status"));
        assertThat(answer.body()).doesNotContain(Bodies.API_KEY_MARKER, Bodies.SECRET_MARKER,
                Bodies.PASSPHRASE_MARKER, "riskBase", "safetyRung");
    }

    @Test
    @DisplayName("B3.3 — реестр не отдаётся без предъявленного принципала")
    void b3_3_theRegistryIsNotGivenWithoutAPrincipal() {
        String tenant = provisionTenant("user-b3-3");
        post(EXCHANGE_ACCOUNTS, identity.browserToken("user-b3-3"),
                Bodies.registration(tenant).body());

        Answer answer = get(EXCHANGE_ACCOUNTS);

        assertThat(answer.status()).isEqualTo(401);
        assertThat(answer.body()).doesNotContain("internalId", "exchangeCode", tenant);
        assertThat(answer.carriesErrorDto()).isTrue();
    }

    @Test
    @DisplayName("B3.4 — различения клиента на этой точке нет")
    void b3_4_thisPointDoesNotTellClientsApart() {
        String tenant = provisionTenant("user-b3-4");
        post(EXCHANGE_ACCOUNTS, identity.browserToken("user-b3-4"),
                Bodies.registration(tenant).body());
        String serviceToken = identity.token(identity.builder()
                .subject("trading-core")
                .authorizedParty(IdentityStub.SERVICE_CLIENT_ID));

        Answer byBrowser = get(EXCHANGE_ACCOUNTS, identity.browserToken("user-b3-4"));
        Answer byService = get(EXCHANGE_ACCOUNTS, serviceToken);

        assertThat(byBrowser.status()).isEqualTo(200);
        assertThat(byService.status()).isEqualTo(200);
        assertThat(byBrowser.asList()).extracting(row -> row.get("internalId"))
                .containsExactlyInAnyOrderElementsOf(
                        byService.asList().stream().map(row -> row.get("internalId")).toList());
    }
}
