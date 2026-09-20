package com.example.tradingcore.box;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Субстрат чёрного ящика `trading-core`: контейнер базы, три стаба соседей
 * по ярусу и стаб провайдера идентичности
 * (.claude/decisions/test-contour-design-pass.md, решения 2, 3, 4).
 *
 * <p><b>Контейнера хранилища секретов здесь НЕТ, и это следствие предмета,
 * а не экономия.</b> Ключей биржевого счёта у ядра нет ни в каком виде —
 * оно адресует счёт идентичностью, а ключи резолвит коннектор
 * (docs/architecture/services/trading-core.md §«Чего не делает
 * намеренно»), — и поднятое «на всякий случай» хранилище отняло бы у
 * группы {@code B14} её предмет.
 *
 * <p><b>Контейнер БРОКЕРА нужен обеими сторонами.</b> Ядро и публикует
 * через outbox, и потребляет тему определений
 * (.claude/tests/cases/trading-core.md §«Чем достаются выходы»), и обе
 * стороны наблюдаются им: опубликованная строка читается своим
 * потребителем прогона, а копия определения ставится ТРОПОЙ ЯЩИКА —
 * сообщением в тему владельца определений, а не вставкой дерева в базу.
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
 * <p><b>Контекст со СВОИМ положением осей берёт и свою группу
 * потребителя, и это не удобство.</b> Контексты прогона не закрываются —
 * их кэширует каркас теста, — а группа потребителя есть состояние на
 * брокере: два контекста с одним именем группы делят партии темы, и
 * партия достаётся ОДНОМУ из них. Тот, кому она не досталась, сообщения
 * не увидит вовсе, а клетка упрётся в таймаут ожидания копии — то есть
 * наблюдатель отнимет предмет у соседнего ящика. Поэтому всякий класс со
 * своим методом {@code @DynamicPropertySource} называет и свою группу
 * ({@link #CONSUMER_GROUP_KEY}); штатное имя группы остаётся у общего
 * ящика ({@link SharedTradingCoreBox}).
 *
 * <p><b>Своя группа заодно СВЕЖА по построению</b> — зафиксированных
 * смещений у неё нет, — и клетка о позиции чтения с начала темы получает
 * своё предусловие тем же ходом.
 *
 * <p><b>Из той же свежести следует вторая ось, и она обязательна: своя
 * ТЕМА.</b> Свежая группа читает с начала, а тема переживает и клетку, и
 * контекст — в ней лежат определения всех соседних классов прогона.
 * Одиночка, поднявшаяся после них, получает переигрывание всей темы, и
 * идёт оно параллельно опустошению базы, которым она начинает клетку:
 * два хода сходятся на одних таблицах в разном порядке, и Postgres
 * снимает один из них взаимной блокировкой. Своя тема снимает причину —
 * читать одиночке нечего, кроме положенного ею самой
 * ({@link #registerOwn}).
 *
 * <p><b>Расписание в прогоне глушится ВЫРАЖЕНИЕМ такта, а не
 * выключателем.</b> Выключатель тика есть ВХОД кейсов {@code B1.13} и
 * {@code B13.*}, и глушить им расписание значило бы отнять у них предмет;
 * такт же приезжает конфигурацией у всех семи тиков, и выражение «раз в
 * год» не даёт по расписанию ни одного. Тик подаёт сам кейс — ручным
 * фасадом.
 */
final class TradingCoreSubstrate {

    /**
     * Образ базы: тот же тег, что у стенда
     * ({@code deploy/base/data/postgres-cluster.yaml}).
     */
    static final String DATABASE_IMAGE = "timescale/timescaledb-ha:pg17.10-ts2.29.2";

    /** Код площадки штатного прогона. */
    static final String EXCHANGE_CODE = "OKX";

    /** Имя окружения штатного прогона. */
    static final String ENVIRONMENT = "dev";

    /**
     * Образ брокера: та же версия, что у клиента в дереве зависимостей
     * ({@code kafka-clients}). Совпадение сверяет проба
     * ({@link SubstrateImagePinTest}).
     */
    static final String BROKER_IMAGE = "apache/kafka:4.1.1";

    /** Тема, в которую ядро публикует свои факты. */
    static final String CORE_TOPIC = "trading-core.facts";

    /** Тема, из которой ядро читает факты владельца определений. */
    static final String STRATEGY_TOPIC = "strategies.facts";

    /**
     * Ключ имени группы потребителя: его переопределяет КАЖДЫЙ класс со
     * своим положением осей конфигурации (см. шапку класса).
     */
    static final String CONSUMER_GROUP_KEY = "consumers.strategy-facts.group-id";

    /**
     * Ключ темы владельца определений: её переопределяет тот же класс и по
     * тому же поводу (см. шапку класса).
     */
    static final String STRATEGY_TOPIC_KEY = "consumers.strategy-facts.topic";

    /** Ключ окна чтения неопубликованных строк реле. */
    static final String RELAY_WINDOW_KEY = "outbox-relay.batch-size";

    /** Ключ окна выборки торгуемых инструментов у отбора входа. */
    static final String ENTRY_WINDOW_KEY = "entry-scanner.instrument-window";

    /** Ключ окна выборки нетерминальных сделок у прохода сопровождения. */
    static final String PASS_WINDOW_KEY = "deal-orchestrator.batch-size";

    /**
     * Ключ выключателя отбора входа.
     *
     * <p>Расписание в прогоне глушится ВЫРАЖЕНИЕМ такта, а не им: сам
     * выключатель есть вход клетки (см. шапку класса).
     */
    static final String ENTRY_ENABLED_KEY = "entry-scanner.enabled";

    /**
     * Ключ выключателя проактивной детекции.
     *
     * <p>Тот же довод, что у выключателя отбора входа: расписание глушится
     * выражением такта, а сам выключатель есть ВХОД клетки {@code B7.10}.
     */
    static final String ANOMALY_ENABLED_KEY = "anomaly-job.enabled";

    /** Ключ окна выборки контура у прохода проактивной детекции. */
    static final String CONTOUR_WINDOW_KEY = "anomaly-job.contour-window";

    /** Ключ адреса брокера: им перекрывается тропа публикации. */
    static final String BROKER_ADDRESS_KEY = "broker.bootstrap-servers";

    /** Выражение такта, до которого прогон не доживает: тик подаёт кейс. */
    static final String NEVER = "0 0 0 1 1 *";

    private static final PostgreSQLContainer DATABASE = startOn(DATABASE_IMAGE);

    private static final KafkaContainer BROKER = startBroker();

    private TradingCoreSubstrate() {
    }

    /** Контейнер базы общего субстрата. */
    static PostgreSQLContainer database() {
        return DATABASE;
    }

    /** Контейнер брокера общего субстрата. */
    static KafkaContainer broker() {
        return BROKER;
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

    /**
     * Свойства контекста класса-ОДИНОЧКИ: его оси плюс своя группа
     * потребителя и своя тема владельца определений.
     *
     * <p><b>Своей темы мало было бы назвать группу, и вот почему.</b>
     * Своя группа свежа, то есть читает тему С НАЧАЛА, а тема переживает
     * и клетку, и контекст: в ней лежат все определения, положенные
     * соседними классами прогона. Одиночка поднимается ПОСЛЕ них и
     * получает переигрывание всей темы — а идёт оно параллельно
     * опустошению базы, которым одиночка начинает свою клетку. Дальше
     * два хода сходятся на одних таблицах в разном порядке, и Postgres
     * снимает один из них: {@code truncate} роняет клетку взаимной
     * блокировкой, а не расхождением предмета.
     *
     * <p><b>Своя тема снимает причину, а не следствие:</b> читать
     * одиночке становится нечего — в её теме лежит только то, что
     * положила она сама.
     *
     * @param registry  реестр свойств контекста
     * @param name      имя одиночки: им называются и группа, и тема
     * @param overrides оси, которые кейс сдвигает сверх этого
     */
    static void registerOwn(DynamicPropertyRegistry registry, String name,
                            Map<String, String> overrides) {
        Map<String, String> values = new LinkedHashMap<>(overrides);
        values.put(CONSUMER_GROUP_KEY, name);
        values.put(STRATEGY_TOPIC_KEY, ownStrategyTopic(name));
        register(registry, values);
    }

    /** Тема владельца определений у названной одиночки. */
    static String ownStrategyTopic(String name) {
        return STRATEGY_TOPIC + "." + name;
    }

    /** Штатное положение всех осей контекста. */
    static Map<String, String> defaults() {
        Map<String, String> values = new LinkedHashMap<>();
        values.putAll(databaseAddress(DATABASE));
        values.put("spring.security.oauth2.resourceserver.jwt.issuer-uri", IdentityStub.stub().issuer());
        values.put("spring.security.oauth2.client.provider.platform.token-uri", IdentityStub.stub().tokenUri());
        values.put("spring.security.oauth2.client.registration.platform-services.client-id", "trading-core");
        values.put("spring.security.oauth2.client.registration.platform-services.client-secret",
                "trading-core-secret");
        values.put("platform.environment.name", ENVIRONMENT);
        values.put("neighbours.connector.exchange-code", EXCHANGE_CODE);
        values.put("neighbours.connector.base-url", PeerStub.connector().baseUrl());
        values.put("neighbours.auth.base-url", PeerStub.auth().baseUrl());
        values.put("neighbours.market-data.base-url", PeerStub.marketData().baseUrl());
        values.put(BROKER_ADDRESS_KEY, BROKER.getBootstrapServers());
        values.putAll(silentSchedule());
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

    /** Такт всех семи тиков, до которого прогон не доживает. */
    static Map<String, String> silentSchedule() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("deal-orchestrator.cron", NEVER);
        values.put("entry-scanner.cron", NEVER);
        values.put("anomaly-job.cron", NEVER);
        values.put("outbox-relay.cron", NEVER);
        values.put("projection-sync.cron", NEVER);
        values.put("strategy-demand.cron", NEVER);
        values.put("trade-fee-rate-sync.cron", NEVER);
        return values;
    }

    /** Контейнер базы на образе стенда. */
    static PostgreSQLContainer startOn(String image) {
        PostgreSQLContainer container = new PostgreSQLContainer(
                DockerImageName.parse(image).asCompatibleSubstituteFor("postgres"))
                .withDatabaseName("trading_core")
                .withUsername("trading_core")
                .withPassword("trading_core");
        container.start();
        return container;
    }

    private static KafkaContainer startBroker() {
        KafkaContainer container = new KafkaContainer(DockerImageName.parse(BROKER_IMAGE));
        container.start();
        return container;
    }
}
