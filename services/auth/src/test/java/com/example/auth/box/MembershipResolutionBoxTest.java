package com.example.auth.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Резолв членств предъявителя — группа {@code B1} документа
 * `.claude/tests/cases/auth.md`.
 *
 * <p><b>Тропа одна, и она же заводит тенанта</b> при первом предъявлении
 * токена человека (`docs/architecture/contracts.md` §«Контекст тенанта в
 * вызове»): ноль членств — не отказ, а заведение.
 *
 * <p><b>Субъект у каждой клетки свой, и это несущее.</b> База общая на
 * прогон, порядка кейсов контур не называет ни одним решением, поэтому
 * предусловие «этого пользователя ещё не видели» ставится выбором
 * субъекта, а не пустотой базы; отрицания по числу строк пишутся
 * РАЗНОСТНОЙ формой
 * (.claude/decisions/test-contour-design-pass.md §«Отрицание содержимого
 * общего субстрата пишется РАЗНОСТНОЙ формой»).
 *
 * <p><b>Все оси токена суть ВХОД</b>, которым управляет тест: ключ
 * подписи, издатель, срок, клиент выдачи, субъект, имя и заголовок типа
 * (решение 4). Заготовка выдаётся корректной, и кейс сдвигает ровно одну
 * ось — иначе красный прогон не назвал бы, какая её произвела.
 *
 * <p><b>Клетки {@code B1.4} и {@code B1.12} разведены по контекстам</b>
 * неслучайно: первая требует НЕНАСТРОЕННОЙ браузерной тропы, то есть
 * другого положения оси окружения, и живёт своим классом
 * ({@link UnconfiguredBrowserTropeBoxTest}); вторая сдвигает claim токена
 * и остаётся здесь.
 */
class MembershipResolutionBoxTest extends SharedAuthBox {

    @Test
    @DisplayName("B1.1 — первое предъявление токена человека заводит тенанта")
    void b1_1_theFirstPresentationProvisionsATenant() {
        Long tenantsBefore = rows.count("tenants");
        Long membershipsBefore = rows.count("memberships");
        Integer secretsBefore = secrets.accountNames(AuthSubstrate.ENVIRONMENT).size();
        String token = identity.token(identity.builder().subject("user-1").preferredUsername("Роман"));

        Answer answer = post(MEMBERSHIPS_SELF, token, "");

        assertThat(answer.status()).isEqualTo(200);
        Map<String, Object> membership = answer.single();
        assertThat(membership.get("role")).isEqualTo("OWNER");
        assertThat(String.valueOf(membership.get("internalId"))).isNotBlank();
        assertThat(String.valueOf(membership.get("tenantId"))).isNotBlank();
        assertThat(rows.count("tenants")).isEqualTo(tenantsBefore + 1);
        assertThat(rows.count("memberships")).isEqualTo(membershipsBefore + 1);
        Map<String, Object> tenant = rows.row("tenants", "internal_id",
                String.valueOf(membership.get("tenantId")));
        assertThat(tenant.get("name")).isEqualTo("Роман");
        assertThat(tenant.get("status")).isEqualTo("ACTIVE");
        Map<String, Object> stored = rows.row("memberships", "user_id", "user-1");
        assertThat(stored.get("role")).isEqualTo("OWNER");
        assertThat(secrets.accountNames(AuthSubstrate.ENVIRONMENT)).hasSize(secretsBefore);
    }

    @Test
    @DisplayName("B1.2 — повторное предъявление второго тенанта не заводит")
    void b1_2_aRepeatedPresentationProvisionsNothing() {
        String token = identity.browserToken("user-2");
        Map<String, Object> first = post(MEMBERSHIPS_SELF, token, "").single();
        Long tenantsAfterFirst = rows.count("tenants");
        Long membershipsAfterFirst = rows.count("memberships");
        Integer secretsAfterFirst = secrets.accountNames(AuthSubstrate.ENVIRONMENT).size();

        Answer answer = post(MEMBERSHIPS_SELF, token, "");

        assertThat(answer.status()).isEqualTo(200);
        Map<String, Object> repeated = answer.single();
        assertThat(repeated.get("internalId")).isEqualTo(first.get("internalId"));
        assertThat(repeated.get("tenantId")).isEqualTo(first.get("tenantId"));
        assertThat(rows.count("tenants")).isEqualTo(tenantsAfterFirst);
        assertThat(rows.count("memberships")).isEqualTo(membershipsAfterFirst);
        assertThat(secrets.accountNames(AuthSubstrate.ENVIRONMENT)).hasSize(secretsAfterFirst);
    }

    /**
     * Служебная идентичность кластера пользователя не несёт вовсе: по ней
     * тенант завёлся бы на каждый сервис. Красное — форма тела: отказ
     * идёт {@code ResponseStatusException}, которого не ловит ни один
     * обработчик сервиса.
     */
    @Test
    @Tag("debt")
    @DisplayName("B1.3 — токен служебной идентичности тенанта не заводит")
    void b1_3_aServiceIdentityTokenProvisionsNothing() {
        Long tenantsBefore = rows.count("tenants");
        Long membershipsBefore = rows.count("memberships");
        Integer secretsBefore = secrets.accountNames(AuthSubstrate.ENVIRONMENT).size();
        String token = identity.token(identity.builder()
                .subject("user-3")
                .authorizedParty(IdentityStub.SERVICE_CLIENT_ID));

        Answer answer = post(MEMBERSHIPS_SELF, token, "");

        assertThat(answer.status()).isEqualTo(403);
        assertThat(answer.carriesErrorDto()).isTrue();
        assertThat(rows.countWhere("memberships", "user_id", "user-3")).isZero();
        assertThat(rows.count("tenants")).isEqualTo(tenantsBefore);
        assertThat(rows.count("memberships")).isEqualTo(membershipsBefore);
        assertThat(secrets.accountNames(AuthSubstrate.ENVIRONMENT)).hasSize(secretsBefore);
    }

    @Test
    @DisplayName("B1.5 — имя тенанта берётся из идентификатора, когда имени нет")
    void b1_5_theTenantNameFallsBackToTheIdentifier() {
        String token = identity.browserToken("user-7");

        Answer answer = post(MEMBERSHIPS_SELF, token, "");

        assertThat(answer.status()).isEqualTo(200);
        Map<String, Object> membership = answer.single();
        assertThat(membership.get("role")).isEqualTo("OWNER");
        Map<String, Object> tenant = rows.row("tenants", "internal_id",
                String.valueOf(membership.get("tenantId")));
        assertThat(tenant.get("name")).isEqualTo("user-7");
    }

    @Test
    @Tag("debt")
    @DisplayName("B1.6 — вызов без предъявленного принципала отвечает 401 единым error-DTO")
    void b1_6_aCallWithoutAPrincipalIsRefused() {
        Long tenantsBefore = rows.count("tenants");
        Long membershipsBefore = rows.count("memberships");

        Answer answer = post(MEMBERSHIPS_SELF, "");

        assertThat(answer.status()).isEqualTo(401);
        assertThat(answer.carriesErrorDto()).isTrue();
        assertThat(rows.count("tenants")).isEqualTo(tenantsBefore);
        assertThat(rows.count("memberships")).isEqualTo(membershipsBefore);
    }

    @Test
    @Tag("debt")
    @DisplayName("B1.7 — токен, подписанный чужим ключом, отвечает 401 единым error-DTO")
    void b1_7_aTokenSignedByAnUnknownKeyIsRefused() {
        Long tenantsBefore = rows.count("tenants");
        Long membershipsBefore = rows.count("memberships");
        String token = identity.token(identity.builder()
                .subject("user-8")
                .keyId(IdentityStub.UNPUBLISHED_KEY_ID));

        Answer answer = post(MEMBERSHIPS_SELF, token, "");

        assertThat(answer.status()).isEqualTo(401);
        assertThat(answer.carriesErrorDto()).isTrue();
        assertThat(rows.count("tenants")).isEqualTo(tenantsBefore);
        assertThat(rows.count("memberships")).isEqualTo(membershipsBefore);
    }

    @Test
    @Tag("debt")
    @DisplayName("B1.8 — просроченный токен отвечает 401 единым error-DTO")
    void b1_8_anExpiredTokenIsRefused() {
        Long tenantsBefore = rows.count("tenants");
        Long membershipsBefore = rows.count("memberships");
        String token = identity.token(identity.builder()
                .subject("user-10")
                .expiresAt(Instant.now().minus(10, ChronoUnit.MINUTES)));

        Answer answer = post(MEMBERSHIPS_SELF, token, "");

        assertThat(answer.status()).isEqualTo(401);
        assertThat(answer.carriesErrorDto()).isTrue();
        assertThat(rows.count("tenants")).isEqualTo(tenantsBefore);
        assertThat(rows.count("memberships")).isEqualTo(membershipsBefore);
    }

    @Test
    @Tag("debt")
    @DisplayName("B1.9 — токен чужого издателя отвечает 401 единым error-DTO")
    void b1_9_aTokenOfAnotherIssuerIsRefused() {
        Long tenantsBefore = rows.count("tenants");
        Long membershipsBefore = rows.count("memberships");
        String token = identity.token(identity.builder()
                .subject("user-11")
                .issuer("https://another-issuer.example"));

        Answer answer = post(MEMBERSHIPS_SELF, token, "");

        assertThat(answer.status()).isEqualTo(401);
        assertThat(answer.carriesErrorDto()).isTrue();
        assertThat(rows.count("tenants")).isEqualTo(tenantsBefore);
        assertThat(rows.count("memberships")).isEqualTo(membershipsBefore);
    }

    @Test
    @DisplayName("B1.10 — второй пользователь получает своего тенанта")
    void b1_10_aSecondUserGetsItsOwnTenant() {
        Map<String, Object> first = post(MEMBERSHIPS_SELF, identity.browserToken("user-12"), "").single();
        Long tenantsBefore = rows.count("tenants");
        Long membershipsBefore = rows.count("memberships");

        Answer answer = post(MEMBERSHIPS_SELF, identity.browserToken("user-13"), "");

        assertThat(answer.status()).isEqualTo(200);
        Map<String, Object> second = answer.single();
        assertThat(second.get("tenantId")).isNotEqualTo(first.get("tenantId"));
        assertThat(rows.count("tenants")).isEqualTo(tenantsBefore + 1);
        assertThat(rows.count("memberships")).isEqualTo(membershipsBefore + 1);
        assertThat(rows.rowsWhere("memberships", "tenant_id", String.valueOf(first.get("tenantId"))))
                .singleElement()
                .satisfies(row -> assertThat(row.get("role")).isEqualTo("OWNER"));
        assertThat(rows.rowsWhere("memberships", "tenant_id", String.valueOf(second.get("tenantId"))))
                .singleElement()
                .satisfies(row -> assertThat(row.get("role")).isEqualTo("OWNER"));
    }

    @Test
    @DisplayName("B1.11 — наружу уходит идентичность, а не ключ базы")
    void b1_11_theBodyCarriesIdentityAndNotTheDatabaseKey() {
        Answer answer = post(MEMBERSHIPS_SELF, identity.browserToken("user-14"), "");

        Map<String, Object> membership = answer.single();
        assertThat(membership).containsOnlyKeys("internalId", "tenantId", "role");
        Map<String, Object> tenant = rows.row("tenants", "internal_id",
                String.valueOf(membership.get("tenantId")));
        assertThat(String.valueOf(membership.get("tenantId"))).isEqualTo(tenant.get("internal_id"));
        Map<String, Object> stored = rows.row("memberships", "user_id", "user-14");
        assertThat(membership.values())
                .doesNotContain(String.valueOf(tenant.get("id")), String.valueOf(stored.get("id")));
    }

    @Test
    @Tag("debt")
    @DisplayName("B1.12 — токен без claim `azp` отвечает 403 единым error-DTO")
    void b1_12_aTokenWithoutTheAuthorizedPartyIsRefused() {
        Long tenantsBefore = rows.count("tenants");
        Long membershipsBefore = rows.count("memberships");
        String token = identity.token(identity.builder().subject("user-15").noAuthorizedParty());

        Answer answer = post(MEMBERSHIPS_SELF, token, "");

        assertThat(answer.status()).isEqualTo(403);
        assertThat(answer.carriesErrorDto()).isTrue();
        assertThat(rows.count("tenants")).isEqualTo(tenantsBefore);
        assertThat(rows.count("memberships")).isEqualTo(membershipsBefore);
    }

    /**
     * Ни тенанта, ни владельца: заведение идёт одной транзакцией, и
     * «тенант есть, владельца нет» невозможно по построению исполнителя.
     * Красное — форма тела: последнего обработчика у сервиса нет вовсе.
     */
    @Test
    @Tag("debt")
    @DisplayName("B1.13 — токен без claim `sub` не заводит ни тенанта, ни членства")
    void b1_13_aTokenWithoutASubjectProvisionsNothing() {
        Long tenantsBefore = rows.count("tenants");
        Long membershipsBefore = rows.count("memberships");
        String token = identity.token(identity.builder().noSubject().preferredUsername("user-9"));

        Answer answer = post(MEMBERSHIPS_SELF, token, "");

        assertThat(answer.status()).isNotEqualTo(200);
        assertThat(rows.count("tenants")).isEqualTo(tenantsBefore);
        assertThat(rows.count("memberships")).isEqualTo(membershipsBefore);
        assertThat(answer.carriesErrorDto()).isTrue();
        assertThat(answer.body()).doesNotContain("Exception", "NullPointer");
    }

    @Test
    @Tag("debt")
    @DisplayName("B1.14 — токен с чужим значением заголовка `typ` отвечает 401 единым error-DTO")
    void b1_14_aTokenWithAForeignTypeHeaderIsRefused() {
        Long tenantsBefore = rows.count("tenants");
        Long membershipsBefore = rows.count("memberships");
        String token = identity.token(identity.builder().subject("user-16").type("x-not-jwt"));

        Answer answer = post(MEMBERSHIPS_SELF, token, "");

        assertThat(answer.status()).isEqualTo(401);
        assertThat(answer.carriesErrorDto()).isTrue();
        assertThat(rows.count("tenants")).isEqualTo(tenantsBefore);
        assertThat(rows.count("memberships")).isEqualTo(membershipsBefore);
    }

    /**
     * Разогрев делает сам кейс: без него ленивую сборку декодера уронил бы
     * {@code SupplierJwtDecoder}, то есть отказ производило бы другое
     * звено тропы (`.claude/tests/cases/auth.md` §«Чем достаются выходы»).
     * Неизвестный кэшу {@code kid} — единственный вход, заставляющий
     * декодер пойти за набором ключей заново.
     *
     * <p>Стаб общий на прогон, поэтому JWKS-точка возвращается в рабочее
     * состояние тем же кейсом: оставленный сломанным, стаб красил бы
     * соседей.
     */
    @Test
    @DisplayName("B1.15 — ключи провайдера недостижимы при разборе токена")
    void b1_15_theProviderKeysAreUnreachable() {
        post(MEMBERSHIPS_SELF, identity.browserToken("user-17"), "");
        Long tenantsBefore = rows.count("tenants");
        Long membershipsBefore = rows.count("memberships");
        String token = identity.token(identity.builder().subject("user-18").keyId("K3"));

        Answer answer;
        identity.breakKeySet();
        try {
            answer = post(MEMBERSHIPS_SELF, token, "");
        } finally {
            identity.healKeySet();
        }

        assertThat(answer.status()).isNotEqualTo(200);
        assertThat(rows.countWhere("memberships", "user_id", "user-18")).isZero();
        assertThat(rows.count("tenants")).isEqualTo(tenantsBefore);
        assertThat(rows.count("memberships")).isEqualTo(membershipsBefore);
    }

    /**
     * Пробел {@code G1} документа, добранный под-шагом 3: негодное
     * ЗНАЧЕНИЕ заголовка предъявления и положительная сторона того же
     * предиката — схема {@code bearer} строчными. Носитель ожидания —
     * исходники {@code DefaultBearerTokenResolver}; дом контракта
     * различия двух отказов не несёт, поэтому мерится исход поверхности,
     * а не параметр {@code error_description}.
     */
    @Test
    @DisplayName("G1 — негодное значение заголовка предъявления отказывает без заведения")
    void g1_aMalformedAuthorizationHeaderValueIsRefused() {
        Long tenantsBefore = rows.count("tenants");
        Long membershipsBefore = rows.count("memberships");

        Answer answer = getWithRawAuthorization(EXCHANGE_ACCOUNTS, "Bearer not a token");

        assertThat(answer.status()).isEqualTo(401);
        assertThat(rows.count("tenants")).isEqualTo(tenantsBefore);
        assertThat(rows.count("memberships")).isEqualTo(membershipsBefore);
    }

    /** Вторая половина того же предиката: схему резолвер читает без учёта регистра. */
    @Test
    @DisplayName("G1 — схема предъявления строчными принимается")
    void g1_aLowercaseBearerSchemeIsAccepted() {
        String token = identity.browserToken("user-19");

        Answer answer = getWithRawAuthorization(EXCHANGE_ACCOUNTS, "bearer " + token);

        assertThat(answer.status()).isEqualTo(200);
        List<Map<String, Object>> registry = answer.asList();
        assertThat(registry).isNotNull();
    }
}
