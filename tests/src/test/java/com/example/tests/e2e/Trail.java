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
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
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

    public static final String EXCHANGE_LEVERAGE = "/api/v5/account/set-leverage";

    public static final String EXCHANGE_ORDER = "/api/v5/trade/order";

    public static final String EXCHANGE_ALGO_PENDING = "/api/v5/trade/orders-algo-pending";

    public static final String EXCHANGE_POSITIONS = "/api/v5/account/positions";

    public static final String EXTERNAL_ORDER = "okx-order-1";

    public static final String EXTERNAL_PROTECTION = "okx-algo-1";

    public static final String ENTRY_PRICE = "2000";

    private static final String NEVER = "0 0 0 1 1 *";

    private static final Integer OWNER_PORT = 8080;

    private static final Integer LOOPBACK_BASE = 21;

    private static final String MARKET_DATA_ADDRESS = "127.0.0.40";

    private static final Duration TICK_WAIT = Duration.ofSeconds(90);

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
    private Boolean projectionsSynced = Boolean.FALSE;
    private Boolean riskAppetiteSet = Boolean.FALSE;
    private Boolean leverageAssigned = Boolean.FALSE;
    private Boolean feeRatesSynced = Boolean.FALSE;
    private String tenant = TENANT;
    private String account = ACCOUNT;

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
        }
    }

    // ---------------------------------------------------------------- предусловия

    /**
     * Ходы тропы идут под названными тенантом и счётом.
     *
     * <p>У тропы сделки оба постоянны — счёт отдаёт стаб владельца реестра.
     * У тропы периметра владелец реестра — сторона: тенанта заводит первый
     * ход самой тропы, а идентичность счёта выдаёт его регистрация, и
     * пролог идёт под ними (.claude/tests/cases/e2e-perimeter-read.md
     * §«Предусловия тропы — что лежит до первого хода»).
     *
     * @param tenantInternalId  тенант ходов
     * @param accountInternalId биржевой счёт ходов
     */
    public void under(String tenantInternalId, String accountInternalId) {
        this.tenant = tenantInternalId;
        this.account = accountInternalId;
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
     * Сделок у ядра нет: ядро их не заводило либо поднято заново.
     *
     * <p>Сделку на паре снять нечем, кроме её собственного терминала, а
     * терминал — предмет другой тропы; поэтому кейс, которому нужна пара
     * без сделки после чужой, получает свежее развёртывание ядра тем же
     * способом, что {@link #withoutProjections()}. Общие предусловия после
     * этого ставятся заново.
     */
    public void withoutDeals() {
        if (isFalse(database(Party.TRADING_CORE).query("select id from deals").isEmpty())) {
            renew(Party.TRADING_CORE);
        }
        commonPreconditions();
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
    }

    /**
     * Забывает следы предусловий: журналы стабов и отметки журналов доступа.
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
    }

    /** Обращения к поверхности стороны после последнего забывания, без проб живости. */
    public List<Side.Access> accesses(Party party) {
        return side(party).accessSince(accessMarks.getOrDefault(party, 0)).stream()
                .filter(access -> isFalse(access.isProbe()))
                .toList();
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
        Answer answer = call(Party.STRATEGIES, "POST", STRATEGIES, tenant,
                referenceDefinition().replace("\"" + ACCOUNT + "\"", "\"" + account + "\""));
        if (answer.status() != 201) {
            throw new IllegalStateException("Предусловие не поставлено: создание определения — "
                    + answer.status() + " " + answer.body());
        }
        return String.valueOf(Json.object(answer.body()).get("internalId"));
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
        retireActiveDefinitions();
        String definition = createDefinition();
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
        marketData.answersPost(PEER_FEATURES, entryFeaturesBody());
    }

    /**
     * Стаб площадки принимает команды тропы: отдаёт свежий снимок средств,
     * подтверждает плечо и постановку, а заявку, которой ещё не наливал,
     * не знает.
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
        exchange.answersPost(EXCHANGE_LEVERAGE, """
                {"code": "0", "msg": "", "data": [{"lever": "10", "mgnMode": "isolated", "instId": "%s",
                  "posSide": "net"}]}
                """.formatted(EXTERNAL_INSTRUMENT));
        exchange.answersPostTemplated(EXCHANGE_ORDER, """
                {"code": "0", "msg": "", "data": [{"ordId": "%s", "clOrdId": "{{jsonPath request.body '$.clOrdId'}}",
                  "sCode": "0", "sMsg": "", "ts": "1758240000000"}]}
                """.formatted(EXTERNAL_ORDER));
        exchange.answers(EXCHANGE_ORDER, """
                {"code": "0", "msg": "", "data": []}
                """);
        exchange.answers(EXCHANGE_POSITIONS, """
                {"code": "0", "msg": "", "data": []}
                """);
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
     */
    public void factSeriesStartedYesterday() {
        OffsetDateTime yesterday = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("eventId", UUID.randomUUID().toString());
        headers.put("eventType", "DEAL_OPENED");
        headers.put("occurredAt", yesterday.toString());
        headers.put("version", "1");
        produce(Substrate.CORE_TOPIC, tenant, """
                {"dealInternalId": "%s", "exchangeAccountInternalId": "%s", "instrumentInternalId": "%s",
                 "strategyInternalId": "%s", "entryReason": "STRATEGY", "direction": "LONG",
                 "entryMarketPhase": "BULL_TREND"}
                """.formatted(UUID.randomUUID(), account, INSTRUMENT, UUID.randomUUID()), headers);
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
                .query("select id from orders where external_id is not null").isEmpty()));
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
        Map<String, Object> order = core.query(
                "select internal_id, size from orders where external_id is not null").getFirst();
        Map<String, Object> protection = core.query(
                "select internal_id, size, stop_loss_trigger_price from attached_algo_orders").getFirst();
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

    /** Конец темы на её единственной партиции — смещение следующей записи. */
    public Long endOffset(String topic) {
        return (long) records(topic).size();
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
        auth.answers(PEER_ACCOUNTS, accountsBody(ACCOUNT));
        marketData.answers(PEER_INSTRUMENTS, instrumentsBody(INSTRUMENT, EXTERNAL_INSTRUMENT));
        marketData.answers(PEER_INSTRUMENTS + "/" + INSTRUMENT + "/rules", rulesBody(EXTERNAL_INSTRUMENT));
        exchange.answers(EXCHANGE_FEE, """
                {"code": "0", "msg": "", "data": [{"instType": "SWAP", "level": "Lv1", "ts": "1758240000000",
                  "taker": "-0.0005", "maker": "-0.0002",
                  "feeGroup": [{"groupId": "1", "taker": "-0.0005", "maker": "-0.0002"}]}]}
                """);
        exchange.answers(EXCHANGE_TIME, """
                {"code": "0", "msg": "", "data": [{"ts": "%d"}]}
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

    private static String accountsBody(String accountInternalId) {
        return """
                [{
                  "internalId": "%s",
                  "tenantInternalId": "%s",
                  "exchangeCode": "OKX",
                  "label": "e2e account",
                  "contour": "%s",
                  "status": "ACTIVE"
                }]
                """.formatted(accountInternalId, TENANT, CONTOUR);
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
                  "externalState": "live"
                }
                """.formatted(externalInstrumentId, externalInstrumentId.split("-")[0]);
    }

    /**
     * Раскладка фич момента, на которой входной шаг бычьей детали эталона
     * истинен: фаза — бычий тренд, быстрая средняя выше медленной, осциллятор
     * не ниже порога; плюс волатильность под стоп и цены момента под расчёт
     * заявки.
     *
     * <p><b>Раскладка ключуется авторскими именами операндов эталона</b> —
     * так её отдаёт владелец данных (форму соседа мерит его ящик), и имя вне
     * эталона гасило бы условие молча, как отсутствующий операнд.
     */
    private static String entryFeaturesBody() {
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
                  "marketPhase": {"type": "BULL_TREND"},
                  "marketPriceData": {
                    "externalLastPrice": "%2$s",
                    "externalBidPrice": "1999.9",
                    "externalAskPrice": "2000.1",
                    "externalBidSize": "500",
                    "externalAskSize": "500",
                    "externalTimestamp": "%1$s"
                  }
                }
                """.formatted(candle, ENTRY_PRICE);
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
            }
            case CONNECTOR -> {
                values.putAll(vault());
                values.put("okx.base-url", exchange.baseUrl());
            }
            case AUDIT -> {
                Database database = databases.get(party);
                values.put("audit.persistence.journal.url", database.url());
                values.put("audit.persistence.journal.username", database.username());
                values.put("audit.persistence.journal.password", database.password());
                values.put("audit.persistence.journal.max-pool-size", "3");
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
