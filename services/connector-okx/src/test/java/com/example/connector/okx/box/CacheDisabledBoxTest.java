package com.example.connector.okx.box;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.connector.okx.util.OkxConstants;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Срок кэша ключей выключен нулём — клетка {@code B1.9} документа
 * `.claude/tests/cases/connector-okx.md`.
 *
 * <p><b>Свой класс, потому что срок кэша есть ось КОНТЕКСТА.</b> Сдвинуть
 * её внутри класса нельзя: свойства входят в ключ кэша контекста, и
 * положение осей окружения здесь — вход ящика, а не состояние субстрата.
 *
 * <p><b>Ноль выключает кэш, а не «почти выключает».</b> Различие счётно:
 * при «почти» второй вызов подряд успел бы попасть в живую запись, и
 * ротация ключей вступала бы в силу с задержкой, которой никто не объявлял.
 */
class CacheDisabledBoxTest extends ConnectorBox {

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        ConnectorSubstrate.register(registry, Map.of("credentials.cache-ttl", "0"));
    }

    @Test
    @DisplayName("B1.9 — срок кэша выражен конфигурацией и выключается нулём")
    void b1_9_theCacheLifetimeIsConfiguredAndZeroTurnsItOff() {
        String account = "ACC-B1-9";
        secrets.put(account, "LIVE");
        exchange.answers(OkxConstants.ACCOUNT_POSITIONS_PATH, Okx.ok());
        Integer readsBefore = secrets.readsOf(account);

        assertThat(get(account(account, "/positions")).status()).isEqualTo(200);
        assertThat(get(account(account, "/positions")).status()).isEqualTo(200);

        assertThat(secrets.readsOf(account) - readsBefore).isEqualTo(2);
    }
}
