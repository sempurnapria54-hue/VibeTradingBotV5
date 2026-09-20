package com.example.auth.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Незаданный перечень допущенных контуров — клетка {@code B2.3} документа
 * `.claude/tests/cases/auth.md`.
 *
 * <p><b>Пустой перечень — не «допустимы все», а отказ:</b> окружение без
 * конфигурации счетов не заводит, иначе оно торговало бы боевыми деньгами
 * по умолчанию (`docs/concept.md` П1, `docs/spec/environment-contour.json`).
 */
class UnconfiguredContoursBoxTest extends AuthBox {

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        AuthSubstrate.register(registry, Map.of("platform.environment.admitted-contours", ""));
    }

    @Test
    @DisplayName("B2.3 — незаданный перечень контуров отвергает любой счёт")
    void b2_3_anUnconfiguredContourListRefusesEveryAccount() {
        String tenant = provisionTenant("user-b2-3");
        Long accountsBefore = rows.count("exchange_accounts");
        Integer secretsBefore = secrets.accountNames(AuthSubstrate.ENVIRONMENT).size();

        Answer answer = post(EXCHANGE_ACCOUNTS, identity.browserToken("user-b2-3"),
                Bodies.registration(tenant).body());

        assertThat(answer.status()).isEqualTo(422);
        assertThat(answer.errorCode()).isEqualTo("CONTOUR_NOT_ADMITTED");
        assertThat(rows.count("exchange_accounts")).isEqualTo(accountsBefore);
        assertThat(secrets.accountNames(AuthSubstrate.ENVIRONMENT)).hasSize(secretsBefore);
    }
}
