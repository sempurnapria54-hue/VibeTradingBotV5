package com.example.auth.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Незаданное имя окружения — клетка {@code B2.10} документа
 * `.claude/tests/cases/auth.md`.
 *
 * <p><b>Первый сегмент пути и есть граница окружений</b>
 * (`docs/architecture/platform.md` §Безопасность): без него ключи легли
 * бы в чужое окружение либо в корень хранилища, и граница исчезла бы.
 * Поэтому отрицание проверяется дважды — под префиксом счетов и в корне
 * СМОНТИРОВАННОГО префикса, — и обе половины разностные.
 *
 * <p>{@code IllegalStateException} поимённо не ловит ни один обработчик
 * `auth` — тело собирает последний.
 */
class UnnamedEnvironmentBoxTest extends AuthBox {

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        AuthSubstrate.register(registry, Map.of("platform.environment.name", ""));
    }

    @Test
    @DisplayName("B2.10 — незаданное имя окружения делает адрес ключей невычислимым")
    void b2_10_anUnnamedEnvironmentMakesTheKeyPathUncomputable() {
        String tenant = provisionTenant("user-b2-10");
        Long accountsBefore = rows.count("exchange_accounts");
        Integer underPrefixBefore = secrets.accountNames(AuthSubstrate.ENVIRONMENT).size();
        Integer atRootBefore = secrets.mountRootNames(AuthSubstrate.ENVIRONMENT).size();

        Answer answer = post(EXCHANGE_ACCOUNTS, identity.browserToken("user-b2-10"),
                Bodies.registration(tenant).body());

        assertThat(answer.status()).isEqualTo(500);
        assertThat(rows.count("exchange_accounts")).isEqualTo(accountsBefore);
        assertThat(secrets.accountNames(AuthSubstrate.ENVIRONMENT)).hasSize(underPrefixBefore);
        assertThat(secrets.mountRootNames(AuthSubstrate.ENVIRONMENT)).hasSize(atRootBefore);
        assertThat(answer.carriesErrorDto()).isTrue();
        assertThat(answer.body()).doesNotContain("IllegalStateException", "невычислим");
    }
}
