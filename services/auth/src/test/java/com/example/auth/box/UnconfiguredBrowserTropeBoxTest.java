package com.example.auth.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Ненастроенная браузерная тропа — клетка {@code B1.4} документа
 * `.claude/tests/cases/auth.md`.
 *
 * <p><b>Своим классом, потому что вход клетки — ОСЬ ОКРУЖЕНИЯ</b>, а не
 * claim токена: незаданный клиент браузера означает, что тропа заведения
 * не настроена, и тогда не проходит ни один токен — незаданное есть
 * отказ, а не разрешение (`docs/concept.md` П1).
 */
class UnconfiguredBrowserTropeBoxTest extends AuthBox {

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        AuthSubstrate.register(registry, Map.of("platform.identity.browser-client-id", ""));
    }

    @Test
    @DisplayName("B1.4 — ненастроенная браузерная тропа отказывает всем")
    void b1_4_anUnconfiguredBrowserTropeRefusesEveryone() {
        Long tenantsBefore = rows.count("tenants");
        Long membershipsBefore = rows.count("memberships");

        Answer answer = post(MEMBERSHIPS_SELF, identity.browserToken("user-4"), "");

        assertThat(answer.status()).isEqualTo(403);
        assertThat(rows.count("tenants")).isEqualTo(tenantsBefore);
        assertThat(rows.count("memberships")).isEqualTo(membershipsBefore);
    }
}
