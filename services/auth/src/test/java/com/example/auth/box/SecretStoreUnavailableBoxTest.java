package com.example.auth.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.vault.VaultContainer;

/**
 * Хранилище секретов недоступно — клетка {@code B2.17} документа
 * `.claude/tests/cases/auth.md`.
 *
 * <p><b>Контейнер у кейса СВОЙ по первому основанию: кейс лишает соседей
 * адреса.</b> Остановленный общий контейнер был бы мёртв соседям до конца
 * прогона, а поднятый заново опубликовал бы НОВЫЙ порт хоста, которого их
 * контекст не знает, — «восстановление» вернуло бы им контейнер, к
 * которому никто не подключён
 * (.claude/decisions/test-contour-design-pass.md §«Кейс, разрушающий
 * субстрат, берёт свой контейнер и свой контекст»). Поэтому
 * восстанавливать здесь нечего.
 *
 * <p><b>С {@code B2.9} клетка разведена КЛАССОМ ОТКАЗА, а не состоянием
 * контейнера:</b> там хранилище отвечает отказом в правах, и клиент
 * переводит ответ в {@code VaultException}; здесь ответа нет вовсе, и
 * отказ соединения уезжает мимо этого перевода другим классом. Регресс,
 * который ловит именно эта клетка: последний обработчик, написанный под
 * класс отказа ОТВЕТА, отказ СОЕДИНЕНИЯ не накрывает.
 *
 * <p><b>Адрес снимается с ЖИВОГО контейнера, и лишь затем контейнер
 * останавливается:</b> контекст обязан получить адрес, по которому никто
 * не отвечает, — отсутствующий адрес есть другой вход (клетка
 * {@code B6.4}).
 */
class SecretStoreUnavailableBoxTest extends AuthBox {

    private static final String DEAD_ADDRESS = startAndStop();

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        AuthSubstrate.register(registry, Map.of("spring.cloud.vault.uri", DEAD_ADDRESS));
    }

    @Test
    @DisplayName("B2.17 — хранилище секретов недоступно")
    void b2_17_theSecretStoreIsUnavailable() {
        String tenant = provisionTenant("user-b2-17");
        Long accountsBefore = rows.count("exchange_accounts");

        Answer answer = post(EXCHANGE_ACCOUNTS, identity.browserToken("user-b2-17"),
                Bodies.registration(tenant).body());

        assertThat(answer.status()).isEqualTo(500);
        assertThat(rows.count("exchange_accounts")).isEqualTo(accountsBefore);
        assertThat(rows.row("tenants", "internal_id", tenant).get("status")).isEqualTo("ACTIVE");
        assertThat(answer.carriesErrorDto()).isTrue();
        assertThat(answer.body()).doesNotContain("Exception", "Connection refused");
    }

    private static String startAndStop() {
        VaultContainer<?> container = new VaultContainer<>(
                DockerImageName.parse(AuthSubstrate.VAULT_IMAGE))
                .withVaultToken(AuthSubstrate.ROOT_TOKEN);
        container.start();
        String address = container.getHttpHostAddress();
        container.stop();
        return address;
    }
}
