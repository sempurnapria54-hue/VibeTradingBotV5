package com.example.auth.box;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.util.ExchangeAccountSecretFields;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Производственный перечень контуров — клетка {@code B6.2} документа
 * `.claude/tests/cases/auth.md`.
 *
 * <p><b>Обе оси окружения сдвинуты вместе, и это не удобство:</b> первый
 * сегмент пути ключей выводится из имени окружения, поэтому «боевой счёт
 * допущен» и «ключи легли под `prod/`» — одно и то же положение осей
 * (`docs/spec/environment-contour.json`, величина {@code contourAdmitted};
 * `docs/architecture/platform.md` §Безопасность).
 */
class ProductionContoursBoxTest extends AuthBox {

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        AuthSubstrate.register(registry, Map.of(
                "platform.environment.name", AuthSubstrate.PRODUCTION_ENVIRONMENT,
                "platform.environment.admitted-contours", "LIVE,DEMO"));
    }

    @Test
    @DisplayName("B6.2 — производственный перечень контуров допускает боевой счёт")
    void b6_2_theProductionContourListAdmitsALiveAccount() {
        String tenant = provisionTenant("user-b6-2");

        Answer answer = post(EXCHANGE_ACCOUNTS, identity.browserToken("user-b6-2"),
                Bodies.registration(tenant).with("contour", "LIVE").body());

        assertThat(answer.status()).isEqualTo(201);
        String internalId = String.valueOf(answer.asObject().get("internalId"));
        assertThat(answer.asObject().get("contour")).isEqualTo("LIVE");
        assertThat(rows.row("exchange_accounts", "internal_id", internalId).get("contour"))
                .isEqualTo("LIVE");
        assertThat(secrets.account(AuthSubstrate.PRODUCTION_ENVIRONMENT, internalId))
                .containsEntry(ExchangeAccountSecretFields.CONTOUR, "LIVE");
        assertThat(secrets.account(AuthSubstrate.ENVIRONMENT, internalId)).isEmpty();
    }
}
