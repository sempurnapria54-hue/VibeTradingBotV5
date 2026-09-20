package com.example.auth.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Ненастроенный адрес хранилища секретов — клетка {@code B6.4} документа
 * `.claude/tests/cases/auth.md`.
 *
 * <p><b>Пришпиливание адреса — ПРЕДУСЛОВИЕ, а не ассерт.</b> При пустом
 * {@code uri} клиент собирает адрес из {@code scheme}, {@code host} и
 * {@code port}, а умолчания у них рабочие; без пришпиливания прогон
 * зависел бы от того, что слушает на машине, — у держателя там живое
 * хранилище с боевыми demo-кредами
 * (.claude/decisions/test-contour-design-pass.md §«Адрес хранилища
 * пришпиливается целиком»).
 *
 * <p><b>Имя окружения задано, и это несущее:</b> писатель ключей
 * проверяет его ДО всякого обращения к клиенту хранилища, и с пустым
 * именем тропа падала бы на предикате имени, ни разу не дойдя до адреса,
 * — предмета оси у клетки не осталось бы вовсе.
 *
 * <p><b>Чем именно отказывает — не выведено ни одним домом</b> (находка
 * {@code F-8}): подъёмом контекста либо регистрацией. Клетка проверяет
 * то, что дом несёт: отсутствие успеха и отсутствие записи.
 */
class UnconfiguredSecretStoreAddressBoxTest extends AuthBox {

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        AuthSubstrate.register(registry, Map.of("spring.cloud.vault.uri", ""));
    }

    @Test
    @DisplayName("B6.4 — ненастроенный адрес хранилища секретов не пускает ключи наружу")
    void b6_4_anUnconfiguredSecretStoreAddressLetsNoKeysOut() {
        String tenant = provisionTenant("user-b6-4");
        Long accountsBefore = rows.count("exchange_accounts");
        Integer secretsBefore = secrets.accountNames(AuthSubstrate.ENVIRONMENT).size();

        Answer answer = post(EXCHANGE_ACCOUNTS, identity.browserToken("user-b6-4"),
                Bodies.registration(tenant).body());

        assertThat(answer.status()).isNotEqualTo(201);
        assertThat(rows.count("exchange_accounts")).isEqualTo(accountsBefore);
        assertThat(secrets.accountNames(AuthSubstrate.ENVIRONMENT)).hasSize(secretsBefore);
    }
}
