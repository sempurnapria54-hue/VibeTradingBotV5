package com.example.tests.e2e;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.GroupListing;
import org.apache.kafka.clients.admin.ListGroupsOptions;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.awaitility.Awaitility;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

/**
 * Тропа: пять сторон процессами, стабы соседей, не являющихся стороной, и
 * субстрат (.claude/tests/cases/e2e-strategy-to-deal.md §«Чем достаются
 * выходы»).
 *
 * <p><b>Адреса сторон друг другу — их реальные порты.</b> Владелец
 * определений зовёт ядро по {@code neighbours.trading-core.base-url}, ядро
 * зовёт коннектор по {@code neighbours.connector.base-url}; стаба между
 * сторонами нет ни одного, иначе проверялся бы не стык.
 *
 * <p><b>Расписание у сторон с фасадом выключено:</b> тик подаётся их
 * поверхностью, и ход тропы подаёт тест. У {@code audit} и
 * {@code statistics} фасада нет намеренно; их тики остаются расписанием,
 * а такт задаётся ключами тропы (.claude/skills/test-code.md §«Уровень 3 —
 * сквозной набор»).
 *
 * <p><b>Предусловия ставятся ходами тропы</b> — тиком синка проекций,
 * простановкой чисел поверхностью ядра, тиком синка ставок, — а не
 * записью в базу, и каждое помнит, поставлено ли оно, чтобы кейс брал
 * ровно те, которые называет.
 *
 * <p><b>Ходы изолированы парой «тенант, счёт», а не пересозданием
 * стороны.</b> Ответы площадки и чтение её журнала — в области ключа счёта
 * ходов ({@link Stub#scope(String, String)}), чтения баз сторон — условиями
 * пары ({@link #BY_DEAL}, {@link #BY_ACCOUNT}, {@link #BY_ORDER},
 * {@link #BY_TENANT}) и счётом {@link #rows(Party, String)}; кейс, которому
 * нужна пара без сделки, берёт свежую пару ({@link #pairWithoutDeal()}), а
 * не свежее развёртывание ядра. Сделки прежних пар на стенде остаются и
 * видят свою площадку: их ответы стаб держит под их областью
 * (.claude/skills/test-code.md §«Уровень 3 — сквозной набор»).
 */
public final class Trail implements AutoCloseable {

    public static final String TENANT = "T1";

    public static final String ACCOUNT = "okx-main-account";

    public static final String INSTRUMENT = "eth-usdt-swap";

    public static final String EXTERNAL_INSTRUMENT = "ETH-USDT-SWAP";

    public static final String CONTOUR = "DEMO";

    public static final String TENANT_HEADER = "X-Tenant-Id";

    public static final String CORE = "/api/v1/trading-core";

    public static final String STRATEGIES = "/api/v1/strategies";

    public static final String PEER_ACCOUNTS = "/api/v1/auth/exchange-accounts";

    public static final String PEER_INSTRUMENTS = "/api/v1/market-data/instruments";

    public static final String EXCHANGE_FEE = "/api/v5/account/trade-fee";

    public static final String EXCHANGE_TIME = "/api/v5/public/time";

    public static final String PEER_FEATURES = PEER_INSTRUMENTS + "/" + INSTRUMENT + "/features";

    public static final String EXCHANGE_BALANCE = "/api/v5/account/balance";

    /** Конфигурация счёта у площадки: вторая половина снимка средств — режим счёта и режим позиций. */
    public static final String EXCHANGE_ACCOUNT_CONFIG = "/api/v5/account/config";

    public static final String EXCHANGE_LEVERAGE = "/api/v5/account/set-leverage";

    public static final String EXCHANGE_ORDER = "/api/v5/trade/order";

    public static final String EXCHANGE_ALGO_PENDING = "/api/v5/trade/orders-algo-pending";

    public static final String EXCHANGE_POSITIONS = "/api/v5/account/positions";

    /** Живые заявки счёта у площадки: второй срез проактивной детекции ядра. */
    public static final String EXCHANGE_ORDERS_PENDING = "/api/v5/trade/orders-pending";

    public static final String EXTERNAL_ORDER = "okx-order-1";

    public static final String EXTERNAL_PROTECTION = "okx-algo-1";

    public static final String ENTRY_PRICE = "2000";

    /**
     * Условие строки ядра, несущей {@code deal_id}, — сделка счёта ходов;
     * параметр — идентичность счёта ({@link #account()}).
     */
    public static final String BY_DEAL = "deal_id in (select deals.id from deals join exchange_accounts"
            + " on exchange_accounts.id = deals.exchange_account_id where exchange_accounts.internal_id = ?)";

    /** Условие строки ядра, несущей {@code exchange_account_id}, — счёт ходов; параметр — идентичность счёта. */
    public static final String BY_ACCOUNT =
            "exchange_account_id in (select id from exchange_accounts where internal_id = ?)";

    /** Условие строки условной заявки, встроенной в заявку, — заявка счёта ходов; параметр — идентичность счёта. */
    public static final String BY_ORDER = "order_id in (select id from orders where " + BY_DEAL + ")";

    /** Условие строки, несущей {@code tenant_id}, — тенант ходов; параметр — тенант ({@link #tenant()}). */
    public static final String BY_TENANT = "tenant_id = ?";

    /** Префикс пути коннектора, называющего счёт: по нему журнал доступа разводит счета. */
    public static final String CONNECTOR_ACCOUNTS = "/api/v1/accounts/";

    /** Заголовок подписанного запроса площадке, называющий ключ счёта: им стаб площадки разводит счета. */
    public static final String ACCESS_KEY = "OK-ACCESS-KEY";

    private static final String NEVER = "0 0 0 1 1 *";

    /** Допуск возраста наблюдения детекцией у ядра стенда: заведомо длиннее прогона. */
    private static final String OBSERVATION_MAX_AGE = "30d";

    /** Пустой срез площадки: ответ без строк. */
    private static final String EMPTY_SLICE = """
            {"code": "0", "msg": "", "data": []}
            """;

    private static final Integer OWNER_PORT = 8080;

    private static final Integer LOOPBACK_BASE = 21;

    private static final String MARKET_DATA_ADDRESS = "127.0.0.40";

    private static final Duration TICK_WAIT = Duration.ofSeconds(90);

    private static final Long ACKNOWLEDGED_AT = 1758240000000L;

    private static final String REFUSE_TRIGGER = "trail_refuse_insert";

    private static final List<String> TERMINAL_DEAL = List.of("CLOSED", "EMERGENCY_CLOSED");

    private static final String REFERENCE_DEFINITION =
            "services/strategies/src/test/resources/strategy-examples/trend-following-ema.json";

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private final String name;
    private final Layout layout;
    private final KafkaContainer broker;
    private final IdentityStub identity = new IdentityStub();
    private final Stub auth = new Stub("auth");
    private final Stub marketData;
    private final Stub exchange = new Stub("okx");
    private final Map<Party, Database> databases = new EnumMap<>(Party.class);
    private final Map<Party, Side> sides = new EnumMap<>(Party.class);
    private final Map<Party, Integer> accessMarks = new EnumMap<>(Party.class);
    private final Map<String, Map<Integer, Long>> topicMarks = new HashMap<>();
    private final Map<String, String> registry = new LinkedHashMap<>();
    private final Map<String, Supplier<String>> startedSeries = new LinkedHashMap<>();
    private Integer freshPairs = 0;
    private Boolean projectionsSynced = Boolean.FALSE;
    private Boolean riskAppetiteSet = Boolean.FALSE;
    private Boolean leverageAssigned = Boolean.FALSE;
    private Boolean feeRatesSynced = Boolean.FALSE;
    private Boolean detectionObserved = Boolean.FALSE;
    private String tenant = TENANT;
    private String account = ACCOUNT;
    private Long acknowledgedAt = ACKNOWLEDGED_AT;

    private Trail(String name, Layout layout) {
        this.name = name;
        this.layout = layout;
        Workspace.clear(name);
        this.marketData = Objects.equals(layout, Layout.PERIMETER)
                ? new Stub("market-data", MARKET_DATA_ADDRESS, OWNER_PORT)
                : new Stub("market-data");
        this.broker = Substrate.broker();
        for (Party party : layout.parties()) {
            if (isTrue(party.hasDatabase())) {
                databases.put(party, Substrate.database(name + "_" + party.databaseSuffix()));
            }
        }
        for (Party party : layout.parties()) {
            sides.put(party, Objects.equals(layout, Layout.PERIMETER)
                    ? new Side(party.module(), name, settingsOf(party), loopbackOf(party), OWNER_PORT)
                    : new Side(party.module(), name, settingsOf(party)));
        }
        if (Objects.equals(layout, Layout.PERIMETER)) {
            side(Party.BFF).jvmOption("-Djdk.net.hosts.file=" + ownerNames());
        }
        registry.put(ACCOUNT, TENANT);
        exchange.shared(EXCHANGE_TIME);
        exchange.scope(ACCESS_KEY, Substrate.apiKeyOf(ACCOUNT));
        neighboursAnswer();
        Substrate.putAccountKeys(ACCOUNT, CONTOUR);
    }

    /**
     * Открывает тропу и поднимает все пять сторон на пустых базах.
     *
     * @param name имя тропы — префикс её баз и каталог журналов
     * @return тропа с поднятыми сторонами и без единого поставленного предусловия
     */
    public static Trail open(String name) {
        return open(name, Layout.DEAL_PATH);
    }

    /**
     * Открывает тропу периметра: семь сторон, {@code auth} среди них,
     * адресация владельцев — конвенцией кластера.
     *
     * @param name имя тропы
     * @return тропа с поднятыми сторонами
     */
    public static Trail openPerimeter(String name) {
        return open(name, Layout.PERIMETER);
    }

    private static Trail open(String name, Layout layout) {
        Trail trail = new Trail(name.toLowerCase(Locale.ROOT), layout);
        trail.sides.values().forEach(Side::launch);
        trail.sides.values().forEach(Side::awaitUp);
        trail.forgetTraces();
        return trail;
    }

    /** Имя тропы — префикс её баз. */
    public String name() {
        return name;
    }

    /** Сторона тропы. */
    public Side side(Party party) {
        return sides.get(party);
    }

    /** База стороны. */
    public Database database(Party party) {
        return databases.get(party);
    }

    /** Стаб владельца реестра счетов. */
    public Stub auth() {
        return auth;
    }

    /** Стаб владельца каталога и рыночных данных. */
    public Stub marketData() {
        return marketData;
    }

    /** Стаб площадки. */
    public Stub exchange() {
        return exchange;
    }

    /** Стаб провайдера идентичности. */
    public IdentityStub identity() {
        return identity;
    }

    /**
     * Останавливает сторону: её следа после этого нет — процесса нет.
     *
     * @param party сторона
     */
    public void stop(Party party) {
        side(party).stop();
    }

    /** Поднимает остановленную сторону на той же базе и том же порту. */
    public void start(Party party) {
        side(party).launch();
        side(party).awaitUp();
    }

    /**
     * Поднимает сторону заново на той же базе с подъёмной конфигурацией и
     * названными перекрытиями: ключи, перекрытые прежними кейсами, сняты.
     *
     * <p>Кейс стоит на своём предусловии, а не на остатке соседнего: ключ,
     * перекрытый ходом, иначе переживает перезапуск ({@link Side#set}).
     *
     * @param party     сторона
     * @param overrides ключи конфигурации кейса
     */
    public void restartWith(Party party, Map<String, String> overrides) {
        side(party).stop();
        side(party).reset(settingsOf(party));
        overrides.forEach(side(party)::set);
        start(party);
    }

    /**
     * Поднимает сторону заново на ПУСТОЙ базе — свежее развёртывание.
     *
     * <p>Предусловия, которые сторона держала у себя, этим сняты, и тропа
     * это помнит: следующий кейс поставит их заново своим ходом.
     */
    public void renew(Party party) {
        side(party).stop();
        if (isTrue(party.hasDatabase())) {
            databases.put(party, Substrate.database(name + "_" + party.databaseSuffix()));
        }
        settingsOf(party).forEach(side(party)::set);
        start(party);
        if (Objects.equals(party, Party.TRADING_CORE)) {
            projectionsSynced = Boolean.FALSE;
            riskAppetiteSet = Boolean.FALSE;
            leverageAssigned = Boolean.FALSE;
            feeRatesSynced = Boolean.FALSE;
            detectionObserved = Boolean.FALSE;
        }
    }

    // ---------------------------------------------------------------- предусловия

    /**
     * Ходы тропы идут под названными тенантом и счётом.
     *
     * <p>У тропы сделки счёт отдаёт стаб владельца реестра. У тропы периметра
     * владелец реестра — сторона: тенанта заводит первый ход самой тропы, а
     * идентичность счёта выдаёт его регистрация, и пролог идёт под ними
     * (.claude/tests/cases/e2e-perimeter-read.md §«Предусловия тропы — что
     * лежит до первого хода»).
     *
     * <p>Площадка переходит в область ключа счёта и отвечает ему с первого
     * хода; предусловия, поставленные прежнему счёту, новому не принадлежат, и
     * тропа это помнит; начатые ряды фактов начинаются и новой паре.
     *
     * @param tenantInternalId  тенант ходов
     * @param accountInternalId биржевой счёт ходов
     * @param accessKey         ключ API счёта — им подписан запрос площадке
     */
    public void under(String tenantInternalId, String accountInternalId, String accessKey) {
        this.tenant = tenantInternalId;
        this.account = accountInternalId;
        exchange.scope(ACCESS_KEY, accessKey);
        exchangeServesAccount();
        projectionsSynced = Boolean.FALSE;
        riskAppetiteSet = Boolean.FALSE;
        leverageAssigned = Boolean.FALSE;
        feeRatesSynced = Boolean.FALSE;
        detectionObserved = Boolean.FALSE;
        startedSeries.forEach((eventType, payload) -> seriesStartedYesterday(eventType, payload.get()));
    }

    /**
     * Пара без сделки — свежей парой «тенант, счёт», а не свежим
     * развёртыванием ядра; общие предусловия после этого поставлены.
     *
     * <p>Сделки прежнего счёта остаются, и снять их нечем, кроме их
     * собственного терминала; но другой счёт — другая пара, и проход
     * оркестратора их ведёт под их областью площадки. Активные определения
     * прежнего тенанта сняты до перехода: иначе сканер открыл бы по ним
     * сделку посреди чужого кейса.
     *
     * <p>Свежая пара заводится так, как её завёл бы владелец реестра: ключи в
     * хранилище, строка в реестре у его стаба; прежние строки реестра
     * остаются — синк сводит проекцию с реестром целиком.
     */
    public void pairWithoutDeal() {
        if (isTrue(projectionsSynced) && isFalse(deals().isEmpty())) {
            freshPair();
        }
        commonPreconditions();
    }

    /**
     * Передаёт стенд следующему классу группы: его ходы начинаются так, будто
     * стенд поднят для него, — кроме строк прежних классов, которые разводит
     * пара ходов.
     *
     * <p>У тропы сделки класс получает свежую пару; у тропы периметра пару
     * заводит пролог самого класса. Ряды фактов, начатые прежним классом, и
     * его момент подтверждения площадки забыты; ответы соседей, не
     * являющихся стороной, — умолчания тропы; следы забыты
     * (.claude/skills/test-code.md §«Уровень 3 — сквозной набор»).
     */
    public void handOver() {
        startedSeries.clear();
        acknowledgedAt = ACKNOWLEDGED_AT;
        auth.forgetScenarios();
        marketData.forgetScenarios();
        neighboursAnswer();
        if (Objects.equals(layout, Layout.DEAL_PATH)) {
            freshPair();
        }
        forgetTraces();
    }

    /**
     * Возвращает сторонам подъёмную конфигурацию: сторона, оставленная
     * классом остановленной либо с перекрытым ключом, поднимается заново на
     * своей базе с конфигурацией подъёма тропы.
     *
     * <p>Это починка, а не порча: состояние стороны живёт в её базе, и
     * перезапуск его не трогает — ровно как перезапуски внутри класса.
     *
     * @return что поднято заново — сторона и перекрытые ключи
     */
    public List<String> restoreSides() {
        List<String> restored = new ArrayList<>();
        sides.forEach((party, side) -> {
            Map<String, String> origin = settingsOf(party);
            Map<String, String> current = side.settings();
            Set<String> keys = new TreeSet<>(origin.keySet());
            keys.addAll(current.keySet());
            keys.removeIf(key -> Objects.equals(origin.get(key), current.get(key)));
            if (isTrue(side.isAlive()) && keys.isEmpty()) {
                return;
            }
            side.stop();
            side.reset(origin);
            start(party);
            restored.add(party.module() + (keys.isEmpty() ? " (была остановлена)" : " " + keys));
        });
        return restored;
    }

    /**
     * Порча стенда — чем он расходится с тем, что подъём обещает следующему
     * классу и что перезапуском стороны не чинится: остановленный брокер,
     * отказ вставки в базе, остановленный приём.
     *
     * <p>Свежее развёртывание стороны ({@link #renew(Party)}) порчей не
     * является: сторона поднята на пустой базе с подъёмной конфигурацией, и
     * предусловия, которые она держала, тропа помнит снятыми. Конфигурацию и
     * остановленную сторону чинит {@link #restoreSides()}.
     *
     * @return причины порчи; пусто — стенд годен следующему классу
     */
    public List<String> spoilage() {
        List<String> reasons = new ArrayList<>();
        if (isFalse(broker.isRunning())) {
            reasons.add("брокер остановлен");
        }
        databases.forEach((party, database) -> {
            if (isFalse(database.query("select tgname from pg_trigger where tgname = ?", REFUSE_TRIGGER).isEmpty())) {
                reasons.add("у базы " + database.name() + " стоит отказ вставки");
            }
            if (isTrue(database.hasTable("reception_states")) && isFalse(database
                    .query("select topic from reception_states where reception_halted").isEmpty())) {
                reasons.add("приём стороны " + party.module() + " остановлен");
            }
        });
        return reasons;
    }

    /**
     * Свежая пара «тенант, счёт» — так, как её завёл бы владелец реестра:
     * ключи в хранилище, строка в реестре у его стаба; прежние строки реестра
     * остаются — синк сводит проекцию с реестром целиком. Активные определения
     * прежнего тенанта сняты до перехода.
     */
    private void freshPair() {
        retireActiveDefinitions();
        freshPairs++;
        String freshTenant = TENANT + "-" + freshPairs;
        String freshAccount = ACCOUNT + "-" + freshPairs;
        Substrate.putAccountKeys(freshAccount, CONTOUR);
        registry.put(freshAccount, freshTenant);
        auth.answers(PEER_ACCOUNTS, accountsBody());
        under(freshTenant, freshAccount, Substrate.apiKeyOf(freshAccount));
    }

    /** Тенант ходов тропы. */
    public String tenant() {
        return tenant;
    }

    /** Биржевой счёт ходов тропы. */
    public String account() {
        return account;
    }

    /**
     * Проекций у ядра нет: синк не подавался либо ядро поднято заново.
     *
     * <p>Синк необратим — снять проекции нечем, — поэтому кейс, которому
     * нужно их отсутствие после поставленного синка, получает свежее
     * развёртывание ядра. Состояние достижимо и в проде: новое ядро при
     * живом владельце определений.
     */
    public void withoutProjections() {
        if (isTrue(projectionsSynced)) {
            renew(Party.TRADING_CORE);
        }
    }

    /** Чисел риск-аппетита у ядра нет: тем же способом, что {@link #withoutProjections()}. */
    public void withoutRiskAppetite() {
        if (isTrue(riskAppetiteSet)) {
            renew(Party.TRADING_CORE);
        }
    }

    /**
     * Статистика поднята заново с названным расписанием пересчёта агрегатов.
     *
     * <p>Фасада у пересчёта нет намеренно, и такт подаётся расписанием,
     * сокращённым конфигурацией процесса; до подъёма с ним расписание не бьёт
     * (.claude/skills/test-code.md §«Уровень 3 — сквозной набор»).
     *
     * @param cron расписание пересчёта
     */
    public void statisticsRecomputes(String cron) {
        stop(Party.STATISTICS);
        side(Party.STATISTICS).set("jobs.aggregate-recompute.cron", cron);
        start(Party.STATISTICS);
    }

    /** Проекции счёта и инструмента у ядра сняты тиком синка. */
    public void projectionsSynced() {
        if (isTrue(projectionsSynced)) {
            return;
        }
        tick(Party.TRADING_CORE, "/registry-projections", "Manual RegistryProjectionJob trigger finished");
        projectionsSynced = Boolean.TRUE;
    }

    /** Числа риск-аппетита тенанта проставлены поверхностью ядра. */
    public void riskAppetiteSet() {
        if (isTrue(riskAppetiteSet)) {
            return;
        }
        Answer answer = call(Party.TRADING_CORE, "PUT", CORE + "/risk-appetites/" + tenant, null, """
                {
                  "globalSimultaneousRiskPerDealPercent": 5,
                  "globalCatastrophicRiskPerDealMultiplier": 100,
                  "globalConsecutiveLossLimit": 4
                }
                """);
        if (answer.status() != 200) {
            throw new IllegalStateException("Предусловие не поставлено: числа риск-аппетита — "
                    + answer.status() + " " + answer.body());
        }
        riskAppetiteSet = Boolean.TRUE;
    }

    /**
     * Рабочее плечо пары «счёт, инструмент» назначено поверхностью ядра: без
     * него risk-creating действие по паре отвергается преконтролем
     * (docs/rules/trading-constraints.md). Пара адресуется идентичностью
     * инструмента, поэтому проекции обязаны стоять раньше.
     */
    public void leverageAssigned() {
        if (isTrue(leverageAssigned)) {
            return;
        }
        projectionsSynced();
        Answer answer = call(Party.TRADING_CORE, "PUT", CORE + "/pair-settings/" + account + "/" + INSTRUMENT,
                null, """
                {"leverage": 10}
                """);
        if (answer.status() != 200) {
            throw new IllegalStateException("Предусловие не поставлено: плечо пары — "
                    + answer.status() + " " + answer.body());
        }
        leverageAssigned = Boolean.TRUE;
    }

    /** Ставка комиссии счёта снята тиком синка ставок — через коннектор у стаба площадки. */
    public void feeRatesSynced() {
        if (isTrue(feeRatesSynced)) {
            return;
        }
        tick(Party.TRADING_CORE, "/trade-fee-rates", "Manual TradeFeeRateSyncJob trigger finished");
        feeRatesSynced = Boolean.TRUE;
    }

    /** Общие предусловия тропы целиком (.claude/tests/cases/e2e-strategy-to-deal.md §«Предусловия тропы — что лежит до первого хода»). */
    public void commonPreconditions() {
        projectionsSynced();
        riskAppetiteSet();
        leverageAssigned();
        feeRatesSynced();
        detectionObserved();
    }

    /**
     * Проактивная детекция наблюдала счёт ходов: без наблюдённого прохода ядро
     * по счёту риска не набирает (docs/components/EntryScannerJob.md §«Гейт
     * входа»). Ставится тиком детекции ядра, чей срез площадка отдаёт пустым
     * целиком, — пара ещё без сделки, и проход по ней чист.
     *
     * <p><b>Срез отдаётся только на время тика</b> ({@link Stub#answersDuring}):
     * ответ, оставленный на ключе этого счёта, отвечал бы ему и тогда, когда
     * тропа уйдёт на свежую пару, — и проход детекции, читающий все счета
     * стенда, сверял бы нынешние строки прежнего счёта с давно заданным срезом.
     * Допуск возраста наблюдения стенд держит длинным ({@code settingsOf}): момент
     * ставится один раз на пару.
     *
     * <p><b>След тика досылается реле тем же предусловием.</b> Проход идёт по
     * всем счетам общего стенда, и у счетов прежних пар срезов нет — их проход
     * неполон, и ядро пишет отчёт о нём. Досланный здесь, этот след ложится в
     * тему ДО базовых замеров кейса; оставленный в outbox, он уехал бы первым
     * же реле кейса и читался бы его следом.
     */
    public void detectionObserved() {
        if (isTrue(detectionObserved)) {
            return;
        }
        projectionsSynced();
        exchange.answersDuring(List.of(EXCHANGE_POSITIONS, EXCHANGE_ORDERS_PENDING, EXCHANGE_ALGO_PENDING),
                EMPTY_SLICE,
                () -> tick(Party.TRADING_CORE, "/anomaly-detection", "Manual AnomalyJob trigger finished"));
        relayCore();
        detectionObserved = Boolean.TRUE;
    }

    /**
     * Забывает следы предусловий: журналы стабов, отметки журналов доступа и
     * концы обеих тем ({@link #published(String)}).
     *
     * <p>Чтения предусловий ушли в те же журналы, что и чтения кейса, и без
     * разделения отрицание «обращений нет» не сошлось бы никогда.
     */
    public void forgetTraces() {
        auth.forgetRequests();
        marketData.forgetRequests();
        exchange.forgetRequests();
        identity.forgetRequests();
        sides.forEach((party, side) -> accessMarks.put(party, side.accessMark()));
        for (String topic : List.of(Substrate.CORE_TOPIC, Substrate.STRATEGY_TOPIC)) {
            topicMarks.put(topic, ends(topic));
        }
    }

    /**
     * Обращения к поверхности стороны после последнего забывания, без проб
     * живости и без обращений, адресованных чужому счёту: путь коннектора
     * называет счёт, и сделки прежних пар стенда ходят к нему своим.
     */
    public List<Side.Access> accesses(Party party) {
        return side(party).accessSince(accessMarks.getOrDefault(party, 0)).stream()
                .filter(access -> isFalse(access.isProbe()))
                .filter(access -> isFalse(access.under(CONNECTOR_ACCOUNTS))
                        || access.under(CONNECTOR_ACCOUNTS + account + "/"))
                .toList();
    }

    /**
     * Число строк таблицы стороны, принадлежащих паре ходов, — по первой из
     * колонок, которой таблица несёт пару: тенант, счёт, сделка счёта либо
     * заявка счёта.
     *
     * <p>Ядро, журнал и статистика на стенде общие, и сделки прежних пар
     * пишут в те же таблицы; счёт строк таблицы целиком мерил бы их, а не
     * кейс. Таблица, не несущая пары ни одной колонкой, — отказ, а не счёт
     * целиком: такой счёт кейсу не принадлежит.
     *
     * @param party сторона
     * @param table таблица её базы
     * @return число строк пары
     */
    public Long rows(Party party, String table) {
        Database database = database(party);
        Set<String> columns = new TreeSet<>();
        database.query("select column_name from information_schema.columns where table_schema = 'public'"
                + " and table_name = ?", table).forEach(row -> columns.add(String.valueOf(row.get("column_name"))));
        String condition;
        String owner;
        if (columns.contains("tenant_id")) {
            condition = BY_TENANT;
            owner = tenant;
        } else if (columns.contains("tenant_internal_id")) {
            condition = "tenant_internal_id = ?";
            owner = tenant;
        } else if (columns.contains("exchange_account_id")) {
            condition = BY_ACCOUNT;
            owner = account;
        } else if (columns.contains("exchange_account_internal_id")) {
            condition = "exchange_account_internal_id = ?";
            owner = account;
        } else if (columns.contains("deal_id")) {
            condition = BY_DEAL;
            owner = account;
        } else if (columns.contains("order_id")) {
            condition = BY_ORDER;
            owner = account;
        } else {
            throw new IllegalStateException("Таблица " + database.name() + "." + table
                    + " пары не несёт ни одной колонкой: " + columns);
        }
        return ((Number) database.query("select count(*) as rows from " + table + " where " + condition, owner)
                .getFirst().get("rows")).longValue();
    }

    // ---------------------------------------------------------------- ходы и чтения

    /**
     * Подаёт тик ручным фасадом стороны и ждёт записи фасада о конце запуска.
     *
     * @param party    сторона с фасадом
     * @param suffix   путь тика после {@code …/jobs}
     * @param finished запись фасада о конце запуска
     */
    public Answer tick(Party party, String suffix, String finished) {
        Side side = side(party);
        Long mark = side.logMark();
        Answer answer = call(party, "POST", party.root() + "/jobs" + suffix, null, "");
        if (answer.status() != 202) {
            throw new IllegalStateException("Тик " + suffix + " стороны " + party.module()
                    + " не запущен: " + answer.status() + " " + answer.body());
        }
        Awaitility.await()
                .atMost(TICK_WAIT)
                .pollInterval(Duration.ofMillis(200))
                .until(() -> side.logSince(mark).contains(finished));
        return answer;
    }

    /**
     * Вызов поверхности стороны токеном теста.
     *
     * @param party  сторона
     * @param method метод
     * @param path   путь
     * @param tenant тенант заголовком контекста либо пусто
     * @param body   тело либо пусто
     */
    public Answer call(Party party, String method, String path, String tenant, String body) {
        return callWith(identity.testToken(), party, method, path, tenant, body);
    }

    /**
     * Вызов поверхности стороны названным токеном — у тропы, где токен и
     * есть вход (браузерный токен у периметра).
     *
     * @param token  предъявляемый токен; пустая строка — вызов без предъявления
     * @param party  сторона
     * @param method метод
     * @param path   путь
     * @param tenant тенант заголовком контекста либо пусто
     * @param body   тело либо пусто
     */
    public Answer callWith(String token, Party party, String method, String path, String tenant, String body) {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create(side(party).baseUrl() + path))
                .timeout(Duration.ofSeconds(60));
        if (isFalse(token.isEmpty())) {
            request.header("Authorization", "Bearer " + token);
        }
        if (nonNull(tenant)) {
            request.header(TENANT_HEADER, tenant);
        }
        HttpRequest.BodyPublisher publisher = isNull(body)
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body);
        if (nonNull(body)) {
            request.header("Content-Type", "application/json");
        }
        request.method(method, publisher);
        try {
            HttpResponse<String> answer = CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofString());
            return new Answer(answer.statusCode(), answer.body());
        } catch (IOException failure) {
            throw new IllegalStateException("Поверхность стороны " + party.module() + " не ответила: " + path, failure);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Ожидание ответа поверхности прервано", failure);
        }
    }

    /**
     * Все записи темы тропы с начала — своим потребителем прогона, без группы.
     *
     * <p><b>Без группы и без фиксации смещений:</b> наблюдатель не входит ни в
     * одну группу сторон и позиции их чтения не трогает.
     */
    public List<ConsumerRecord<String, String>> records(String topic) {
        Properties settings = new Properties();
        settings.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBootstrapServers());
        settings.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        settings.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        settings.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        settings.put(ConsumerConfig.ALLOW_AUTO_CREATE_TOPICS_CONFIG, "false");
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(settings)) {
            List<TopicPartition> partitions = consumer.partitionsFor(topic, Duration.ofSeconds(30)).stream()
                    .map(info -> new TopicPartition(topic, info.partition()))
                    .toList();
            consumer.assign(partitions);
            consumer.seekToBeginning(partitions);
            Map<TopicPartition, Long> ends = consumer.endOffsets(partitions, Duration.ofSeconds(30));
            List<ConsumerRecord<String, String>> records = new ArrayList<>();
            long deadline = System.currentTimeMillis() + 30_000;
            while (partitions.stream().anyMatch(partition -> consumer.position(partition) < ends.get(partition))) {
                if (System.currentTimeMillis() > deadline) {
                    throw new IllegalStateException("Тема " + topic + " не дочитана до конца за 30 секунд");
                }
                consumer.poll(Duration.ofMillis(500)).forEach(records::add);
            }
            return records;
        }
    }

    /**
     * Заводит определение у владельца его поверхностью — эталоном на паре тропы.
     *
     * @return идентичность определения
     */
    public String createDefinition() {
        return createDefinition(referenceDefinition());
    }

    /**
     * Заводит определение у владельца его поверхностью — названным телом на
     * паре тропы.
     *
     * @param definition тело определения
     * @return идентичность определения
     */
    public String createDefinition(String definition) {
        Answer answer = call(Party.STRATEGIES, "POST", STRATEGIES, tenant, onPair(definition));
        if (answer.status() != 201) {
            throw new IllegalStateException("Предусловие не поставлено: создание определения — "
                    + answer.status() + " " + answer.body());
        }
        return String.valueOf(Json.object(answer.body()).get("internalId"));
    }

    /**
     * Тело определения на паре ходов: счёт эталона заменён счётом пары.
     *
     * @param definition тело определения
     * @return то же тело на счёте ходов
     */
    public String onPair(String definition) {
        return definition.replace("\"" + ACCOUNT + "\"", "\"" + account + "\"");
    }

    /**
     * Переводит определение у владельца в статус его поверхностью.
     *
     * @param internalId идентичность определения
     * @param status     целевой статус
     */
    public Answer moveDefinition(String internalId, String status) {
        return call(Party.STRATEGIES, "PUT", STRATEGIES + "/" + internalId + "/status", tenant,
                "{\"status\": \"" + status + "\"}");
    }

    /**
     * Снимает активные определения тенанта у владельца — деактивацией и тиком реле.
     *
     * <p>У пары «счёт × инструмент» активным бывает одно определение, и
     * кейсу, активирующему своё, чужое активное на той же паре отказало бы
     * переходом. Снимается оно ходом владельца, а не записью в базу.
     */
    public void retireActiveDefinitions() {
        Answer listed = call(Party.STRATEGIES, "GET", STRATEGIES, tenant, null);
        List<String> active = new ArrayList<>();
        Json.tree(listed.body()).forEach(definition -> {
            if (Objects.equals("ACTIVE", definition.path("status").asString())) {
                active.add(definition.path("internalId").asString());
            }
        });
        if (active.isEmpty()) {
            return;
        }
        active.forEach(internalId -> moveDefinition(internalId, "INACTIVE"));
        relayOwner();
    }

    /** Тик реле владельца определений его фасадом. */
    public void relayOwner() {
        tick(Party.STRATEGIES, "/outbox-relay", "Manual OutboxRelayJob trigger finished");
    }

    /** Тик реле ядра его фасадом. */
    public void relayCore() {
        tick(Party.TRADING_CORE, "/outbox-relay", "Manual OutboxRelayJob trigger finished");
    }

    /** Тик сканера входа ядра его фасадом. */
    public void scanEntries() {
        tick(Party.TRADING_CORE, "/entry-scanner", "Manual EntryScannerJob trigger finished");
    }

    /** Тик прохода оркестратора ядра его фасадом. */
    public void orchestrate() {
        tick(Party.TRADING_CORE, "/deal-orchestrator", "Manual DealOrchestratorJob trigger finished");
    }

    /**
     * Активное определение с копией у ядра — ходами тропы: создание,
     * активация, тик реле владельца, ожидание копии.
     *
     * <p>Прочие активные определения тенанта сняты тем же ходом: на паре
     * активным бывает одно.
     *
     * @return идентичность определения
     */
    public String activeDefinition() {
        return activeDefinition(referenceDefinition());
    }

    /**
     * Активное определение названного тела с копией у ядра — теми же ходами,
     * что {@link #activeDefinition()}.
     *
     * @param body тело определения
     * @return идентичность определения
     */
    public String activeDefinition(String body) {
        retireActiveDefinitions();
        String definition = createDefinition(body);
        Answer moved = moveDefinition(definition, "ACTIVE");
        if (moved.status() != 200) {
            throw new IllegalStateException("Предусловие не поставлено: активация — " + moved.status() + " "
                    + moved.body());
        }
        relayOwner();
        await("копия определения " + definition + " у ядра", () -> database(Party.TRADING_CORE)
                .query("select id from strategies where internal_id = ? and status = 'ACTIVE'", definition)
                .size() == 1);
        return definition;
    }

    /**
     * Стаб владельца рыночных данных отдаёт раскладку фич, на которой
     * входное условие бычьей детали эталона истинно.
     */
    public void marketFavoursEntry() {
        marketPhaseIs("BULL_TREND");
    }

    /**
     * Стаб владельца рыночных данных отдаёт ту же раскладку фич с названной
     * фазой рынка: у эталона фаза, сменившаяся после входа, истинит шаг
     * выхода уровня сделки.
     *
     * @param phase фаза рынка раскладки
     */
    public void marketPhaseIs(String phase) {
        marketData.answersPost(PEER_FEATURES, featuresBody(phase));
    }

    /**
     * Стаб владельца рыночных данных отдаёт бычью раскладку без названного
     * индикатора: так владелец отдаёт значение устаревшее либо не собранное —
     * пустым местом (docs/rules/market-data-freshness.md).
     *
     * @param indicatorKey авторское имя операнда
     */
    public void marketLosesIndicator(String indicatorKey) {
        JsonNode features = Json.tree(featuresBody("BULL_TREND"));
        ((ObjectNode) features.path("latestIndicators")).remove(indicatorKey);
        marketData.answersPost(PEER_FEATURES, features.toString());
    }

    /**
     * Каталог владельца рыночных данных несёт сверх инструмента тропы второй
     * инструмент контура, и правила второго отдаются по его идентичности; до
     * ядра каталог доезжает следующим синком проекций. Каталог одного
     * инструмента возвращает передача стенда следующему классу.
     *
     * @param instrumentInternalId идентичность второго инструмента
     * @param externalInstrumentId биржевое имя второго инструмента
     */
    public void marketListsSecondInstrument(String instrumentInternalId, String externalInstrumentId) {
        String first = instrumentsBody(INSTRUMENT, EXTERNAL_INSTRUMENT).strip();
        String second = instrumentsBody(instrumentInternalId, externalInstrumentId).strip();
        marketData.answers(PEER_INSTRUMENTS,
                first.substring(0, first.length() - 1) + "," + second.substring(1));
        marketData.answers(PEER_INSTRUMENTS + "/" + instrumentInternalId + "/rules", rulesBody(externalInstrumentId));
    }

    /**
     * Стаб площадки принимает команды тропы: отдаёт свежий снимок средств,
     * подтверждает плечо и постановку, а заявку, которой ещё не наливал,
     * не знает.
     *
     * <p>Снимок средств — два ответа: баланс и конфигурация счёта. Режимы
     * конфигурации — те, что держит контур (фьючерсный режим счёта,
     * нетто-позиции): иной либо пустой режим преконтроль читает выходом из
     * контура и вход отвергает (docs/spec/risk-limits.json, величина
     * {@code accountModeOutOfContour}).
     *
     * <p>Это общее предусловие тропы; кейс, перекрывший ответ своим (отказ
     * постановки), возвращает его этим же ходом.
     */
    public void exchangeAcceptsCommands() {
        exchange.answersTemplated(EXCHANGE_BALANCE, """
                {"code": "0", "msg": "", "data": [{"uTime": "{{now format='epoch'}}", "totalEq": "10000",
                  "adjEq": "10000", "availEq": "10000", "details": [{"ccy": "USDT",
                  "uTime": "{{now format='epoch'}}", "eq": "10000",
                  "cashBal": "10000", "availBal": "10000", "frozenBal": "0"}]}]}
                """);
        exchange.answers(EXCHANGE_ACCOUNT_CONFIG, """
                {"code": "0", "msg": "", "data": [{"acctLv": "2", "posMode": "net_mode"}]}
                """);
        exchange.answersPost(EXCHANGE_LEVERAGE, """
                {"code": "0", "msg": "", "data": [{"lever": "10", "mgnMode": "isolated", "instId": "%s",
                  "posSide": "net"}]}
                """.formatted(EXTERNAL_INSTRUMENT));
        exchange.answersPostTemplated(EXCHANGE_ORDER, """
                {"code": "0", "msg": "", "data": [{"ordId": "%s", "clOrdId": "{{jsonPath request.body '$.clOrdId'}}",
                  "sCode": "0", "sMsg": "", "ts": "%d"}]}
                """.formatted(EXTERNAL_ORDER, acknowledgedAt));
        exchange.answers(EXCHANGE_ORDER, """
                {"code": "0", "msg": "", "data": []}
                """);
        // Поиск одним клиентским идентификатором — отправка ищет ногу перед
        // постановкой — находит лишь ту, что площадке названа по нему; прочие
        // не существуют, какую бы ногу ни отдавал ответ пути. Ненайденность
        // площадка сообщает кодом отказа (docs/integrations/okx/contracts/order.md).
        exchange.answersWithout(EXCHANGE_ORDER, "ordId", """
                {"code": "51603", "msg": "Order does not exist", "data": []}
                """);
        exchange.answers(EXCHANGE_POSITIONS, """
                {"code": "0", "msg": "", "data": []}
                """);
    }

    /**
     * Площадка подтверждает постановку заявки названным биржевым моментом — с
     * этого хода и до конца тропы.
     *
     * <p>Момент подтверждения первой входной ноги есть нижняя граница окна
     * движений сделки (docs/models/domain/aggregate/Deal.md,
     * {@code billsWindowBegin}); умолчание тропы — год назад, то есть граница
     * старше глубины свежего эндпоинта движений.
     *
     * @param moment биржевой момент подтверждения, мс
     */
    public void exchangeAcknowledgesAt(Long moment) {
        acknowledgedAt = moment;
        exchangeAcceptsCommands();
    }

    /**
     * Ряд фактов зерна начат прошлыми сутками: первый факт положен в тему ядра
     * так, как положил бы производитель, — моментом происшествия в конверте.
     *
     * <p><b>Без него сутки тропы не пересчитываются вовсе:</b> проход пишет
     * сутки, только если они начались не раньше первого факта ряда
     * (docs/spec/statistics-aggregates.json, {@code dayRecomputable}), а на
     * свежей тропе ряд начинается посреди сегодняшних суток. Часы процессов
     * при этом не двигаются — возраст стоит в данных.
     *
     * <p>Этот ход начинает ряд <b>зерна происшествий</b> — фактом заведения
     * сделки; ряд сделочного зерна свой и начинается фактом класса терминала
     * ({@link #factSeriesStartedYesterday(String, String)}).
     */
    public void factSeriesStartedYesterday() {
        factSeriesStartedYesterday("DEAL_OPENED", () -> """
                {"dealInternalId": "%s", "exchangeAccountInternalId": "%s", "instrumentInternalId": "%s",
                 "strategyInternalId": "%s", "entryReason": "STRATEGY", "direction": "LONG",
                 "entryMarketPhase": "BULL_TREND"}
                """.formatted(UUID.randomUUID(), account, INSTRUMENT, UUID.randomUUID()));
    }

    /**
     * Ряд фактов зерна начат прошлыми сутками фактом названного класса — тем
     * же ходом, что {@link #factSeriesStartedYesterday()}: у каждого зерна
     * статистики ряд свой, и начинает его факт того класса, который зерно
     * несёт.
     *
     * <p><b>Ряд у каждой пары свой</b>, и тропа его помнит: смена пары
     * ({@link #under(String, String, String)}) начинает тот же ряд новой паре
     * тем же ходом — содержимое собирается заново под её счёт.
     *
     * @param eventType класс события
     * @param payload   содержимое события — под счёт ходов на момент хода
     */
    public void factSeriesStartedYesterday(String eventType, Supplier<String> payload) {
        startedSeries.put(eventType, payload);
        seriesStartedYesterday(eventType, payload.get());
    }

    private void seriesStartedYesterday(String eventType, String payload) {
        OffsetDateTime yesterday = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("eventId", UUID.randomUUID().toString());
        headers.put("eventType", eventType);
        headers.put("occurredAt", yesterday.toString());
        headers.put("version", "1");
        produce(Substrate.CORE_TOPIC, tenant, payload, headers);
    }

    /**
     * Площадка отвергает постановку заявки кодом своего словаря — эхом
     * клиентского идентификатора и без биржевого.
     */
    public void exchangeRejectsPlacement() {
        exchange.answersPostTemplated(EXCHANGE_ORDER, """
                {"code": "1", "msg": "All operations failed", "data": [{"ordId": "",
                  "clOrdId": "{{jsonPath request.body '$.clOrdId'}}", "sCode": "51008",
                  "sMsg": "Order failed. Insufficient USDT balance in account.", "ts": "1758240000000"}]}
                """);
    }

    /** Площадка отвергает команду плеча кодом своего словаря. */
    public void exchangeRejectsLeverage() {
        exchange.answersPost(EXCHANGE_LEVERAGE, """
                {"code": "59000", "msg": "Setting failed. Cancel any open orders, close positions, and stop trading bots first.",
                  "data": []}
                """);
    }

    /**
     * Сделка по копии заведена тиком сканера на раскладке, на которой вход
     * истинен, — ходом тропы, а не записью.
     *
     * @return идентичность сделки
     */
    public String openDeal() {
        marketFavoursEntry();
        scanEntries();
        List<JsonNode> opened = deals().stream()
                .filter(deal -> isFalse(TERMINAL_DEAL.contains(deal.path("status").asString())))
                .toList();
        if (opened.size() != 1) {
            throw new IllegalStateException("Предусловие не поставлено: сделка на паре — " + opened);
        }
        return opened.getFirst().path("internalId").asString();
    }

    /**
     * Подаёт проходы оркестратора, пока условие о следе не станет истинным.
     *
     * <p><b>Проход дробит работу по звену за раз</b> — снимок средств,
     * заведение заявки, её отправка, — и число проходов до состояния есть
     * свойство тропы, а не кейса. Потолок — время, а не счёт: повтор
     * упавшей команды ждёт своей паузы, и проходы до неё пусты.
     *
     * @param label   что ждётся — в сообщение отказа
     * @param outcome условие
     */
    public void passUntil(String label, Callable<Boolean> outcome) {
        long deadline = System.currentTimeMillis() + TICK_WAIT.toMillis();
        while (isFalse(evaluate(outcome))) {
            if (System.currentTimeMillis() > deadline) {
                throw new IllegalStateException("Состояние не достигнуто проходами за " + TICK_WAIT + ": " + label);
            }
            orchestrate();
        }
    }

    /**
     * Входная заявка сделки отправлена: площадка подтвердила постановку, у
     * зеркала ядра — её внешний идентификатор.
     */
    public void entrySubmitted() {
        passUntil("входная заявка отправлена", () -> isFalse(database(Party.TRADING_CORE)
                .query("select id from orders where " + BY_DEAL + " and external_id is not null", account)
                .isEmpty()));
    }

    /**
     * Площадка отдаёт отправленную входную заявку налитой целиком, а её
     * встроенную защиту — материализованной условной заявкой.
     *
     * <p><b>Идентичности берутся у зеркала ядра</b>, потому что их выпускает
     * ядро: клиентский идентификатор заявки и защиты площадка возвращает
     * эхом, а не придумывает.
     */
    public void exchangeFillsEntry() {
        Database core = database(Party.TRADING_CORE);
        Map<String, Object> order = core.query("select internal_id, size from orders where " + BY_DEAL
                + " and external_id is not null", account).getFirst();
        Map<String, Object> protection = core.query("select internal_id, size, stop_loss_trigger_price"
                + " from attached_algo_orders where " + BY_ORDER, account).getFirst();
        exchange.answers(EXCHANGE_ORDER, """
                {"code": "0", "msg": "", "data": [{"instId": "%s", "ordId": "%s", "clOrdId": "%s",
                  "ordType": "market", "side": "buy", "posSide": "net", "state": "filled", "px": "",
                  "sz": "%s", "accFillSz": "%s", "avgPx": "%s", "fee": "-0.1", "feeCcy": "USDT",
                  "cTime": "1758240000000", "uTime": "1758240001000"}]}
                """.formatted(EXTERNAL_INSTRUMENT, EXTERNAL_ORDER, order.get("internal_id"),
                plain(order.get("size")), plain(order.get("size")), ENTRY_PRICE));
        exchange.answers(EXCHANGE_ALGO_PENDING, """
                {"code": "0", "msg": "", "data": [{"instId": "%s", "algoId": "%s", "algoClOrdId": "%s",
                  "ordType": "conditional", "side": "sell", "posSide": "net", "state": "live", "sz": "%s",
                  "slTriggerPx": "%s", "slTriggerPxType": "mark", "slOrdPx": "-1",
                  "cTime": "1758240000000", "uTime": "1758240001000"}]}
                """.formatted(EXTERNAL_INSTRUMENT, EXTERNAL_PROTECTION, protection.get("internal_id"),
                plain(protection.get("size")), plain(protection.get("stop_loss_trigger_price"))));
    }

    /**
     * Сделки ядра на паре тропы — его поверхностью.
     *
     * @return перечень сделок счёта тропы
     */
    public List<JsonNode> deals() {
        Answer answer = call(Party.TRADING_CORE, "GET", CORE + "/deals?exchangeAccountInternalId=" + account,
                tenant, null);
        if (answer.status() != 200) {
            throw new IllegalStateException("Чтение сделок ядра — " + answer.status() + " " + answer.body());
        }
        List<JsonNode> deals = new ArrayList<>();
        Json.tree(answer.body()).forEach(deals::add);
        return deals;
    }

    /**
     * Зафиксированное смещение группы потребителя на единственной партиции темы.
     *
     * <p>Читается администратором брокера, а не участником группы: второй
     * участник отнял бы партицию у стороны (ловушка TC-026 скилла кода тестов).
     *
     * @return смещение либо -1, если группа по теме ничего не фиксировала
     */
    public Long committedOffset(String group, String topic) {
        Map<String, Object> settings = new HashMap<>();
        settings.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBootstrapServers());
        try (Admin admin = Admin.create(settings)) {
            OffsetAndMetadata offset = admin.listConsumerGroupOffsets(group).partitionsToOffsetAndMetadata()
                    .get(30, TimeUnit.SECONDS)
                    .get(new TopicPartition(topic, 0));
            return isNull(offset) ? -1L : offset.offset();
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Чтение смещений группы прервано", failure);
        } catch (ExecutionException | TimeoutException failure) {
            throw new IllegalStateException("Смещения группы " + group + " не прочитаны", failure);
        }
    }

    /**
     * Живые группы потребителей с названным префиксом и темы, назначенные их
     * участникам, — администратором брокера, а не участником группы.
     *
     * <p>Группа без участников в ответ не входит: у эфемерной группы
     * остановленной реплики назначений нет, и потребителем она не является.
     *
     * @param prefix префикс имени группы
     * @return группа → темы её назначений
     */
    public Map<String, Set<String>> consumingGroups(String prefix) {
        Map<String, Object> settings = new HashMap<>();
        settings.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBootstrapServers());
        try (Admin admin = Admin.create(settings)) {
            List<String> named = admin.listGroups(ListGroupsOptions.forConsumerGroups()).all()
                    .get(30, TimeUnit.SECONDS).stream()
                    .map(GroupListing::groupId)
                    .filter(group -> group.startsWith(prefix))
                    .toList();
            Map<String, Set<String>> consuming = new TreeMap<>();
            if (named.isEmpty()) {
                return consuming;
            }
            admin.describeConsumerGroups(named).all().get(30, TimeUnit.SECONDS).forEach((group, description) -> {
                Set<String> topics = new TreeSet<>();
                description.members().forEach(member -> member.assignment().topicPartitions()
                        .forEach(partition -> topics.add(partition.topic())));
                if (isFalse(description.members().isEmpty())) {
                    consuming.put(group, topics);
                }
            });
            return consuming;
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Чтение групп прервано", failure);
        } catch (ExecutionException | TimeoutException failure) {
            throw new IllegalStateException("Группы с префиксом " + prefix + " не прочитаны", failure);
        }
    }

    /**
     * Темы брокера тропы — администратором брокера, без служебных тем самого
     * брокера.
     *
     * @return имена тем
     */
    public Set<String> topics() {
        Map<String, Object> settings = new HashMap<>();
        settings.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBootstrapServers());
        try (Admin admin = Admin.create(settings)) {
            return new TreeSet<>(admin.listTopics().names().get(30, TimeUnit.SECONDS));
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Чтение тем прервано", failure);
        } catch (ExecutionException | TimeoutException failure) {
            throw new IllegalStateException("Темы брокера не прочитаны", failure);
        }
    }

    /**
     * Останавливает брокер тропы. Поднять его обратно нечем: новый контейнер
     * опубликовал бы новый порт, которого стороны не знают, — поэтому ход
     * законен только последним ходом тропы.
     */
    public void stopBroker() {
        broker.stop();
    }

    /** Конец темы на её единственной партиции — смещение следующей записи. */
    public Long endOffset(String topic) {
        return (long) records(topic).size();
    }

    /**
     * Записи темы, легшие после последнего забывания следов, — отрицание «за
     * ход в тему не легло ничего» на общем стенде, где записи прежних классов
     * в теме уже лежат.
     *
     * @param topic тема
     * @return записи со смещением не меньше конца, снятого забыванием
     */
    public List<ConsumerRecord<String, String>> published(String topic) {
        Map<Integer, Long> ends = topicMarks.getOrDefault(topic, Map.of());
        return records(topic).stream()
                .filter(record -> record.offset() >= ends.getOrDefault(record.partition(), 0L))
                .toList();
    }

    /** Концы партиций темы — администратором брокера, без чтения записей. */
    private Map<Integer, Long> ends(String topic) {
        Map<String, Object> settings = new HashMap<>();
        settings.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBootstrapServers());
        try (Admin admin = Admin.create(settings)) {
            Map<TopicPartition, OffsetSpec> latest = new HashMap<>();
            admin.describeTopics(List.of(topic)).allTopicNames().get(30, TimeUnit.SECONDS).get(topic).partitions()
                    .forEach(partition -> latest.put(new TopicPartition(topic, partition.partition()),
                            OffsetSpec.latest()));
            Map<Integer, Long> ends = new HashMap<>();
            admin.listOffsets(latest).all().get(30, TimeUnit.SECONDS)
                    .forEach((partition, offset) -> ends.put(partition.partition(), offset.offset()));
            return ends;
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Чтение концов темы прервано", failure);
        } catch (ExecutionException | TimeoutException failure) {
            throw new IllegalStateException("Концы темы " + topic + " не прочитаны", failure);
        }
    }

    /**
     * Кладёт запись в тему тропы — так, как её положил бы производитель.
     *
     * @param topic   тема
     * @param key     ключ записи
     * @param value   содержимое
     * @param headers заголовки конверта
     */
    public void produce(String topic, String key, String value, Map<String, String> headers) {
        Properties settings = new Properties();
        settings.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBootstrapServers());
        settings.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        settings.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(settings)) {
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, value);
            headers.forEach((name, text) -> record.headers().add(name, text.getBytes(StandardCharsets.UTF_8)));
            producer.send(record).get(30, TimeUnit.SECONDS);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Запись в тему прервана", failure);
        } catch (ExecutionException | TimeoutException failure) {
            throw new IllegalStateException("Запись в тему " + topic + " не подтверждена", failure);
        }
    }

    /**
     * Ждёт, пока условие о следе стороны станет истинным.
     *
     * @param label   что ждётся — в сообщение истечения
     * @param outcome условие
     */
    public static void await(String label, Callable<Boolean> outcome) {
        Awaitility.await(label)
                .atMost(TICK_WAIT)
                .pollInterval(Duration.ofMillis(300))
                .until(outcome);
    }

    /** Эталон определения у владельца определений — на паре счёта и инструмента тропы. */
    public static String referenceDefinition() {
        try {
            return Files.readString(Workspace.repositoryRoot().resolve(Path.of(REFERENCE_DEFINITION)));
        } catch (IOException failure) {
            throw new IllegalStateException("Эталон определения не прочитался: " + REFERENCE_DEFINITION, failure);
        }
    }

    @Override
    public void close() {
        sides.values().forEach(Side::stop);
        auth.stop();
        marketData.stop();
        exchange.stop();
        identity.stop();
        broker.stop();
    }

    // ---------------------------------------------------------------- проводка

    private void neighboursAnswer() {
        auth.answers(PEER_ACCOUNTS, accountsBody());
        marketData.answers(PEER_INSTRUMENTS, instrumentsBody(INSTRUMENT, EXTERNAL_INSTRUMENT));
        marketData.answers(PEER_INSTRUMENTS + "/" + INSTRUMENT + "/rules", rulesBody(EXTERNAL_INSTRUMENT));
        exchange.answers(EXCHANGE_TIME, """
                {"code": "0", "msg": "", "data": [{"ts": "%d"}]}
                """.formatted(System.currentTimeMillis()));
        exchangeServesAccount();
    }

    /**
     * Площадка отвечает счёту ходов с первого хода: ставкой комиссии и приёмом команд.
     *
     * <p>Момент ставки — момент ответа, а не дата в прошлом: ядро мерит по нему свежесть
     * ставки, и застывшая дата подняла бы мягкую ступень на каждом инструменте контура с
     * первого же тика синка (docs/rules/instrument-hold.md §«Несвежесть ставки комиссии»).
     */
    private void exchangeServesAccount() {
        exchange.answers(EXCHANGE_FEE, """
                {"code": "0", "msg": "", "data": [{"instType": "SWAP", "level": "Lv1", "ts": "%d",
                  "taker": "-0.0005", "maker": "-0.0002",
                  "feeGroup": [{"groupId": "1", "taker": "-0.0005", "maker": "-0.0002"}]}]}
                """.formatted(System.currentTimeMillis()));
        exchangeAcceptsCommands();
    }

    private static Boolean evaluate(Callable<Boolean> outcome) {
        try {
            return outcome.call();
        } catch (Exception failure) {
            throw new IllegalStateException("Условие о следе не вычислилось", failure);
        }
    }

    private static String plain(Object number) {
        return ((BigDecimal) number).stripTrailingZeros().toPlainString();
    }

    /** Реестр счетов у стаба владельца — все пары, заведённые тропой. */
    private String accountsBody() {
        List<String> rows = new ArrayList<>();
        registry.forEach((accountInternalId, tenantInternalId) -> rows.add("""
                {
                  "internalId": "%s",
                  "tenantInternalId": "%s",
                  "exchangeCode": "OKX",
                  "label": "e2e account",
                  "contour": "%s",
                  "status": "ACTIVE"
                }""".formatted(accountInternalId, tenantInternalId, CONTOUR)));
        return "[" + String.join(",", rows) + "]";
    }

    /** Каталог, который стаб владельца рыночных данных отдаёт на чтение инструментов. */
    public static String stubbedInstrumentsBody() {
        return instrumentsBody(INSTRUMENT, EXTERNAL_INSTRUMENT);
    }

    private static String instrumentsBody(String instrumentInternalId, String externalInstrumentId) {
        return """
                [{
                  "internalId": "%s",
                  "exchangeCode": "OKX",
                  "externalId": "%s",
                  "externalType": "SWAP",
                  "status": "ACTIVE",
                  "externalSettlementCurrency": "USDT",
                  "externalBaseCurrency": "%s",
                  "externalQuoteCurrency": "USDT"
                }]
                """.formatted(instrumentInternalId, externalInstrumentId, externalInstrumentId.split("-")[0]);
    }

    /**
     * Правила инструмента, которые стаб владельца рыночных данных отдаёт по
     * его идентичности.
     *
     * <p><b>Тир один и покрывает всякий размер позиции тропы:</b> без тиров
     * оценка ликвидации после входа не измерена, и преконтроль вход отвергает
     * (docs/spec/risk-limits.json, величина {@code postActMaintenanceMarginRate}).
     */
    private static String rulesBody(String externalInstrumentId) {
        return """
                {
                  "externalInstrumentId": "%s",
                  "externalInstrumentType": "SWAP",
                  "externalTickSize": "0.01",
                  "externalLotSize": "1",
                  "externalMinSize": "1",
                  "externalMaxLimitSize": "1000000",
                  "externalMaxMarketSize": "1000000",
                  "externalContractValue": "0.1",
                  "externalContractValueCurrency": "%s",
                  "externalMaxLeverage": "50",
                  "externalFeeGroupId": "1",
                  "instrumentType": "SWAP",
                  "status": "LIVE",
                  "externalState": "live",
                  "positionTiers": [{"minSize": 0, "maxSize": 1000000, "maintenanceMarginRate": 0.004}]
                }
                """.formatted(externalInstrumentId, externalInstrumentId.split("-")[0]);
    }

    /**
     * Раскладка фич момента с названной фазой рынка. На бычьей фазе входной
     * шаг бычьей детали эталона истинен: быстрая средняя выше медленной,
     * осциллятор не ниже порога; плюс волатильность под стоп и цены момента
     * под расчёт заявки.
     *
     * <p><b>Раскладка ключуется авторскими именами операндов эталона</b> —
     * так её отдаёт владелец данных (форму соседа мерит его ящик), и имя вне
     * эталона гасило бы условие молча, как отсутствующий операнд.
     */
    private static String featuresBody(String phase) {
        String candle = OffsetDateTime.now(ZoneOffset.UTC).withSecond(0).withNano(0).toString();
        return """
                {
                  "latestIndicators": {
                    "ema_fast_15m": {"indicatorType": "EMA", "candleTimestamp": "%1$s", "ema": "2010"},
                    "ema_slow_15m": {"indicatorType": "EMA", "candleTimestamp": "%1$s", "ema": "2000"},
                    "rsi_5m": {"indicatorType": "RSI", "candleTimestamp": "%1$s", "rsi": "60"},
                    "atr_15m": {"indicatorType": "ATR", "candleTimestamp": "%1$s", "atr": "20"}
                  },
                  "previousIndicators": {},
                  "structures": {},
                  "marketPhase": {"type": "%3$s"},
                  "marketPriceData": {
                    "externalLastPrice": "%2$s",
                    "externalBidPrice": "1999.9",
                    "externalAskPrice": "2000.1",
                    "externalBidSize": "500",
                    "externalAskSize": "500",
                    "externalTimestamp": "%1$s"
                  }
                }
                """.formatted(candle, ENTRY_PRICE, phase);
    }

    private Map<String, String> settingsOf(Party party) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("spring.security.oauth2.resourceserver.jwt.issuer-uri", identity.issuer());
        values.put("platform.environment.name", Substrate.ENVIRONMENT);
        switch (party) {
            case STRATEGIES -> {
                values.putAll(datasource(databases.get(party)));
                values.putAll(outgoingIdentity(party));
                values.put("neighbours.trading-core.base-url", addressOf(Party.TRADING_CORE));
                values.put("broker.bootstrap-servers", broker.getBootstrapServers());
                values.put("jobs.outbox-relay.enabled", "true");
                values.put("jobs.outbox-relay.cron", NEVER);
            }
            case TRADING_CORE -> {
                values.putAll(datasource(databases.get(party)));
                values.putAll(outgoingIdentity(party));
                values.put("neighbours.connector.exchange-code", "OKX");
                values.put("neighbours.connector.base-url", addressOf(Party.CONNECTOR));
                values.put("neighbours.auth.base-url", Objects.equals(layout, Layout.PERIMETER)
                        ? addressOf(Party.AUTH) : auth.baseUrl());
                values.put("neighbours.market-data.base-url", marketData.baseUrl());
                values.put("broker.bootstrap-servers", broker.getBootstrapServers());
                for (String job : List.of("deal-orchestrator", "entry-scanner", "anomaly-job", "outbox-relay",
                        "projection-sync", "strategy-demand", "trade-fee-rate-sync")) {
                    values.put(job + ".cron", NEVER);
                }
                // Детекция тикается только ходом тропы, и наблюдённый проход
                // ставится один раз на пару (detectionObserved): допуск
                // возраста наблюдения длиннее жизни пары, иначе вход закрывал
                // бы не стык, а время прогона класса.
                values.put("entry-scanner.observation-max-age", OBSERVATION_MAX_AGE);
            }
            case CONNECTOR -> {
                values.putAll(vault());
                values.put("okx.base-url", exchange.baseUrl());
            }
            case AUDIT -> {
                Database database = databases.get(party);
                values.put("audit.persistence.url", database.url());
                values.put("audit.persistence.username", database.username());
                values.put("audit.persistence.password", database.password());
                values.put("audit.persistence.max-pool-size", "3");
                values.put("reception.bootstrap-servers", broker.getBootstrapServers());
                values.put("reception.group-id", "audit.journal");
                values.put("reception.topics", Substrate.CORE_TOPIC + "," + Substrate.STRATEGY_TOPIC);
                values.put("reception.state-tick-enabled", "true");
                values.put("reception.state-tick-interval", "2s");
                values.put("jobs.journal-cleanup.enabled", "true");
                values.put("jobs.journal-cleanup.cron", NEVER);
                values.put("platform.environment.journal-retention-profile", "REDUCED");
            }
            case AUTH -> {
                values.putAll(datasource(databases.get(party)));
                values.putAll(vault());
                values.put("platform.identity.browser-client-id", IdentityStub.BROWSER_CLIENT_ID);
                values.put("platform.environment.admitted-contours", CONTOUR);
            }
            case BFF -> {
                values.put("broker.bootstrap-servers", broker.getBootstrapServers());
                values.put("perimeter.owner-url-template", "http://{owner}:" + OWNER_PORT);
                values.put("perimeter.read-retries", "1");
                values.put("perimeter.membership.cache-ttl", "60s");
                values.put("perimeter.stream.topics", Substrate.CORE_TOPIC + "," + Substrate.STRATEGY_TOPIC);
                values.put("perimeter.stream.replay-window", "200");
                values.put("perimeter.stream.pulse-interval", "365d");
                values.put("perimeter.stream.pulse-enabled", "true");
                values.put("perimeter.stream.connection-timeout", "30m");
                values.put("perimeter.stream.max-subscriptions-per-tenant", "32");
                values.put("perimeter.ticket.secret", "e2e-ticket-secret-7d21");
                values.put("perimeter.ticket.ttl", "10m");
            }
            case STATISTICS -> {
                Database database = databases.get(party);
                values.put("statistics.persistence.url", database.url());
                values.put("statistics.persistence.username", database.username());
                values.put("statistics.persistence.password", database.password());
                values.put("statistics.persistence.max-pool-size", "3");
                values.put("reception.bootstrap-servers", broker.getBootstrapServers());
                values.put("reception.group-id", "statistics.facts");
                values.put("reception.topics", Substrate.CORE_TOPIC);
                values.put("reception.state-tick-enabled", "true");
                values.put("reception.state-tick-interval", "2s");
                values.put("jobs.aggregate-recompute.enabled", "true");
                values.put("jobs.aggregate-recompute.cron", NEVER);
            }
        }
        return values;
    }

    /**
     * Адрес стороны для её соседей.
     *
     * <p>У тропы сделки он известен после заведения стороны (порт выбирается
     * свободным), поэтому сторона, зовущая соседа, заводится после него; у
     * тропы периметра он выводится из стороны — свой loopback-адрес на общем
     * порту владельцев.
     */
    private String addressOf(Party party) {
        if (Objects.equals(layout, Layout.PERIMETER)) {
            return "http://" + loopbackOf(party) + ":" + OWNER_PORT;
        }
        if (isFalse(sides.containsKey(party))) {
            throw new IllegalStateException("Адрес стороны " + party.module() + " нужен раньше, чем она заведена");
        }
        return side(party).baseUrl();
    }

    private static String loopbackOf(Party party) {
        return "127.0.0." + (LOOPBACK_BASE + party.ordinal());
    }

    /**
     * Таблица имён процесса периметра — то, что в кластере даёт DNS: имя
     * владельца разрешается в адрес его стороны либо стаба.
     *
     * <p><b>Это воспроизведение среды, а не подмена предмета:</b> периметр
     * собирает адрес владельца шаблоном из имени, и в кластере имя и есть
     * хост сервиса на общем порту. Прочие имена процесса (адреса стабов и
     * брокера по {@code localhost}) едут той же таблицей.
     */
    private String ownerNames() {
        StringBuilder names = new StringBuilder("127.0.0.1 localhost\n");
        for (Party party : List.of(Party.TRADING_CORE, Party.STRATEGIES, Party.AUDIT, Party.STATISTICS, Party.AUTH)) {
            names.append(loopbackOf(party)).append(' ').append(party.module()).append('\n');
        }
        names.append(MARKET_DATA_ADDRESS).append(" market-data\n");
        Path file = Workspace.workDirectory(name).resolve("owner-names.txt");
        try {
            Files.writeString(file, names.toString());
        } catch (IOException failure) {
            throw new IllegalStateException("Таблица имён периметра не записалась: " + file, failure);
        }
        return file.toString();
    }

    private static Map<String, String> vault() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("spring.cloud.vault.uri", Substrate.vaultAddress());
        values.put("spring.cloud.vault.authentication", "TOKEN");
        values.put("spring.cloud.vault.token", Substrate.VAULT_TOKEN);
        values.put("spring.cloud.vault.scheme", "https");
        values.put("spring.cloud.vault.host", "127.0.0.1");
        values.put("spring.cloud.vault.port", "1");
        return values;
    }

    private Map<String, String> outgoingIdentity(Party party) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("spring.security.oauth2.client.provider.platform.token-uri", identity.tokenUri());
        values.put("spring.security.oauth2.client.registration.platform-services.client-id", party.module());
        values.put("spring.security.oauth2.client.registration.platform-services.client-secret",
                party.module() + "-secret");
        return values;
    }

    private static Map<String, String> datasource(Database database) {
        Map<String, String> values = new HashMap<>();
        values.put("spring.datasource.url", database.url());
        values.put("spring.datasource.username", database.username());
        values.put("spring.datasource.password", database.password());
        values.put("spring.datasource.hikari.maximum-pool-size", "3");
        values.put("spring.datasource.hikari.minimum-idle", "0");
        return values;
    }


    /**
     * Раскладка тропы: какие стороны поднимаются процессами и как они
     * находят друг друга.
     */
    private enum Layout {

        /** Тропа сделки: пять сторон, {@code auth} — стаб, адреса — свободные порты. */
        DEAL_PATH(List.of(Party.CONNECTOR, Party.TRADING_CORE, Party.STRATEGIES, Party.AUDIT, Party.STATISTICS)),

        /**
         * Тропа периметра: семь сторон, {@code auth} — сторона; адреса —
         * конвенция кластера, потому что периметр собирает адрес владельца
         * шаблоном из имени.
         */
        PERIMETER(List.of(Party.values()));

        private final List<Party> parties;

        Layout(List<Party> parties) {
            this.parties = parties;
        }

        List<Party> parties() {
            return parties;
        }
    }

    /**
     * Ответ поверхности стороны.
     *
     * @param status код
     * @param body   тело
     */
    public record Answer(Integer status, String body) {
    }
}
