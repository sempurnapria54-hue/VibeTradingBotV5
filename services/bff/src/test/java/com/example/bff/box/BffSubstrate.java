package com.example.bff.box;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Субстрат чёрного ящика `bff`: контейнер брокера, стаб провайдера
 * идентичности и стаб шести владельцев за периметром
 * (.claude/decisions/test-contour-design-pass.md, решения 2, 3, 4).
 *
 * <p><b>Контейнер здесь ОДИН, и это следствие предмета, а не
 * экономия.</b> Своей базы у периметра нет ни одной
 * (docs/architecture/services/bff.md §«Что держит истиной»), ключей
 * площадки он не касается — хранилище секретов ему не нужно, а
 * единственный его секрет (общий у реплик секрет подписи билета)
 * приезжает осью конфигурации. Это первый из восьми предметов уровня 1,
 * чей ящик поднимается БЕЗ контейнера базы
 * (.claude/tests/cases/bff.md §«Чем достаются выходы»).
 *
 * <p><b>Брокер нужен ВХОДОМ, и это ось предмета.</b> Периметр только
 * потребляет: запись темы есть вход раздачи в браузер, а собственных
 * публикаций у него нет ни одной. Поэтому предусловие клеток потока
 * ставится СООБЩЕНИЕМ, а не поверхностью, — обратно тому, как оно
 * ставится у соседних ящиков.
 *
 * <p><b>Образ брокера пинится версией клиента дерева.</b> Манифест стенда
 * версии не называет вовсе — её выбирает оператор, — поэтому единственная
 * запись, с которой совпадение проверяемо, есть версия
 * {@code kafka-clients} в дереве зависимостей; пин на неё и стои́т, а
 * совпадение сверяет проба ({@link SubstrateImagePinTest}).
 *
 * <p><b>Субстрат общий на прогон, а не на кейс:</b> подъём контейнера
 * стои́т секунды, и платить их за каждую клетку незачем.
 *
 * <p><b>Расписание пульса в прогоне глушится ВЫКЛЮЧАТЕЛЕМ, и это не
 * отнимает предмета ни у одной клетки.</b> У тика пульса ручного фасада
 * нет намеренно (.claude/rules/codestyle.md §Джобы), и решение 6
 * дизайн-прохода называет прямой вызов метода джобы единственной точкой,
 * где кейс уровня 1 касается бина, — то есть ВХОДОМ, а не подменой.
 * Выключатель при этом остаётся ВХОДОМ клетки {@code B5.6}: она поднимает
 * свой контекст с выключенным тиком и предъявляет молчание того же
 * прямого вызова. Штатное же положение — выключатель включён, а такт
 * назначен длиннее прогона: иначе в поток попадал бы пульс, которого
 * клетка не звала.
 */
final class BffSubstrate {

    /**
     * Образ брокера: та же версия, что у клиента в дереве зависимостей
     * ({@code kafka-clients}). Совпадение сверяет проба
     * ({@link SubstrateImagePinTest}).
     */
    static final String BROKER_IMAGE = "apache/kafka:4.1.1";

    /** Ключ адреса брокера: им перекрывается тропа потребления. */
    static final String BROKER_ADDRESS_KEY = "broker.bootstrap-servers";

    /** Ключ точки провайдера идентичности, по которой проверяется входящий токен. */
    static final String ISSUER_KEY = "spring.security.oauth2.resourceserver.jwt.issuer-uri";

    /** Ключ шаблона адреса владельца: единственная ось адресации шести владельцев. */
    static final String OWNER_URL_TEMPLATE_KEY = "perimeter.owner-url-template";

    /** Ключ бюджета повторов чтения. */
    static final String READ_RETRIES_KEY = "perimeter.read-retries";

    /** Ключ срока годности записи кэша членств. */
    static final String MEMBERSHIP_CACHE_TTL_KEY = "perimeter.membership.cache-ttl";

    /** Ключ перечня тем подписки. */
    static final String STREAM_TOPICS_KEY = "perimeter.stream.topics";

    /** Ключ ширины окна переигрывания. */
    static final String REPLAY_WINDOW_KEY = "perimeter.stream.replay-window";

    /** Ключ такта пульса. */
    static final String PULSE_INTERVAL_KEY = "perimeter.stream.pulse-interval";

    /** Ключ выключателя тика пульса: вход клетки о выключенном тике. */
    static final String PULSE_ENABLED_KEY = "perimeter.stream.pulse-enabled";

    /** Ключ срока жизни соединения подписки. */
    static final String CONNECTION_TIMEOUT_KEY = "perimeter.stream.connection-timeout";

    /** Ключ потолка одновременных подписок тенанта. */
    static final String MAX_SUBSCRIPTIONS_KEY = "perimeter.stream.max-subscriptions-per-tenant";

    /** Ключ секрета подписи билета. */
    static final String TICKET_SECRET_KEY = "perimeter.ticket.secret";

    /** Ключ срока билета. */
    static final String TICKET_TTL_KEY = "perimeter.ticket.ttl";

    /** Секрет подписи билета штатного прогона. */
    static final String TICKET_SECRET = "box-ticket-secret-4c19";

    /**
     * Такт пульса, до которого прогон не доживает: тик подаёт сам кейс
     * прямым вызовом метода джобы (см. шапку класса).
     */
    static final String NEVER = "365d";

    private static final KafkaContainer BROKER = startBroker();

    private BffSubstrate() {
    }

    /** Адрес брокера общего субстрата: им ходит и свой клиент прогона. */
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
        values.put(BROKER_ADDRESS_KEY, BROKER.getBootstrapServers());
        values.put(ISSUER_KEY, IdentityStub.stub().issuer());
        values.put(OWNER_URL_TEMPLATE_KEY, OwnerStub.stub().urlTemplate());
        values.put(READ_RETRIES_KEY, "1");
        values.put(MEMBERSHIP_CACHE_TTL_KEY, "60s");
        values.put(STREAM_TOPICS_KEY, Wire.CORE_TOPIC + "," + Wire.STRATEGIES_TOPIC);
        values.put(REPLAY_WINDOW_KEY, "200");
        values.put(PULSE_INTERVAL_KEY, NEVER);
        values.put(PULSE_ENABLED_KEY, "true");
        values.put(CONNECTION_TIMEOUT_KEY, "30m");
        values.put(MAX_SUBSCRIPTIONS_KEY, "32");
        values.put(TICKET_SECRET_KEY, TICKET_SECRET);
        values.put(TICKET_TTL_KEY, "10m");
        return values;
    }

    private static KafkaContainer startBroker() {
        KafkaContainer container = new KafkaContainer(DockerImageName.parse(BROKER_IMAGE));
        container.start();
        return container;
    }
}
