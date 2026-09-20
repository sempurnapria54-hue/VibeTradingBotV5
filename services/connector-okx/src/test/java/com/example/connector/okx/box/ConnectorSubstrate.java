package com.example.connector.okx.box;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.Container;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.vault.VaultContainer;

/**
 * Субстрат чёрного ящика `connector-okx`: контейнер хранилища секретов,
 * стаб площадки, стаб провайдера идентичности и стаб поверхности соседа
 * (.claude/decisions/test-contour-design-pass.md, решения 3, 4, 5).
 *
 * <p><b>Контейнера базы здесь нет, и это предмет клетки, а не экономия.</b>
 * Коннектор стейтлесс: таблиц у него нет ни одной, и {@code B10.1} стои́т
 * ровно на том, что весь набор троп проходит при не поднятой СУБД. Подними
 * прогон базу «на всякий случай» — и клетка перестала бы что-либо
 * утверждать.
 *
 * <p><b>Субстрат общий на прогон, а не на кейс:</b> подъём контейнера
 * стои́т секунды, и платить их за каждую клетку незачем. Свой экземпляр
 * берут ровно те кейсы, у которых на это есть одно из двух оснований —
 * кейс лишает соседей адреса либо предмет кейса есть состояние субстрата
 * целиком (там же, §«Оснований брать свой контейнер ДВА…»); у коннектора
 * такой один — {@code B1.7}, и контейнер он заводит сам.
 *
 * <p><b>Чтения хранилища наблюдаются его собственным журналом аудита.</b>
 * Клетки {@code B1.1}, {@code B1.8}, {@code B1.9}, {@code B1.11} и
 * {@code B6.1} утверждают о ЧИСЛЕ походов за ключами, а изнутри процесса
 * это число не наблюдаемо ничем, кроме подменённого бина — то есть ровно
 * тем, чего ящик не делает. Журнал аудита Vault пишет сама площадка
 * хранилища, и он виден снаружи: путь запроса в нём лежит открытым текстом,
 * значения — под HMAC.
 *
 * <p><b>Адрес хранилища пришпиливается ЦЕЛИКОМ</b> — сверх {@code uri}
 * задаются схема, хост и порт заведомо мёртвого адреса (решение 5): при
 * пустом {@code uri} клиент собирает адрес из них, а умолчания у них
 * рабочие, и без пришпиливания прогон читал бы ключи из живого хранилища
 * машины держателя.
 */
final class ConnectorSubstrate {

    /** Образ хранилища секретов: тот же, что в локальном контуре данных. */
    static final String VAULT_IMAGE = "hashicorp/vault:1.15";

    /** Корневой токен dev-режима: им ящик и читает ключи. */
    static final String ROOT_TOKEN = "box-root-token";

    /** Имя окружения штатного прогона: первый сегмент пути ключей. */
    static final String ENVIRONMENT = "dev";

    /** Файл журнала аудита внутри контейнера хранилища. */
    static final String AUDIT_LOG = "/tmp/vault-audit.log";

    private static final VaultContainer<?> VAULT = startVault();

    private ConnectorSubstrate() {
    }

    /** Контейнер хранилища общего субстрата. */
    static VaultContainer<?> vault() {
        return VAULT;
    }

    /**
     * Свойства контекста ящика: адреса субстрата плюс оси окружения.
     *
     * <p><b>Перечень собирается один раз и целиком</b>, а переопределения
     * накладываются на него до регистрации: два {@code add} по одному
     * ключу оставляли бы исход зависящим от порядка обхода, которого
     * контракт реестра не обещает.
     *
     * @param registry  реестр свойств контекста
     * @param overrides оси, которые кейс сдвигает
     */
    static void register(DynamicPropertyRegistry registry, Map<String, String> overrides) {
        Map<String, String> values = new LinkedHashMap<>(defaults());
        values.putAll(overrides);
        values.forEach((key, value) -> registry.add(key, () -> value));
    }

    /** Штатное положение всех осей контекста. */
    static Map<String, String> defaults() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("spring.security.oauth2.resourceserver.jwt.issuer-uri", IdentityStub.stub().issuer());
        values.put("spring.cloud.vault.uri", VAULT.getHttpHostAddress());
        values.put("spring.cloud.vault.authentication", "TOKEN");
        values.put("spring.cloud.vault.token", ROOT_TOKEN);
        values.putAll(deadVaultAddress());
        values.put("platform.environment.name", ENVIRONMENT);
        values.put("okx.base-url", ExchangeStub.stub().baseUrl());
        // Срок заметно больше длительности кейса: клетки кэша управляют им
        // как входом, а прочие не должны зависеть от того, сколько прогон шёл.
        values.put("credentials.cache-ttl", "10m");
        return values;
    }

    /**
     * Заведомо мёртвый адрес хранилища: три оси, из которых клиент
     * собирает адрес при пустом {@code uri}.
     */
    static Map<String, String> deadVaultAddress() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("spring.cloud.vault.scheme", "https");
        values.put("spring.cloud.vault.host", "127.0.0.1");
        values.put("spring.cloud.vault.port", "1");
        return values;
    }

    /**
     * Хранилище dev-режима с token-аутентификацией: Kubernetes-грант
     * требует кластера, которого у прогона нет (решение 5).
     *
     * <p>KV монтируется ПО ИМЕНИ ОКРУЖЕНИЯ и первой версией — ровно так,
     * как на стенде ({@code tools/stand/vault-setup.sh}): сервисы
     * адресуют ключи путём {@code <окружение>/exchange-accounts/<id>}, а
     * у второй версии адрес несёт вставку {@code data/}, которой в форме
     * пути нет.
     *
     * <p>Журнал аудита включается тем же ходом: он и есть наблюдатель числа
     * походов за ключами.
     */
    private static VaultContainer<?> startVault() {
        VaultContainer<?> container = new VaultContainer<>(DockerImageName.parse(VAULT_IMAGE))
                .withVaultToken(ROOT_TOKEN)
                .withInitCommand("secrets enable -path=" + ENVIRONMENT + " -version=1 kv")
                .withInitCommand("audit enable -path=box file file_path=" + AUDIT_LOG);
        container.start();
        return container;
    }

    /** Журнал аудита хранилища целиком; пустая строка, когда записей ещё нет. */
    static String auditLog() {
        try {
            Container.ExecResult result = VAULT.execInContainer("sh", "-c", "cat " + AUDIT_LOG + " 2>/dev/null");
            return result.getStdout();
        } catch (IOException | InterruptedException failure) {
            throw new IllegalStateException("Журнал аудита хранилища недоступен", failure);
        }
    }
}
