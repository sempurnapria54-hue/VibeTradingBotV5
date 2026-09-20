package com.example.marketdata.box;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Субстрат чёрного ящика `market-data`: контейнер базы, стаб коннектора и
 * стаб провайдера идентичности
 * (.claude/decisions/test-contour-design-pass.md, решения 2, 3, 4).
 *
 * <p><b>Контейнера хранилища секретов здесь НЕТ, и это предмет клетки, а
 * не экономия.</b> Ключей биржевого счёта у сервиса нет ни в каком виде —
 * все его чтения площадки публичные, — и {@code B9.8} стои́т ровно на том,
 * что весь набор троп проходит при не поднятом Vault. Подними прогон
 * хранилище «на всякий случай» — клетка перестала бы утверждать
 * что-либо.
 *
 * <p><b>Образ базы — тот же, что в {@code deploy/base}</b>, и совпадение
 * тега сверяет проба ({@link SubstrateImagePinTest}): миграция {@code V1}
 * заводит ГИПЕРТАБЛИЦЫ, а на стоковом {@code postgres} они не
 * поднимаются вовсе ({@code B9.7}).
 *
 * <p><b>Субстрат общий на прогон, а не на кейс:</b> подъём контейнера
 * стои́т секунды, и платить их за каждую клетку незачем. Свой экземпляр
 * берут ровно те кейсы, у которых на это есть одно из двух оснований —
 * кейс лишает соседей адреса либо предмет кейса есть состояние субстрата
 * целиком (там же, §«Оснований брать свой контейнер ДВА…»); у
 * `market-data` такой один — {@code B9.7}, и контейнер он заводит сам.
 *
 * <p><b>Расписание в прогоне выключено ВЫРАЖЕНИЕМ, а не выключателем.</b>
 * Выключатель тика есть ВХОД кейсов {@code B2.8} и {@code B4.12}, и
 * глушить им расписание значило бы отнять у них предмет; такт же
 * приезжает конфигурацией ({@code B9.5}), и выражение «раз в год» тик по
 * расписанию в прогоне не даёт ни одного. Тик подаёт сам кейс — ручным
 * фасадом.
 */
final class MarketDataSubstrate {

    /**
     * Образ базы: тот же тег, что у стенда
     * ({@code deploy/base/data/postgres-cluster.yaml}).
     */
    static final String DATABASE_IMAGE = "timescale/timescaledb-ha:pg17.10-ts2.29.2";

    /** Код площадки штатного прогона. */
    static final String EXCHANGE_CODE = "OKX";

    /** Имя окружения штатного прогона. */
    static final String ENVIRONMENT = "dev";

    /** Выражение такта, до которого прогон не доживает: тик подаёт кейс. */
    static final String NEVER = "0 0 0 1 1 *";

    private static final PostgreSQLContainer DATABASE = startDatabase();

    private MarketDataSubstrate() {
    }

    /** Контейнер базы общего субстрата. */
    static PostgreSQLContainer database() {
        return DATABASE;
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
        values.putAll(databaseAddress(DATABASE));
        values.put("spring.security.oauth2.resourceserver.jwt.issuer-uri", IdentityStub.stub().issuer());
        values.put("spring.security.oauth2.client.provider.platform.token-uri", IdentityStub.stub().tokenUri());
        values.put("spring.security.oauth2.client.registration.connector.client-id", "market-data");
        values.put("spring.security.oauth2.client.registration.connector.client-secret", "market-data-secret");
        values.put("platform.environment.name", ENVIRONMENT);
        values.put("connector.exchange-code", EXCHANGE_CODE);
        values.put("connector.base-url", ConnectorStub.stub().baseUrl());
        values.put("connector.instrument-types", "SWAP");
        values.put("connector.index-quote-currencies", "USDT,USD");
        values.putAll(silentSchedule());
        return values;
    }

    /**
     * Адрес названного контейнера базы вместе с потолком пула.
     *
     * <p><b>Потолок пула задан, и это не настройка «для скорости».</b>
     * Контекстов у прогона столько, сколько у ящика положений осей
     * конфигурации, и КАЖДЫЙ держит свой пул к одному и тому же
     * контейнеру; при умолчании в десять соединений полтора десятка
     * контекстов исчерпывают лимит клиентов базы, и падает при этом не
     * та клетка, которая его исчерпала, а следующая. Кейсу больше одного
     * соединения не нужно: тик подаётся по одному.
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

    /** Такт всех пяти тиков, до которого прогон не доживает. */
    static Map<String, String> silentSchedule() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("instrument-sync.cron", NEVER);
        values.put("candle-loading.cron", NEVER);
        values.put("market-data.indicator.cron", NEVER);
        values.put("market-data.structure.cron", NEVER);
        values.put("snapshot-collection.cron", NEVER);
        return values;
    }

    /** Контейнер базы на образе стенда: расширение временных рядов в нём есть. */
    static PostgreSQLContainer startOn(String image) {
        PostgreSQLContainer container = new PostgreSQLContainer(
                DockerImageName.parse(image).asCompatibleSubstituteFor("postgres"))
                .withDatabaseName("market_data")
                .withUsername("market_data")
                .withPassword("market_data");
        container.start();
        return container;
    }

    private static PostgreSQLContainer startDatabase() {
        return startOn(DATABASE_IMAGE);
    }
}
