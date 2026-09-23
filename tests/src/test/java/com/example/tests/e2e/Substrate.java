package com.example.tests.e2e;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.vault.VaultContainer;

/**
 * Субстрат сквозного набора: база, брокер и хранилище секретов —
 * контейнерами прогона (.claude/decisions/test-contour-design-pass.md,
 * решения 2 и 5).
 *
 * <p><b>База одна на прогон, а своя база у каждой стороны каждой тропы — в
 * ней.</b> Миграции накатывает сама сторона своим стартом, чужих таблиц ни
 * одна не трогает (docs/architecture/data-ownership.md §Раскладка), и
 * контейнер на сторону стоил бы секунды подъёма без единого различия.
 * Образ — тот же, что у стенда: у статистики гипертаблицы, а второй тег
 * ради прочих сторон расходился бы с первым молча.
 *
 * <p><b>Брокер — СВОЙ у каждой тропы.</b> Имена тем у производителей
 * зашиты в код ({@code strategies.facts}, {@code trading-core.facts}), и
 * развести две тропы на одном брокере можно было бы только их правкой.
 *
 * <p><b>Хранилище одно на прогон:</b> ключи счёта лежат по пути его
 * идентичности, и тропы с разными счетами друг другу не мешают.
 */
public final class Substrate {

    public static final String DATABASE_IMAGE = "timescale/timescaledb-ha:pg17.10-ts2.29.2";

    public static final String BROKER_IMAGE = "apache/kafka:4.1.1";

    public static final String VAULT_IMAGE = "hashicorp/vault:1.15";

    public static final String VAULT_TOKEN = "e2e-root-token";

    public static final String ENVIRONMENT = "dev";

    public static final String STRATEGY_TOPIC = "strategies.facts";

    public static final String CORE_TOPIC = "trading-core.facts";

    private static final String PRELOADED_LIBRARIES = "timescaledb,pg_textsearch,pg_stat_statements";

    private static final Duration ADMIN_TIMEOUT = Duration.ofSeconds(60);

    private static final PostgreSQLContainer DATABASE = startDatabase();

    private static final VaultContainer<?> VAULT = startVault();

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private Substrate() {
    }

    /**
     * Заводит пустую базу стороны в общем контейнере.
     *
     * @param name имя базы — тропа и сторона
     * @return адрес и учётные данные базы
     */
    public static Database database(String name) {
        try (Connection connection = DriverManager.getConnection(DATABASE.getJdbcUrl(),
                DATABASE.getUsername(), DATABASE.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("drop database if exists " + name + " with (force)");
            statement.execute("create database " + name);
        } catch (SQLException failure) {
            throw new IllegalStateException("База стороны не заводится: " + name, failure);
        }
        String url = "jdbc:postgresql://" + DATABASE.getHost() + ":" + DATABASE.getMappedPort(5432) + "/" + name;
        return new Database(name, url, DATABASE.getUsername(), DATABASE.getPassword());
    }

    /**
     * Поднимает брокер тропы и заводит обе темы до старта сторон.
     *
     * <p><b>Автозаведение выключено:</b> тема, заведённая первым обращением
     * клиента, сделала бы «тема заведена обвязкой» неотличимым от «тему
     * завела сторона».
     */
    public static KafkaContainer broker() {
        KafkaContainer container = new KafkaContainer(DockerImageName.parse(BROKER_IMAGE))
                .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "false");
        container.start();
        createTopics(container.getBootstrapServers(), List.of(STRATEGY_TOPIC, CORE_TOPIC));
        return container;
    }

    /** Адрес хранилища секретов. */
    public static String vaultAddress() {
        return VAULT.getHttpHostAddress();
    }

    /**
     * Кладёт ключи счёта в хранилище — так, как их кладёт владелец реестра.
     *
     * <p><b>Писателя ключей в тропе нет</b>: единственный, кто их пишет, —
     * {@code auth}, а он стороной тропы не является и поднимается стабом.
     *
     * @param accountInternalId идентичность счёта
     * @param contour           контур счёта у площадки
     */
    public static void putAccountKeys(String accountInternalId, String contour) {
        String body = """
                {"apiKey": "%s", "secret": "%s", "passphrase": "%s", "contour": "%s"}
                """.formatted(apiKeyOf(accountInternalId), "secret-" + accountInternalId,
                "passphrase-" + accountInternalId, contour);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(vaultAddress() + "/v1/" + ENVIRONMENT + "/exchange-accounts/" + accountInternalId))
                .header("X-Vault-Token", VAULT_TOKEN)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            HttpResponse<String> answer = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (answer.statusCode() >= 300) {
                throw new IllegalStateException("Хранилище отвергло ключи счёта: "
                        + answer.statusCode() + " " + answer.body());
            }
        } catch (IOException failure) {
            throw new IllegalStateException("Хранилище субстрата не ответило", failure);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Ожидание ответа хранилища прервано", failure);
        }
    }

    /** Ключ API счёта, лежащий в хранилище: им подписанный запрос опознаётся у стаба площадки. */
    public static String apiKeyOf(String accountInternalId) {
        return "api-key-" + accountInternalId;
    }

    private static PostgreSQLContainer startDatabase() {
        PostgreSQLContainer container = new PostgreSQLContainer(
                DockerImageName.parse(DATABASE_IMAGE).asCompatibleSubstituteFor("postgres"))
                .withDatabaseName("e2e")
                .withUsername("e2e")
                .withPassword("e2e")
                .withCommand("postgres", "-c", "fsync=off", "-c", "max_connections=300",
                        "-c", "shared_preload_libraries=" + PRELOADED_LIBRARIES);
        container.start();
        return container;
    }

    private static VaultContainer<?> startVault() {
        VaultContainer<?> container = new VaultContainer<>(DockerImageName.parse(VAULT_IMAGE))
                .withVaultToken(VAULT_TOKEN)
                .withInitCommand("secrets enable -path=" + ENVIRONMENT + " -version=1 kv");
        container.start();
        return container;
    }

    private static void createTopics(String bootstrapServers, List<String> names) {
        Map<String, Object> settings = new HashMap<>();
        settings.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        try (Admin admin = Admin.create(settings)) {
            admin.createTopics(names.stream().map(name -> new NewTopic(name, 1, (short) 1)).toList())
                    .all()
                    .get(ADMIN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Заведение тем тропы прервано", failure);
        } catch (Exception failure) {
            throw new IllegalStateException("Брокер тропы не завёл тем", failure);
        }
    }
}
