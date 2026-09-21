package com.example.statistics.box;

import static org.apache.commons.lang3.BooleanUtils.isFalse;

import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Субстрат чёрного ящика `statistics`: контейнер базы, контейнер брокера и
 * стаб провайдера идентичности
 * (.claude/decisions/test-contour-design-pass.md, решения 2, 4).
 *
 * <p><b>Образ базы здесь НЕСУЩИЙ, а не унаследованный у соседа по форме.</b>
 * Обе таблицы фактов — гипертаблицы Timescale, и на стоковом Postgres
 * миграция {@code V1} не накатывается вовсе
 * (docs/models/domain/other/StatisticsFact.md §Персистентность): субстрат на
 * стоковом образе отказал бы на подъёме первого же контекста, а не на клетке.
 *
 * <p><b>Контейнера хранилища секретов здесь НЕТ, и это следствие предмета, а
 * не экономия.</b> Ключей площадки статистика не касается ни одной тропой, а
 * учётные данные базы приезжают свойствами прогона
 * (.claude/tests/cases/statistics.md §«Чем достаются выходы»).
 *
 * <p><b>Брокер нужен ОБЕИМИ сторонами.</b> Единственный вход сервиса —
 * события: кейс кладёт запись в тему и смотрит, что из неё вышло. Поэтому
 * контейнер брокера служит здесь и подателем входа, и наблюдателем смещений
 * группы.
 *
 * <p><b>Тема у ШТАТНОЙ подписки ОДНА, и это свойство предмета.</b> Класс несом
 * статистикой ровно тогда, когда его содержимое несёт операнд объявленного
 * зерна, и сегодня такие классы производит один сосед
 * (docs/models/domain/other/StatisticsFact.md §«Признак несомого класса»).
 * Отсюда следствие для формы клеток: РАДИУС остановки приёма — что флаг лёг у
 * своей пары, а не у группы — на одной паре не выразим вовсе, и кейс
 * {@code B2.15} назван непрогоняемым по той же причине.
 *
 * <p><b>Это не значит, что двух тем не бывает ни у одного контекста.</b>
 * Радиус остановки требует второй темы, по которой приём ИДЁТ, — то есть
 * второго производителя несомых классов, которого нет. Состав же пар не
 * требует от второй темы ничего, кроме присутствия в подписке: сколько тем
 * названо в ключе, решает окружение, и клетки о разведении подписки с
 * назначением и о замене состава рядов берут вторую тему осью конфигурации
 * ({@link #ownSecondTopic(String)}).
 *
 * <p><b>Темы заводятся ЯВНО, до первого контекста.</b> Автозаведение темы
 * первым спросом отдаёт раскладку не сразу — лидер партии на этот момент ещё
 * не выбран, — а тик состояния приёма мерит живость НАЗНАЧЕННЫМИ партициями:
 * контекст, поднявшийся раньше темы, получил бы пустое назначение и тик,
 * молчащий по причине, которой кейс не ставил.
 *
 * <p><b>Субстрат общий на прогон, а не на кейс:</b> подъём контейнера стои́т
 * секунды, и платить их за каждую клетку незачем.
 *
 * <p><b>Расписание в прогоне глушится ВЫРАЖЕНИЕМ такта, а не
 * выключателем.</b> Выключатели тика приёма и тика пересчёта суть ВХОДЫ
 * клеток своих групп, и глушить ими расписание значило бы отнять у них
 * предмет. У тика приёма такт выражается длительностью, и часовая не даёт за
 * прогон ни одного повторения; у пересчёта — выражением CRON, и «раз в год»
 * не даёт ни одного вовсе. Такт подаёт сам кейс — прямым вызовом метода
 * джобы, потому что ручного фасада у сервиса нет намеренно (решение 6).
 *
 * <p><b>Осей здесь ровно столько, сколько читают написанные клетки.</b>
 * Ключ, заведённый под ненаписанную группу, был бы формой раньше предмета
 * (.claude/rules/design-simplicity.md), и разойтись с сервисом он успел бы
 * раньше первого своего читателя.
 */
final class StatisticsSubstrate {

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

    /** Тема фактов торгового ядра: единственный производитель несомых классов. */
    static final String CORE_TOPIC = "trading-core.facts";

    /** Имя группы потребителя штатного прогона. */
    static final String CONSUMER_GROUP = "statistics.facts";

    /** Ключ имени группы потребителя. */
    static final String CONSUMER_GROUP_KEY = "reception.group-id";

    /** Ключ подписки: форма значения — скаляр через запятую. */
    static final String TOPICS_KEY = "reception.topics";

    /**
     * Ключ паузы между повторами доставки.
     *
     * <p><b>Сжимают её клетки, чей вход — ЧИСЛО попыток, а не их
     * частота:</b> число попыток не ограничено ничем по построению
     * ({@code ReceptionErrorHandler}), а пауза решает лишь, как часто повтор
     * бьётся в брокер и базу. Сжатая, она даёт сотню попыток за окно, которое
     * прогон может себе позволить.
     */
    static final String RETRY_INTERVAL_KEY = "reception.retry-interval";

    /** Ключ выключателя тика состояния приёма: им снимается состав пар. */
    static final String STATE_TICK_ENABLED_KEY = "reception.state-tick-enabled";

    /**
     * Ключ допустимого возраста строки состояния приёма.
     *
     * <p><b>Сдвигает его клетка о неживом приёме</b>: истечение свежести
     * строки и есть предмет её последнего ожидания — молчащий измеритель
     * обязан кончиться ответом «не утверждаема», а не молчаливым «дыры
     * нет». При штатных пяти минутах клетка мерила бы то же самое, платя за
     * это пятью минутами прогона.
     */
    static final String STATE_MAX_AGE_KEY = "reception.state-max-age";

    /**
     * Ключ ширины окна пересчёта агрегатов.
     *
     * <p><b>Сдвигают его клетки ОКНА</b> ({@code B7.1}, {@code B7.15} —
     * {@code B7.17}): их ожидание говорит, что ширина взята ВЕЛИЧИНОЙ
     * КОНФИГУРАЦИИ, а не константой кода, и на штатных семи сутках оно не
     * выразимо вовсе — они совпадают с умолчанием сервиса дословно, то есть
     * клетка прошла бы и на реализации, читающей константу. Тот же довод,
     * что у допустимого возраста строки состояния.
     */
    static final String RECOMPUTE_WINDOW_KEY = "jobs.aggregate-recompute.window-days";

    /**
     * Число суток окна пересчёта в штатном положении осей.
     *
     * <p><b>Объявлено здесь ВЕЛИЧИНОЙ, а не унаследовано умолчанием
     * сервиса.</b> Окно решает, какие сутки клетка вправе выбрать МОМЕНТОМ
     * события: факт, положенный вне окна, строки агрегата не получит ни при
     * каком числе тактов. Унаследованное умолчание пришлось бы прочитать из
     * ресурса сервиса, то есть заглянуть ящику внутрь; объявленная ось
     * читается как вход.
     *
     * <p><b>Значение совпадает с умолчанием сервиса, и это не дубль, а
     * объявление.</b> Сдвигать его незачем: проход идёт посуточными
     * порциями, и семь порций прогону ничего не стоя́т.
     */
    static final Integer RECOMPUTE_WINDOW_DAYS = 7;

    /**
     * Допустимый возраст строки состояния приёма в штатном положении осей.
     *
     * <p><b>Объявлен здесь ВЕЛИЧИНОЙ по тому же доводу.</b> Свежесть строки
     * состояния — третий конъюнкт предиката непрерывности
     * (docs/spec/durable-reception.json, {@code receptionStateFresh}), а
     * предикат едет с каждой выдачей чтения и читается клетками этой группы:
     * возраст, унаследованный умолчанием, делал бы их ожидание зависящим от
     * ресурса сервиса.
     *
     * <p><b>Клетки читают его НАПРЯМУЮ</b>, а не повторяют своей константой:
     * величина одна, и вторая её запись разошлась бы с первой при первом же
     * сдвиге оси (.claude/rules/carrier-levels.md).
     */
    static final Duration STATE_MAX_AGE = Duration.ofMinutes(5);

    /** Выражение такта, до которого прогон не доживает. */
    private static final String NEVER = "0 0 0 1 1 *";

    /** Такт тика приёма, второго повторения которого за прогон не бывает. */
    private static final String HOURLY = "1h";

    /** Потолок ожидания заведения тем. */
    private static final Duration ADMIN_TIMEOUT = Duration.ofSeconds(60);

    private static final PostgreSQLContainer DATABASE = startDatabase();

    private static final KafkaContainer BROKER = startBroker();

    private StatisticsSubstrate() {
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
     * накладываются на него до регистрации: два {@code add} по одному ключу
     * оставляли бы исход зависящим от порядка обхода, которого контракт
     * реестра не обещает.
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
        values.put("statistics.persistence.url", DATABASE.getJdbcUrl());
        values.put("statistics.persistence.username", DATABASE.getUsername());
        values.put("statistics.persistence.password", DATABASE.getPassword());
        // ПОТОЛОК ПУЛА ЗАДАН, и это не настройка «для скорости». Контекстов у
        // прогона столько, сколько у ящика положений осей конфигурации, и
        // КАЖДЫЙ держит свой пул к одному и тому же контейнеру; при умолчании
        // сервиса в пять соединений полтора десятка контекстов исчерпывают
        // лимит клиентов базы, и падает при этом не та клетка, которая его
        // исчерпала, а следующая.
        values.put("statistics.persistence.max-pool-size", "3");
        values.put("spring.security.oauth2.resourceserver.jwt.issuer-uri",
                IdentityStub.stub().issuer());
        values.put("reception.bootstrap-servers", BROKER.getBootstrapServers());
        values.put(CONSUMER_GROUP_KEY, CONSUMER_GROUP);
        values.put(TOPICS_KEY, CORE_TOPIC);
        values.put(STATE_TICK_ENABLED_KEY, "true");
        values.put("reception.state-tick-interval", HOURLY);
        values.put(STATE_MAX_AGE_KEY, STATE_MAX_AGE.toMinutes() + "m");
        values.put("jobs.aggregate-recompute.enabled", "true");
        values.put("jobs.aggregate-recompute.cron", NEVER);
        values.put(RECOMPUTE_WINDOW_KEY, String.valueOf(RECOMPUTE_WINDOW_DAYS));
        values.put("platform.environment.name", "dev");
        return values;
    }

    /**
     * Свойства контекста клетки, у которой СВОЯ группа и СВОЯ тема.
     *
     * <p><b>Своя пара нужна не для порядка, а потому что клетка ЛОМАЕТ
     * приём.</b> Отравленное сообщение повторяется без ограничения числа
     * попыток и занимает единственный поток слушателя; контекст, в котором
     * оно легло, больше не принимает ничего. Две такие клетки в одном
     * контексте невозможны: вторая наблюдала бы исход первой, и её
     * отрицания («строки нет», «смещение стои́т») сошлись бы по ложной
     * причине — сообщение до обработки просто не дошло бы.
     *
     * <p><b>Своя ГРУППА и своя ТЕМА берутся вместе.</b> Своя группа читает
     * тему с начала, поэтому без своей темы одиночка переиграла бы всё, что
     * положили соседние классы прогона, и писала бы их строки в базу
     * параллельно чужой клетке.
     *
     * @param registry  реестр свойств контекста
     * @param slug      краткое имя клетки: из него строятся группа и тема
     * @param overrides прочие оси, которые клетка сдвигает
     */
    static void registerOwn(DynamicPropertyRegistry registry, String slug,
                            Map<String, String> overrides) {
        createTopics(BROKER.getBootstrapServers(), List.of(ownTopic(slug)));
        Map<String, String> values = new LinkedHashMap<>();
        values.put(CONSUMER_GROUP_KEY, CONSUMER_GROUP + "." + slug);
        values.put(TOPICS_KEY, ownTopic(slug));
        values.putAll(overrides);
        register(registry, values);
    }

    /** Своя пара «группа + тема» при штатном положении прочих осей. */
    static void registerOwn(DynamicPropertyRegistry registry, String slug) {
        registerOwn(registry, slug, Map.of());
    }

    /**
     * Свойства контекста клетки, у которой объявленная подписка и заведённые
     * темы РАЗВЕДЕНЫ.
     *
     * <p><b>Разведение — вход двух клеток, и ставится оно единственным
     * детерминированным поводом.</b> Состав пар берётся из объявленной
     * подписки, а не из назначенных партиций
     * (docs/components/ReceptionStateJob.md §«Состав пар берётся из
     * объявленной подписки, а не из назначения»), и клетка о разведении
     * обязана поставить подписку шире назначения. Тема, у брокера не
     * заведённая, назначения не даёт ни одной партицией; второй повод —
     * отдача партиции другой реплике той же группы — зависит от
     * назначающего и от протокола группы, то есть сделал бы предусловие
     * гонкой.
     *
     * <p><b>Сама возможность такого состава стои́т на том, что брокер
     * субстрата тем не заводит сам</b> ({@link #startBroker()}): при
     * автозаведении объявленная тема появилась бы первым же спросом
     * метаданных, и разведения не получилось бы вовсе.
     *
     * <p><b>Вторая тема подписки здесь — ось КОНФИГУРАЦИИ, а не второй
     * производитель.</b> Форма значения ключа — скаляр через запятую, и
     * сколько тем в нём названо, решает окружение
     * ({@code application.yaml}); что сегодня операнды зерна несёт один
     * сосед, говорит о СОСТАВЕ подписки в проде, а не о том, скольким
     * темам тик умеет вести строки.
     *
     * @param registry  реестр свойств контекста
     * @param slug      краткое имя клетки: из него строятся группа и темы
     * @param created   темы, заводимые у брокера
     * @param declared  темы объявленной подписки
     * @param overrides прочие оси, которые клетка сдвигает
     */
    static void registerOwn(DynamicPropertyRegistry registry, String slug, List<String> created,
                            List<String> declared, Map<String, String> overrides) {
        createTopics(BROKER.getBootstrapServers(), created);
        Map<String, String> values = new LinkedHashMap<>(overrides);
        values.put(CONSUMER_GROUP_KEY, CONSUMER_GROUP + "." + slug);
        values.put(TOPICS_KEY, String.join(",", declared));
        register(registry, values);
    }

    /** Своя тема производителя у названной клетки. */
    static String ownTopic(String slug) {
        return slug + ".core";
    }

    /**
     * Вторая тема подписки у названной клетки.
     *
     * <p><b>Производителя у неё нет ни одного, и это не пробел.</b> Её
     * единственная работа — быть ВТОРЫМ членом состава: без второго члена
     * ни разведение подписки с назначением, ни замена состава рядов не
     * выразимы вовсе — снятие единственной темы делает приём неживым, то
     * есть подменяет предмет соседней клеткой.
     */
    static String ownSecondTopic(String slug) {
        return slug + ".second";
    }

    /** Контейнер базы на образе стенда. */
    private static PostgreSQLContainer startDatabase() {
        PostgreSQLContainer container = new PostgreSQLContainer(
                DockerImageName.parse(DATABASE_IMAGE).asCompatibleSubstituteFor("postgres"))
                .withDatabaseName("statistics")
                .withUsername("statistics")
                .withPassword("statistics");
        container.start();
        return container;
    }

    /**
     * Брокер субстрата, который тем НЕ ЗАВОДИТ сам.
     *
     * <p><b>Автозаведение снято не ради чистоты, а потому что оно отняло бы
     * у клеток вход.</b> Темы субстрат заводит явно и до первого контекста;
     * клетке о неживом приёме автозаведение вернуло бы удалённую тему
     * обратно — то есть оживило бы приём ровно там, где клетка его хоронит, —
     * а клетке о разведении подписки и назначения нужна тема, объявленная
     * подпиской и у брокера отсутствующая.
     */
    private static KafkaContainer startBroker() {
        KafkaContainer container = new KafkaContainer(DockerImageName.parse(BROKER_IMAGE))
                .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "false");
        container.start();
        createTopics(container.getBootstrapServers(), List.of(CORE_TOPIC));
        return container;
    }

    /**
     * Заводит темы подписки до первого контекста.
     *
     * <p><b>Партиция у каждой одна, и это не умолчание.</b> Порядок сообщений
     * между партициями брокер не обещает ничем, а клетки о моменте последнего
     * принятого события подают записи заведомо упорядоченными; две партиции
     * сделали бы их ожидание гонкой, а не утверждением о поведении.
     *
     * <p><b>Заведение идемпотентно:</b> брокер субстрата общий на прогон, а
     * контекст класса каркас теста кэширует — второй экземпляр того же класса
     * застал бы тему уже заведённой.
     */
    private static void createTopics(String bootstrapServers, List<String> names) {
        Map<String, Object> settings = new HashMap<>();
        settings.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        try (Admin admin = Admin.create(settings)) {
            Set<String> known = admin.listTopics().names()
                    .get(ADMIN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            List<NewTopic> absent = names.stream()
                    .filter(name -> isFalse(known.contains(name)))
                    .map(name -> new NewTopic(name, 1, (short) 1))
                    .toList();
            if (absent.isEmpty()) {
                return;
            }
            admin.createTopics(absent).all().get(ADMIN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Заведение тем субстрата прервано", failure);
        } catch (Exception failure) {
            throw new IllegalStateException("Брокер субстрата не завёл тем подписки", failure);
        }
    }
}
