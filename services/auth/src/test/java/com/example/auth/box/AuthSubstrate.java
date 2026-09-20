package com.example.auth.box;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.vault.VaultContainer;

/**
 * Субстрат чёрного ящика `auth`: контейнер базы, контейнер хранилища
 * секретов и стаб провайдера идентичности
 * (.claude/decisions/test-contour-design-pass.md, решения 2, 3, 5).
 *
 * <p><b>Субстрат общий на прогон, а не на кейс:</b> подъём контейнера
 * стои́т секунды, и платить их за каждую клетку незачем. Свой экземпляр
 * берут ровно те кейсы, у которых на это есть одно из двух оснований —
 * кейс лишает соседей адреса либо предмет кейса есть состояние субстрата
 * целиком (там же, §«Оснований брать свой контейнер ДВА…»); у `auth`
 * таких два — {@code B2.17} и {@code B3.2}, и каждый заводит контейнер
 * сам.
 *
 * <p><b>Образ базы — тот же, что в {@code deploy/base}</b>, и совпадение
 * тега сверяет проба ({@link SubstrateImagePinTest}): две записи тега
 * разошлись бы молча, а стоковый {@code postgres} не поднял бы
 * гипертаблицы соседних сервисов вовсе.
 *
 * <p><b>Адрес хранилища пришпиливается ЦЕЛИКОМ</b> — сверх {@code uri}
 * задаются схема, хост и порт заведомо мёртвого адреса (решение 5): при
 * пустом {@code uri} клиент собирает адрес из них, а умолчания у них
 * рабочие, и без пришпиливания прогон писал бы ключи в живое хранилище
 * машины держателя.
 */
final class AuthSubstrate {

    /**
     * Образ базы: тот же тег, что у стенда
     * ({@code deploy/base/data/postgres-cluster.yaml}).
     */
    static final String DATABASE_IMAGE = "timescale/timescaledb-ha:pg17.10-ts2.29.2";

    /** Образ хранилища секретов: тот же, что в локальном контуре данных. */
    static final String VAULT_IMAGE = "hashicorp/vault:1.15";

    /** Корневой токен dev-режима: им ящик и пишет ключи. */
    static final String ROOT_TOKEN = "box-root-token";

    /**
     * Токен, у которого на префиксе счетов есть только чтение, — вход
     * кейса {@code B2.9}. Идентификатор задан явно, поэтому значение
     * известно до прогона и не добывается разбором вывода.
     */
    static final String READ_ONLY_TOKEN = "box-reader-token";

    /** Имя окружения штатного прогона: первый сегмент пути ключей. */
    static final String ENVIRONMENT = "dev";

    /** Имя производственного окружения: второй смонтированный префикс. */
    static final String PRODUCTION_ENVIRONMENT = "prod";

    private static final PostgreSQLContainer DATABASE = startDatabase();

    private static final VaultContainer<?> VAULT = startVault();

    private AuthSubstrate() {
    }

    /** Контейнер базы общего субстрата. */
    static PostgreSQLContainer database() {
        return DATABASE;
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
        values.put("spring.datasource.url", DATABASE.getJdbcUrl());
        values.put("spring.datasource.username", DATABASE.getUsername());
        values.put("spring.datasource.password", DATABASE.getPassword());
        values.put("spring.security.oauth2.resourceserver.jwt.issuer-uri", IdentityStub.stub().issuer());
        values.put("spring.cloud.vault.uri", VAULT.getHttpHostAddress());
        values.put("spring.cloud.vault.authentication", "TOKEN");
        values.put("spring.cloud.vault.token", ROOT_TOKEN);
        values.putAll(deadVaultAddress());
        values.put("platform.identity.browser-client-id", IdentityStub.BROWSER_CLIENT_ID);
        values.put("platform.environment.name", ENVIRONMENT);
        values.put("platform.environment.admitted-contours", "DEMO");
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

    private static PostgreSQLContainer startDatabase() {
        PostgreSQLContainer container = new PostgreSQLContainer(
                DockerImageName.parse(DATABASE_IMAGE).asCompatibleSubstituteFor("postgres"))
                .withDatabaseName("auth")
                .withUsername("auth")
                .withPassword("auth");
        container.start();
        return container;
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
     */
    private static VaultContainer<?> startVault() {
        VaultContainer<?> container = new VaultContainer<>(DockerImageName.parse(VAULT_IMAGE))
                .withVaultToken(ROOT_TOKEN)
                .withInitCommand("secrets enable -path=" + ENVIRONMENT + " -version=1 kv")
                .withInitCommand("secrets enable -path=" + PRODUCTION_ENVIRONMENT + " -version=1 kv");
        container.start();
        grantReadOnlyToken(container);
        return container;
    }

    /**
     * Заводит политику «только чтение ключей счёта» и токен под ней: им
     * ходит контекст кейса {@code B2.9}, где хранилище живо, а права
     * записи у прогона нет.
     */
    /**
     * Возвращает тому же токену право записи: им проверяется вторая
     * половина кейса {@code B2.9} — «повторная регистрация после возврата
     * права проходит штатно». Политика правится на месте, поэтому токен
     * остаётся тем же и контекста перевыпускать не нужно.
     */
    static void allowReadOnlyTokenToWrite() {
        execute(VAULT, "printf 'path \"" + ENVIRONMENT + "/exchange-accounts/*\" "
                + "{ capabilities = [\"create\", \"update\", \"read\"] }\\n' "
                + "| vault policy write account-reader -");
    }

    private static void grantReadOnlyToken(VaultContainer<?> container) {
        execute(container, "printf 'path \"" + ENVIRONMENT + "/exchange-accounts/*\" "
                + "{ capabilities = [\"read\"] }\\n' | vault policy write account-reader -");
        execute(container, "vault token create -policy=account-reader -id=" + READ_ONLY_TOKEN);
    }

    private static void execute(VaultContainer<?> container, String command) {
        try {
            Container.ExecResult result = container.execInContainer("sh", "-c", command);
            if (result.getExitCode() != 0) {
                throw new IllegalStateException("Хранилище не приняло команду подготовки: " + command
                        + "; вывод: " + result.getStdout() + result.getStderr());
            }
        } catch (IOException | InterruptedException failure) {
            throw new IllegalStateException("Хранилище недоступно при подготовке: " + command, failure);
        }
    }
}
