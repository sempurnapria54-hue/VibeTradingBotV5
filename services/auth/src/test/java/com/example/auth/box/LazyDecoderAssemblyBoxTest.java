package com.example.auth.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Пробел {@code G4} документа `.claude/tests/cases/auth.md`, добранный
 * под-шагом 3: отказ ЛЕНИВОЙ СБОРКИ декодера.
 *
 * <p><b>Своим контуром — по первому основанию, как {@code B2.17}:</b>
 * {@code SupplierJwtDecoder} производит свой отказ ровно тогда, когда
 * диспетчер OIDC недоступен на ПЕРВОМ токене контекста; после удачи
 * сборка закэширована, и на общем контексте вход недостижим по
 * построению.
 *
 * <p><b>Звено разведено с {@code B1.15} по производителю отказа:</b> там
 * сборка позади и отказывает добыча ключей, здесь не удаётся сама сборка.
 * Ожидание — по форме {@code B1.15}: успех недопустим, принципал не
 * принят, заведения нет; класс исхода читается, число пишет прогон.
 *
 * <p>Стаб общий на прогон, поэтому диспетчер возвращается в рабочее
 * состояние тем же кейсом.
 */
class LazyDecoderAssemblyBoxTest extends AuthBox {

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        AuthSubstrate.register(registry, Map.of());
    }

    @Test
    @DisplayName("G4 — отказ ленивой сборки декодера не пускает принципала")
    void g4_aFailedLazyDecoderAssemblyAdmitsNoPrincipal() {
        Long tenantsBefore = rows.count("tenants");
        Long membershipsBefore = rows.count("memberships");
        String token = identity.browserToken("user-g4");

        Answer answer;
        identity.breakDiscovery();
        try {
            answer = post(MEMBERSHIPS_SELF, token, "");
        } finally {
            identity.healDiscovery();
        }

        assertThat(answer.status()).isNotEqualTo(200);
        assertThat(rows.countWhere("memberships", "user_id", "user-g4")).isZero();
        assertThat(rows.count("tenants")).isEqualTo(tenantsBefore);
        assertThat(rows.count("memberships")).isEqualTo(membershipsBefore);
    }
}
