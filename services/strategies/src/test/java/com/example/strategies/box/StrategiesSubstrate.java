package com.example.strategies.box;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Субстрат чёрного ящика `strategies`: контейнер базы, контейнер брокера,
 * стаб единственного соседа по ярусу и стаб провайдера идентичности
 * (.claude/decisions/test-contour-design-pass.md, решения 2, 3, 4).
 *
 * <p><b>Контейнера хранилища секретов здесь НЕТ, и это следствие предмета,
 * а не экономия.</b> Ключей площадки владелец определений не касается ни
 * одной тропой (docs/architecture/services/strategies.md §«Чего не делает
 * намеренно»), и поднятое «на всякий случай» хранилище отняло бы у группы
 * {@code B10} её предмет.
 *
 * <p><b>Брокер нужен ОДНОЙ стороной, и это ось предмета.</b> Сервис
 * ничего не потребляет — он существует ради того, что публикует
 * (.claude/tests/cases/strategies.md §«Новая ось формы — событие как
 * ВЫХОД»). Поэтому контейнер брокера служит здесь наблюдателем ВЫХОДА
 * реле, а входа через него у ящика нет вовсе: предусловия ставятся
 * поверхностью, а не сообщением.
 *
 * <p><b>Образ брокера пинится версией клиента дерева.</b> Манифест стенда
 * версии не называет вовсе — её выбирает оператор, — поэтому единственная
 * запись, с которой совпадение проверяемо, есть версия {@code kafka-clients}
 * в дереве зависимостей; пин на неё и стои́т.
 *
 * <p><b>Образ базы — тот же, что в {@code deploy/base}</b>, и совпадение
 * тега сверяет проба ({@link SubstrateImagePinTest}): две записи одного
 * тега расходятся молча.
 *
 * <p><b>Субстрат общий на прогон, а не на кейс:</b> подъём контейнера
 * стои́т секунды, и платить их за каждую клетку незачем. Свой экземпляр
 * берут ровно те кейсы, у которых на это есть одно из двух оснований —
 * кейс лишает соседей адреса либо предмет кейса есть состояние субстрата
 * целиком (там же, §«Оснований брать свой контейнер ДВА…»).
 *
 * <p><b>Расписание в прогоне глушится ВЫРАЖЕНИЕМ такта, а не
 * выключателем.</b> Выключатель реле есть ВХОД клетки {@code B7.8}, и
 * глушить им расписание значило бы отнять у неё предмет; такт же
 * приезжает конфигурацией, и выражение «раз в год» не даёт по расписанию
 * ни одного тика. Тик подаёт сам кейс — ручным фасадом.
 *
 * <p><b>Своей темы у одиночек здесь не нужно, и различие с соседним
 * ящиком названо.</b> У ядра тема была ВХОДОМ — копия определения
 * ставилась сообщением, и свежая группа потребителя переигрывала чужие
 * сообщения; здесь потребителя нет ни одного, а тема читается прогоном
 * своим клиентом с названного смещения.
 */
final class StrategiesSubstrate {

    /**
     * Образ базы: тот же тег, что у стенда
     * ({@code deploy/base/data/postgres-cluster.yaml}).
     */
    static final String DATABASE_IMAGE = "timescale/timescaledb-ha:pg17.10-ts2.29.2";

    /**
     * Образ брокера: та же версия, что у клиента в дереве зависимостей
     * ({@code kafka-clients}). Совпадение сверяет проба
     * ({@link SubstrateImagePinTest}).
     */
    static final String BROKER_IMAGE = "apache/kafka:4.1.1";

    /** Тема, в которую владелец определений публикует свои факты. */
    static final String FACTS_TOPIC = "strategies.facts";

    /** Ключ адреса соседа по ярусу: им перекрывается тропа чтения операндов. */
    static final String PEER_ADDRESS_KEY = "neighbours.trading-core.base-url";

    /** Ключ адреса брокера: им перекрывается тропа публикации. */
    static final String BROKER_ADDRESS_KEY = "broker.bootstrap-servers";

    /** Ключ окна перечня определений тенанта. */
    static final String LIST_WINDOW_KEY = "surface.strategy-list-window";

    /** Ключ окна чтения неопубликованных строк реле. */
    static final String RELAY_WINDOW_KEY = "jobs.outbox-relay.batch-size";

    /**
     * Ключ выключателя реле.
     *
     * <p>Расписание в прогоне глушится ВЫРАЖЕНИЕМ такта, а не им: сам
     * выключатель есть вход клетки (см. шапку класса).
     */
    static final String RELAY_ENABLED_KEY = "jobs.outbox-relay.enabled";

    /** Ключ такта реле. */
    static final String RELAY_CRON_KEY = "jobs.outbox-relay.cron";

    /** Ключ точки провайдера идентичности, по которой проверяется входящий токен. */
    static final String ISSUER_KEY = "spring.security.oauth2.resourceserver.jwt.issuer-uri";

    /** Ключ точки выдачи исходящей служебной идентичности. */
    static final String TOKEN_URI_KEY = "spring.security.oauth2.client.provider.platform.token-uri";

    /** Выражение такта, до которого прогон не доживает: тик подаёт кейс. */
    static final String NEVER = "0 0 0 1 1 *";

    private static final PostgreSQLContainer DATABASE = startOn(DATABASE_IMAGE);

    private static final KafkaContainer BROKER = startBroker();

    private StrategiesSubstrate() {
    }

    /** Контейнер базы общего субстрата. */
    static PostgreSQLContainer database() {
        return DATABASE;
    }

    /** Адрес брокера общего субстрата: им читает свой клиент прогона. */
    static String brokerAddress() {
        return BROKER.getBootstrapServers();
    }

    /**
     * Свойства контекста ящика: адреса субстрата плюс оси конфигурации.
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
        values.putAll(databaseAddress(DATABASE));
        values.put(ISSUER_KEY, IdentityStub.stub().issuer());
        values.put(TOKEN_URI_KEY, IdentityStub.stub().tokenUri());
        values.put("spring.security.oauth2.client.registration.platform-services.client-id", "strategies");
        values.put("spring.security.oauth2.client.registration.platform-services.client-secret",
                "strategies-secret");
        values.put(PEER_ADDRESS_KEY, PeerStub.tradingCore().baseUrl());
        values.put(BROKER_ADDRESS_KEY, BROKER.getBootstrapServers());
        values.put(RELAY_ENABLED_KEY, "true");
        values.put(RELAY_CRON_KEY, NEVER);
        return values;
    }

    /**
     * Адрес названного контейнера базы вместе с потолком пула.
     *
     * <p><b>Потолок пула задан, и это не настройка «для скорости».</b>
     * Контекстов у прогона столько, сколько у ящика положений осей
     * конфигурации, и КАЖДЫЙ держит свой пул к одному и тому же
     * контейнеру; при умолчании сервиса в десять соединений полтора
     * десятка контекстов исчерпывают лимит клиентов базы, и падает при
     * этом не та клетка, которая его исчерпала, а следующая. Кейсу больше
     * трёх соединений не нужно: тик подаётся по одному.
     *
     * @param container контейнер базы
     * @return адрес, учётные данные и потолок пула
     */
    static Map<String, String> databaseAddress(PostgreSQLContainer container) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("spring.datasource.url", container.getJdbcUrl());
        values.put("spring.datasource.username", container.getUsername());
        values.put("spring.datasource.password", container.getPassword());
        values.put("spring.datasource.hikari.maximum-pool-size", "3");
        values.put("spring.datasource.hikari.minimum-idle", "0");
        return values;
    }

    /** Контейнер базы на образе стенда. */
    static PostgreSQLContainer startOn(String image) {
        PostgreSQLContainer container = new PostgreSQLContainer(
                DockerImageName.parse(image).asCompatibleSubstituteFor("postgres"))
                .withDatabaseName("strategies")
                .withUsername("strategies")
                .withPassword("strategies");
        container.start();
        return container;
    }

    private static KafkaContainer startBroker() {
        KafkaContainer container = new KafkaContainer(DockerImageName.parse(BROKER_IMAGE));
        container.start();
        return container;
    }
}
