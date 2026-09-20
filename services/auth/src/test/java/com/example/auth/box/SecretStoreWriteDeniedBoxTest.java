package com.example.auth.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Хранилище отвергает запись правами — клетка {@code B2.9} документа
 * `.claude/tests/cases/auth.md`.
 *
 * <p><b>Хранилище при этом ЖИВО и содержимого не теряет:</b> отбираются
 * права, а не адрес, — поэтому общий контейнер кейсу годится, и своего он
 * не берёт (`.claude/decisions/test-contour-design-pass.md` §«Кейс,
 * разрушающий субстрат, берёт свой контейнер и свой контекст»). Свой у
 * кейса контекст: токен прогона есть ось конфигурации.
 *
 * <p><b>Порядок записи объявлен несущим:</b> ключи пишутся ПОСЛЕДНИМ
 * действием и внутри транзакции, поэтому отказ хранилища откатывает
 * строку счёта — счёта, для которого нечем подписать запрос, не остаётся.
 * Вторая половина клетки — возврат права: та же тропа тем же токеном
 * проходит штатно и прибавляет ровно одну строку.
 */
class SecretStoreWriteDeniedBoxTest extends AuthBox {

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        AuthSubstrate.register(registry,
                Map.of("spring.cloud.vault.token", AuthSubstrate.READ_ONLY_TOKEN));
    }

    @Test
    @Tag("debt")
    @DisplayName("B2.9 — хранилище секретов отвергает запись правами")
    void b2_9_theSecretStoreRefusesTheWriteByRights() {
        String tenant = provisionTenant("user-b2-9");
        Long accountsBefore = rows.count("exchange_accounts");

        Answer refused = post(EXCHANGE_ACCOUNTS, identity.browserToken("user-b2-9"),
                Bodies.registration(tenant).body());

        assertThat(refused.status()).isEqualTo(500);
        assertThat(rows.count("exchange_accounts")).isEqualTo(accountsBefore);
        assertThat(rows.row("tenants", "internal_id", tenant).get("status")).isEqualTo("ACTIVE");

        AuthSubstrate.allowReadOnlyTokenToWrite();
        Answer accepted = post(EXCHANGE_ACCOUNTS, identity.browserToken("user-b2-9"),
                Bodies.registration(tenant).body());

        assertThat(accepted.status()).isEqualTo(201);
        assertThat(rows.count("exchange_accounts")).isEqualTo(accountsBefore + 1);
        assertThat(refused.carriesErrorDto()).isTrue();
        assertThat(refused.body()).doesNotContain("VaultException", "permission denied");
    }
}
