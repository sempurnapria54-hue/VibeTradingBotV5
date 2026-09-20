package com.example.audit.box;

import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Субстрат чёрного ящика `audit`: контейнер базы, контейнер брокера и стаб
 * провайдера идентичности
 * (.claude/decisions/test-contour-design-pass.md, решения 2, 4).
 *
 * <p><b>Контейнера хранилища секретов здесь НЕТ, и это следствие предмета,
 * а не экономия.</b> Ключей площадки журнал не касается ни одной тропой, а
 * учётные данные базы приезжают свойствами прогона
 * (.claude/tests/cases/audit.md §«Чем достаются выходы»).
 *
 * <p><b>Брокер нужен ОБЕИМИ сторонами, и это ось предмета.</b> Единственный
 * вход сервиса — события: кейс кладёт запись в тему и смотрит, что из неё
 * вышло (.claude/tests/cases/audit.md §«Новая ось формы — событие как
 * ВХОД»). Поэтому контейнер брокера служит здесь и подателем входа, и
 * наблюдателем смещений группы — в отличие от соседнего ящика, где он
 * наблюдал только выход реле.
 *
 * <p><b>Темы заводятся ЯВНО, до первого контекста.</b> Автозаведение темы
 * первым спросом отдаёт раскладку не сразу — лидер партии на этот момент
 * ещё не выбран, — а тик состояния приёма мерит живость НАЗНАЧЕННЫМИ
 * партициями: контекст, поднявшийся раньше темы, получил бы пустое
 * назначение и тик, молчащий по причине, которой кейс не ставил.
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
 * стои́т секунды, и платить их за каждую клетку незачем.
 *
 * <p><b>Расписание в прогоне глушится ВЫРАЖЕНИЕМ такта, а не
 * выключателем.</b> Выключатели тика и чистки суть ВХОДЫ клеток
 * {@code B3.7} и {@code B7.5}, и глушить ими расписание значило бы отнять у
 * них предмет. У тика такт выражается длительностью, и часовая не даёт за
 * прогон ни одного повторения; у чистки — выражением, и «раз в год» не даёт
 * ни одного вовсе. Тик подаёт сам кейс — прямым вызовом метода джобы,
 * потому что ручного фасада у сервиса нет намеренно (решение 6).
 */
final class AuditSubstrate {

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

    /** Тема фактов торгового ядра: первый производитель несомых классов. */
    static final String CORE_TOPIC = "trading-core.facts";

    /** Тема фактов владельца определений: второй производитель. */
    static final String STRATEGY_TOPIC = "strategies.facts";

    /** Имя группы потребителя штатного прогона. */
    static final String CONSUMER_GROUP = "audit.journal";

    /** Ключ адреса брокера: им перекрывается тропа приёма. */
    static final String BROKER_ADDRESS_KEY = "reception.bootstrap-servers";

    /** Ключ имени группы потребителя. */
    static final String CONSUMER_GROUP_KEY = "reception.group-id";

    /** Ключ подписки: форма значения — скаляр через запятую. */
    static final String TOPICS_KEY = "reception.topics";

    /** Ключ выключателя тика состояния приёма. */
    static final String STATE_TICK_ENABLED_KEY = "reception.state-tick-enabled";

    /** Ключ допустимого возраста строки состояния — третьего конъюнкта предиката. */
    static final String STATE_MAX_AGE_KEY = "reception.state-max-age";

    /**
     * Допустимый возраст строки состояния приёма в штатном положении осей.
     *
     * <p><b>Объявлен здесь ВЕЛИЧИНОЙ, а не унаследован умолчанием
     * сервиса.</b> Клетка о третьем конъюнкте предиката утверждает о самой
     * ГРАНИЦЕ возраста — строка старше её на секунду и моложе на секунду
     * дают разные ответы, — и границу эту обязан знать кейс. Унаследованное
     * умолчание пришлось бы прочитать из ресурса сервиса, то есть заглянуть
     * ящику внутрь; объявленная ось читается как вход.
     */
    static final Duration STATE_MAX_AGE = Duration.ofMinutes(5);

    /** Ключ паузы между повторами отравленного сообщения. */
    static final String RETRY_INTERVAL_KEY = "reception.retry-interval";

    /** Ключ точки провайдера идентичности, по которой проверяется токен. */
    static final String ISSUER_KEY = "spring.security.oauth2.resourceserver.jwt.issuer-uri";

    /** Ключ выключателя чистки журнала. */
    static final String CLEANUP_ENABLED_KEY = "jobs.journal-cleanup.enabled";

    /** Ключ профиля хранения журнала — оси окружения. */
    static final String RETENTION_PROFILE_KEY = "platform.environment.journal-retention-profile";

    /** Ключ предела ширины окна журнальной выборки чтения. */
    static final String MAX_WINDOW_KEY = "surface.journal-read.max-window";

    /**
     * Предельная ширина окна чтения в штатном положении осей.
     *
     * <p><b>Объявлен здесь ВЕЛИЧИНОЙ по тому же доводу, что допустимый
     * возраст строки состояния:</b> клетка о третьем отвержении вопроса
     * утверждает о самой ГРАНИЦЕ — окно шириной ровно в предел принимается,
     * шире на секунду отвергается, — и границу эту обязан знать кейс.
     * Унаследованное умолчание пришлось бы прочитать из ресурса сервиса, то
     * есть заглянуть ящику внутрь.
     *
     * <p><b>Значение совпадает с умолчанием сервиса, и это не дубль, а
     * объявление.</b> Сдвигать его незачем: окно в неделю прогону ничего не
     * стои́т — в отличие от размера страницы, который сдвинут ниже.
     */
    static final Duration MAX_WINDOW = Duration.ofDays(7);

    /** Ключ размера курсорной страницы журнальной выборки чтения. */
    static final String PAGE_SIZE_KEY = "surface.journal-read.page-size";

    /**
     * Размер страницы чтения в штатном положении осей.
     *
     * <p><b>СДВИНУТ относительно умолчания сервиса, и сдвиг назван.</b>
     * Клетки о курсорной странице требуют журнала ДЛИННЕЕ страницы: при
     * умолчании в две сотни строк им пришлось бы провести через брокер две
     * сотни записей — то есть мерить пропускную способность субстрата, а не
     * продолжение чтения. Размер есть ось конфигурации у самого предмета
     * (docs/models/domain/other/AuditRecord.md §«Как журнал читается»),
     * поэтому его положение — законный вход ящика, а не подмена.
     *
     * <p><b>Прочим клеткам прогона сдвиг безразличен:</b> ни одна из них не
     * читает страницы длиннее одной строки.
     */
    static final Integer PAGE_SIZE = 3;

    /** Выражение такта, до которого прогон не доживает. */
    static final String NEVER = "0 0 0 1 1 *";

    /**
     * Метка «ключ в контекст не уезжает вовсе»: ею ставится состояние
     * «ось не доехала».
     *
     * <p><b>Пустое значение здесь не значение, а ИЗЪЯТИЕ ключа.</b>
     * Реестр свойств перекрывает умолчание сервиса, а клетке о
     * недоехавшей оси нужно ровно обратное — чтобы до исполнителя доехало
     * то, что он получает в развёртывании без назначенной оси. Его
     * собственное умолчание пусто ({@code application.yaml}), и снятый
     * ключ отдаёт исполнителю именно его; подстановка пустой строки
     * перекрытием мерила бы вдобавок преобразование пустого значения в
     * перечень, то есть не тот предмет.
     */
    static final String UNSET = "";

    /** Такт тика, второго повторения которого за прогон не бывает. */
    private static final String HOURLY = "1h";

    /** Потолок ожидания заведения тем. */
    private static final Duration ADMIN_TIMEOUT = Duration.ofSeconds(60);

    private static final PostgreSQLContainer DATABASE = startOn(DATABASE_IMAGE);

    private static final KafkaContainer BROKER = startBroker();

    private AuditSubstrate() {
    }

    /** Контейнер базы общего субстрата. */
    static PostgreSQLContainer database() {
        return DATABASE;
    }

    /** Адрес брокера общего субстрата: им ходит свой клиент прогона. */
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
     * <p><b>Ключ, чьё перекрытие равно {@link #UNSET}, изымается из
     * перечня</b>, и сервис получает по нему своё умолчание — состояние
     * «ось не доехала».
     *
     * @param registry  реестр свойств контекста
     * @param overrides оси, которые кейс сдвигает
     */
    static void register(DynamicPropertyRegistry registry, Map<String, String> overrides) {
        Map<String, String> values = new LinkedHashMap<>(defaults());
        values.putAll(overrides);
        values.values().removeIf(UNSET::equals);
        values.forEach((key, value) -> registry.add(key, () -> value));
    }

    /** Штатное положение всех осей контекста. */
    static Map<String, String> defaults() {
        Map<String, String> values = new LinkedHashMap<>();
        values.putAll(databaseAddress(DATABASE));
        values.put(ISSUER_KEY, IdentityStub.stub().issuer());
        values.put(BROKER_ADDRESS_KEY, BROKER.getBootstrapServers());
        values.put(CONSUMER_GROUP_KEY, CONSUMER_GROUP);
        values.put(TOPICS_KEY, CORE_TOPIC + "," + STRATEGY_TOPIC);
        values.put(STATE_TICK_ENABLED_KEY, "true");
        values.put("reception.state-tick-interval", HOURLY);
        values.put(STATE_MAX_AGE_KEY, STATE_MAX_AGE.toMinutes() + "m");
        values.put(CLEANUP_ENABLED_KEY, "true");
        values.put("jobs.journal-cleanup.cron", NEVER);
        values.put("platform.environment.name", "dev");
        values.put(RETENTION_PROFILE_KEY, "REDUCED");
        values.put(MAX_WINDOW_KEY, MAX_WINDOW.toDays() + "d");
        values.put(PAGE_SIZE_KEY, String.valueOf(PAGE_SIZE));
        return values;
    }

    /**
     * Адрес названного контейнера базы вместе с потолком пула.
     *
     * <p><b>Потолок пула задан, и это не настройка «для скорости».</b>
     * Контекстов у прогона столько, сколько у ящика положений осей
     * конфигурации, и КАЖДЫЙ держит свой пул к одному и тому же
     * контейнеру; при умолчании сервиса в пять соединений полтора десятка
     * контекстов исчерпывают лимит клиентов базы, и падает при этом не та
     * клетка, которая его исчерпала, а следующая.
     *
     * @param container контейнер базы
     * @return адрес, учётные данные и потолок пула
     */
    static Map<String, String> databaseAddress(PostgreSQLContainer container) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("audit.persistence.journal.url", container.getJdbcUrl());
        values.put("audit.persistence.journal.username", container.getUsername());
        values.put("audit.persistence.journal.password", container.getPassword());
        values.put("audit.persistence.journal.max-pool-size", "3");
        return values;
    }

    /**
     * Свойства контекста клетки, у которой СВОЯ группа и СВОИ темы.
     *
     * <p><b>Своя пара нужна не для порядка, а потому что клетка ЛОМАЕТ
     * приём.</b> Отравленное сообщение повторяется без ограничения числа
     * попыток и занимает единственный поток слушателя; контекст, в котором
     * оно легло, больше не принимает ничего — ни по этой теме, ни по
     * соседней. Две такие клетки в одном контексте невозможны: вторая
     * наблюдала бы исход первой, и её отрицания («строки нет», «смещение
     * стои́т») сошлись бы по ложной причине — сообщение до обработки просто
     * не дошло бы.
     *
     * <p><b>Своя ГРУППА и своя ТЕМА берутся вместе</b>, и довод тот же, что
     * у соседнего класса ({@link SharedAuditBox}): своя группа читает тему с
     * начала, поэтому без своей темы одиночка переиграла бы всё, что
     * положили соседние классы прогона.
     *
     * <p><b>Тем у клетки ДВЕ, а не одна:</b> предусловие группы говорит
     * «строки обеих пар заведены», и радиус остановки — что флаг лёг у
     * своей пары, а не у группы — на одной паре не выразим вовсе.
     *
     * @param registry  реестр свойств контекста
     * @param slug      краткое имя клетки: из него строятся группа и темы
     * @param overrides оси, которые клетка сдвигает сверх своей пары
     */
    static void registerOwn(DynamicPropertyRegistry registry, String slug, Map<String, String> overrides) {
        List<String> both = List.of(ownCoreTopic(slug), ownStrategyTopic(slug));
        registerOwn(registry, slug, both, both, overrides);
    }

    /**
     * Свойства контекста клетки, у которой состав ЗАВЕДЁННЫХ у брокера тем
     * не совпадает с составом ОБЪЯВЛЕННОЙ подписки.
     *
     * <p><b>Два состава разведены потому, что их разводит сам предмет.</b>
     * Тик берёт состав пар из объявленной подписки контейнера, а живость —
     * из назначенных партиций
     * (docs/components/ReceptionStateJob.md §«Состав пар берётся из
     * объявленной подписки, а не из назначения»), и клетка о разведении
     * обязана поставить подписку шире назначения. Тема, у брокера не
     * заведённая, назначения не даёт ни одной партицией — и это
     * единственный повод пустого назначения, ДЕТЕРМИНИРОВАННЫЙ у прогона:
     * отдача партиции другой реплике зависит от назначающего и от
     * протокола группы, то есть сделала бы предусловие гонкой.
     *
     * <p><b>Сама возможность такого состава стои́т на том, что брокер
     * субстрата тем не заводит сам</b> ({@link #startBroker()}): при
     * автозаведении объявленная тема появилась бы первым же спросом
     * метаданных, и разведения не получилось бы вовсе.
     *
     * @param registry  реестр свойств контекста
     * @param slug      краткое имя клетки: из него строятся группа и темы
     * @param created   темы, заводимые у брокера
     * @param declared  темы объявленной подписки
     * @param overrides оси, которые клетка сдвигает сверх своей пары
     */
    static void registerOwn(DynamicPropertyRegistry registry, String slug, List<String> created,
                            List<String> declared, Map<String, String> overrides) {
        createTopics(BROKER.getBootstrapServers(), created);
        Map<String, String> values = new LinkedHashMap<>(overrides);
        values.put(CONSUMER_GROUP_KEY, CONSUMER_GROUP + "." + slug);
        values.put(TOPICS_KEY, String.join(",", declared));
        register(registry, values);
    }

    /** Своя тема первого производителя у названной клетки. */
    static String ownCoreTopic(String slug) {
        return slug + ".core";
    }

    /** Своя тема второго производителя у названной клетки. */
    static String ownStrategyTopic(String slug) {
        return slug + ".strategies";
    }

    /** Контейнер базы на образе стенда. */
    static PostgreSQLContainer startOn(String image) {
        PostgreSQLContainer container = new PostgreSQLContainer(
                DockerImageName.parse(image).asCompatibleSubstituteFor("postgres"))
                .withDatabaseName("audit")
                .withUsername("audit")
                .withPassword("audit");
        container.start();
        return container;
    }

    /**
     * Брокер субстрата, который тем НЕ ЗАВОДИТ сам.
     *
     * <p><b>Автозаведение снято не ради чистоты, а потому что оно отняло
     * бы у клеток вход.</b> Темы субстрат заводит явно и до первого
     * контекста, а клетке о разведении подписки и назначения нужна тема,
     * объявленная подпиской и у брокера отсутствующая
     * ({@link #registerOwn(DynamicPropertyRegistry, String, List, List, Map)}):
     * при автозаведении она появилась бы первым же спросом метаданных
     * потребителя. Клетке о неживом приёме автозаведение вернуло бы
     * удалённую тему обратно — то есть оживило бы приём ровно там, где
     * клетка его хоронит.
     */
    private static KafkaContainer startBroker() {
        KafkaContainer container = new KafkaContainer(DockerImageName.parse(BROKER_IMAGE))
                .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "false");
        container.start();
        createTopics(container.getBootstrapServers(), List.of(CORE_TOPIC, STRATEGY_TOPIC));
        return container;
    }

    /**
     * Заводит обе темы подписки до первого контекста.
     *
     * <p><b>Партиция у каждой одна, и это не умолчание.</b> Порядок
     * сообщений между партициями брокер не обещает ничем, а клетки о
     * моменте последнего принятого события подают записи заведомо
     * упорядоченными; две партиции сделали бы их ожидание гонкой, а не
     * утверждением о поведении.
     */
    private static void createTopics(String bootstrapServers, List<String> names) {
        Map<String, Object> settings = new HashMap<>();
        settings.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        try (Admin admin = Admin.create(settings)) {
            admin.createTopics(names.stream().map(name -> new NewTopic(name, 1, (short) 1)).toList())
                    .all()
                    .get(ADMIN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Заведение тем субстрата прервано", failure);
        } catch (Exception failure) {
            throw new IllegalStateException("Брокер субстрата не завёл тем подписки", failure);
        }
    }
}
