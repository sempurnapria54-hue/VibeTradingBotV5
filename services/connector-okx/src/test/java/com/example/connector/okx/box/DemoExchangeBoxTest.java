package com.example.connector.okx.box;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.awaitility.Awaitility.await;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.connector.okx.integration.external.api.client.OkxWriteLoggingInterceptor;
import com.example.connector.okx.util.OkxConstants;
import com.example.tradingbot.domain.exchange.ExchangeFailureClass;
import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import com.example.tradingbot.domain.util.ExchangeAccountSecretFields;
import com.example.tradingbot.domain.util.InternalIdFactory;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Мишень {@code -D} — тот же ящик против ЖИВОЙ demo-площадки OKX: кейсы
 * {@code B2.1-D}, {@code B3.1-D}, {@code B6.1-D} и группа {@code B11}
 * документа `.claude/tests/cases/connector-okx.md`.
 *
 * <p><b>Класс — на конфигурацию контекста</b>: адрес площадки здесь живой
 * ({@code okx.base-url} = {@link DemoExchange#BASE_URL}), а не стаб; прочие оси
 * штатные. Ключи demo-счёта держателя кладутся в хранилище субстрата ящика
 * ПО АДРЕСУ СЧЁТА общей формой ({@code ExchangeAccountKeyPath}: окружение
 * {@code dev}, счёт {@link #DEMO_ACCOUNT}) с контуром {@code DEMO} — ровно так,
 * как их кладёт {@code auth} на стенде; заголовок демо-контура ставит сам
 * коннектор по контуру ключей.
 *
 * <p><b>В пайплайн не входит.</b> Метка {@code demo} изъята из умолчания
 * прогона свойством {@code test.excluded.groups} модуля; мишень идёт по
 * запросу и по расписанию (.claude/tests/cases/connector-okx.md §«Две мишени
 * и суффикс метки»). Профиль-гейт структурный: профиль {@code prod} — класс не
 * исполняется (.claude/skills/test-code.md §«Ключевые правила»).
 *
 * <p><b>Счёт общий с живыми стратегиями стенда, и это задаёт три выбора.</b>
 * <ul>
 *   <li><b>инструмент</b> — {@link #DEMO_INSTRUMENT}: его не ведёт ни одна
 *       стратегия (они на {@code ETH-USDT-SWAP} и {@code SOL-USDT-SWAP}), а
 *       детектор чужого инструмента ядра его не берёт — контур ядра есть весь
 *       каталог площадки (docs/components/AnomalyJob.md, детектор {@code A2});</li>
 *   <li><b>идентификатор заявки</b> — с маркером контура
 *       ({@link InternalIdFactory#forExchangeBoundEntity()}): заявка без маркера
 *       на инструменте каталога — детектор {@code A7}, жёсткая ступень счёта
 *       со сворачиванием живых стратегий; с маркером она в худшем случае хвост
 *       {@code A9} — мягкая ступень ОДНОЙ пары. Живёт заявка секунды, гистерезис
 *       обоих — два тика по минуте;</li>
 *   <li><b>радиус проверки конца</b> — инструмент кейсов и настройки счёта
 *       ({@link DemoAccount}): сущности стратегий меняются независимо от набора.</li>
 * </ul>
 *
 * <p><b>Заявка неисполнима и минимальна.</b> Цена снимается тем же прогоном у
 * demo-контура — того, где заявка встанет: лучшая покупка минус десятая доля,
 * не ниже нижней границы цены площадки, округлено вверх к шагу цены; размер —
 * минимальный размер правил инструмента.
 *
 * <p><b>Teardown — внутри кейса, авторитет — проверка конца.</b> Кейс снимает
 * свою заявку сам, затем снимок у площадки сверяется с исходным; расхождение —
 * жёсткий отказ плюс принудительная зачистка, а зачистка, которая не помогла,
 * ОСТАНАВЛИВАЕТ прогон: следующие stateful-кейсы отказывают, не трогая счёт
 * (.claude/tests/case-material/connector-okx.md §«2. Инвариант восстановления
 * состояния stateful-кейса»).
 */
@Tag("demo")
@DisabledIfSystemProperty(named = "spring.profiles.active", matches = DemoExchangeBoxTest.PROD_PROFILE)
@DisabledIfEnvironmentVariable(named = "SPRING_PROFILES_ACTIVE", matches = DemoExchangeBoxTest.PROD_PROFILE)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DemoExchangeBoxTest extends ConnectorBox {

    /** Профиль, при котором живой прогон не исполняется. */
    static final String PROD_PROFILE = "(.*,)?\\s*prod\\s*(,.*)?";

    private static final Logger LOG = LoggerFactory.getLogger(DemoExchangeBoxTest.class);

    /** Счёт demo в хранилище субстрата: адрес ключей — {@code dev/exchange-accounts/ACC-OKX-DEMO}. */
    private static final String DEMO_ACCOUNT = "ACC-OKX-DEMO";

    /** Инструмент кейсов: его не ведёт ни одна стратегия стенда. */
    private static final String DEMO_INSTRUMENT = "BTC-USDT-SWAP";

    /** Доля лучшей покупки, на которой ставится неисполнимая заявка. */
    private static final BigDecimal BID_DISTANCE = new BigDecimal("0.90");

    /** Число одновременных чтений серии {@code B11.4-D}: втрое выше лимита чтения времени. */
    private static final Integer BURST = 30;

    /** Сколько ждать осадки площадки после команды. */
    private static final Duration SETTLE = Duration.ofSeconds(20);

    private static final Duration POLL = Duration.ofSeconds(1);

    private static final String ORDERS = "/orders?externalInstrumentId=" + DEMO_INSTRUMENT;

    private static final String CANCELLATIONS = "/orders/cancellations?externalInstrumentId=" + DEMO_INSTRUMENT;

    private static final String PENDING = "/orders/pending/instrument?externalInstrumentId=" + DEMO_INSTRUMENT;

    private static final String POSITIONS_SWAP =
            OkxConstants.ACCOUNT_POSITIONS_PATH + "?instType=" + OkxConstants.INST_TYPE_SWAP;

    private static final String PRICE_LIMIT_PATH = "/api/v5/public/price-limit";

    /** Код площадки: ключ не того контура — demo-ключ без заголовка демо-контура. */
    private static final String CONTOUR_MISMATCH_CODE = "50101";

    private static final String INSTRUMENT_INVENTORY = "docs/models/integrations/okx/InstrumentOkxResponse.md";

    private static final String ORDER_INVENTORY = "docs/models/integrations/okx/OrderOkxResponse.md";

    /** Метка тела в строке журнала записи площадки ({@link OkxWriteLoggingInterceptor}). */
    private static final String WRITE_LOG_BODY = "body=";

    /** Записи, которые прогон обязан снять для стаба ({@code B11.5-D}). */
    private static final List<String> EXPECTED_RECORDS = List.of(
            "account-positions", "account-positions-without-contour", "public-instruments", "public-time",
            "market-ticker-demo", "public-price-limit-demo", "trade-order-place", "trade-order-live",
            "trade-cancel-order", "trade-order-canceled", "trade-orders-pending-after-cancel",
            "trade-orders-pending-before", "account-config-before", "account-leverage-info-before",
            "account-positions-instrument-before");

    /** Приёмник строк журнала записи: им снимаются сырые ответы площадки на команды. */
    private static final ListAppender<ILoggingEvent> WRITES = new ListAppender<>();

    private static DemoKeys tradeKeys;

    private static DemoKeys readKeys;

    private static DemoAccount.State initial;

    private static String stopReason;

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        ConnectorSubstrate.register(registry, Map.of("okx.base-url", DemoExchange.BASE_URL));
    }

    /**
     * Ключи держателя, исходный снимок счёта и ключи в хранилище субстрата.
     *
     * <p><b>Инструмент кейсов обязан быть чист на входе</b>: зачистка снимает
     * всё живое на нём, и законна она ровно потому, что всё это поставил
     * набор. Застав на нём чужое, набор не начинается — зачищать чужое он не
     * вправе.
     */
    @BeforeAll
    static void demoAccount() {
        tradeKeys = DemoKeys.trade();
        readKeys = DemoKeys.readOnly();
        initial = DemoAccount.snapshot(readKeys, DEMO_INSTRUMENT, "before");
        if (isFalse(initial.instrumentClean())) {
            throw new IllegalStateException("Мишень -D: на " + DEMO_INSTRUMENT + " demo-счёта уже есть живое — "
                    + initial + ". Набор не начинается: зачистка сняла бы не своё");
        }
        SecretStore.shared().put(DEMO_ACCOUNT, secretOf(tradeKeys));
    }

    /**
     * Приёмник журнала записи привязывается перед каждой клеткой: подъём
     * контекста переинициализирует логирование и снимает чужих приёмников
     * (ловушка TC-045).
     */
    @BeforeEach
    void captureWriteLog() {
        ch.qos.logback.classic.Logger writes =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(OkxWriteLoggingInterceptor.class);
        if (isFalse(WRITES.isStarted())) {
            WRITES.start();
        }
        if (isFalse(writes.isAttached(WRITES))) {
            writes.addAppender(WRITES);
        }
    }

    @Test
    @Order(1)
    @DisplayName("B11.1-D — ключи demo принимаются площадкой")
    void b11_1_demoKeysAreAcceptedByTheExchange() {
        String path = account(DEMO_ACCOUNT, "/positions");

        Answer answer = boxGet(path);

        assertThat(answer.status()).as("B11.1-D: вход GET %s; ответ %s", path, answer.body()).isEqualTo(200);
        assertThat(answer.carriesErrorDto()).as("B11.1-D: отказ вместо перечня — %s", answer.body()).isFalse();
        assertThat(answer.asList()).as("B11.1-D: тело — перечень позиций").isNotNull();

        // Контроль: тот же торговый demo-ключ без заголовка контура площадка
        // отвергает — без этого 200 предмета не доказывал бы, что заголовок ушёл.
        DemoExchange.Raw withoutContour = DemoExchange.signedGetWithoutContour(tradeKeys, POSITIONS_SWAP);
        assertThat(withoutContour.code())
                .as("B11.1-D, контроль: demo-ключ без заголовка демо-контура; ответ %s %s",
                        withoutContour.status(), withoutContour.body())
                .isEqualTo(CONTOUR_MISMATCH_CODE);
        DemoRecordings.keep("account-positions-without-contour", withoutContour.body());
        DemoRecordings.keep("account-positions", DemoExchange.signedGet(readKeys, POSITIONS_SWAP).required().body());
    }

    @Test
    @Order(2)
    @DisplayName("B6.1-D — публичное чтение на живой площадке как детектор дрейфа")
    void b6_1_aPublicReadOnTheLiveExchangeDetectsDrift() {
        String path = market("/instruments?externalInstrumentType=" + OkxConstants.INST_TYPE_SWAP);

        Answer listing = boxGet(path);

        assertThat(listing.status()).as("B6.1-D: вход GET %s; ответ %s", path, listing.body()).isEqualTo(200);
        List<Map<String, Object>> instruments = listing.asList();
        assertThat(instruments).as("B6.1-D: листинг пуст").isNotEmpty();
        assertThat(instruments).as("B6.1-D: в листинге нет %s", DEMO_INSTRUMENT)
                .anySatisfy(instrument -> assertThat(instrument.get("externalId")).isEqualTo(DEMO_INSTRUMENT));

        DemoExchange.Raw raw = DemoExchange.publicGet(OkxConstants.INSTRUMENTS_PATH + "?instType="
                + OkxConstants.INST_TYPE_SWAP + "&instId=" + DEMO_INSTRUMENT).required();
        DemoRecordings.keep("public-instruments", raw.body());
        assertThat(raw.data()).as("B6.1-D: сырой ответ площадки по %s", DEMO_INSTRUMENT).hasSize(1);
        Set<String> fields = raw.data().getFirst().keySet();
        ApiInventory inventory = ApiInventory.of(INSTRUMENT_INVENTORY);
        observe("B6.1-D", INSTRUMENT_INVENTORY, inventory, fields);
        assertThat(inventory.missingFrom(fields))
                .as("B6.1-D: поля, объявленные инвентарём %s, ответ площадки не несёт — находка в дом апидока"
                        + " с провенансом «рантайм», а не правка ожидания", INSTRUMENT_INVENTORY)
                .isEmpty();
    }

    @Test
    @Order(3)
    @DisplayName("B2.1-D, B3.1-D — заявка ставится на живой площадке и читается по идентификатору")
    void b2_1_b3_1_anOrderPlacedOnTheLiveExchangeIsReadBackByIdentifier() {
        requireRunNotStopped("B2.1-D");
        String internalId = InternalIdFactory.forExchangeBoundEntity();
        Placement placement = unexecutablePlacement("B2.1-D");
        AtomicReference<String> externalId = new AtomicReference<>();
        Throwable caseFailure = null;
        try {
            Answer ack = boxPost(account(DEMO_ACCOUNT, ORDERS), placement.order(internalId));

            assertThat(ack.status()).as("B2.1-D: вход %s; ответ %s", placement.order(internalId), ack.body())
                    .isEqualTo(200);
            Map<String, Object> acknowledged = ack.asObject();
            assertThat(acknowledged.get("success")).as("B2.1-D: подтверждение %s", ack.body())
                    .isEqualTo(Boolean.TRUE);
            assertThat(acknowledged.get("externalId")).as("B2.1-D: биржевой идентификатор пуст").isNotNull();
            externalId.set(String.valueOf(acknowledged.get("externalId")));
            assertThat(externalId.get()).as("B2.1-D: биржевой идентификатор пуст").isNotBlank();
            assertThat(acknowledged.get("internalId")).as("B2.1-D: наш идентификатор не вернулся")
                    .isEqualTo(internalId);
            assertThat(acknowledged).as("B2.1-D: поля конверта источника наружу").doesNotContainKeys("data", "msg");
            DemoRecordings.keep("trade-order-place", lastWriteBody(OkxConstants.TRADE_ORDER_PATH));

            Map<String, Object> read = awaitOrder("B3.1-D", externalId.get(), order -> Boolean.TRUE);

            assertThat(read.get("status")).as("B3.1-D: статус живой заявки — %s", read).isEqualTo("ACTIVE");
            assertThat(read.get("externalId")).as("B3.1-D: биржевой идентификатор").isEqualTo(externalId.get());
            assertThat(read.get("internalId")).as("B3.1-D: наш идентификатор").isEqualTo(internalId);
            DemoExchange.Raw raw = DemoExchange.signedGet(readKeys, orderOnExchange(externalId.get())).required();
            DemoRecordings.keep("trade-order-live", raw.body());
            observe("B3.1-D", ORDER_INVENTORY, ApiInventory.of(ORDER_INVENTORY), raw.data().getFirst().keySet());
        } catch (AssertionError | RuntimeException failure) {
            caseFailure = failure;
        }
        finish("B2.1-D", caseFailure, () -> cancelViaBox(internalId, externalId.get(), placement));
    }

    @Test
    @Order(4)
    @DisplayName("B11.2-D — цепочка состояния: поставить → прочитать → снять → прочитать")
    void b11_2_placeReadCancelReadChain() {
        requireRunNotStopped("B11.2-D");
        assertThat(DemoAccount.snapshot(readKeys, DEMO_INSTRUMENT, "").instrumentClean())
                .as("B11.2-D, предусловие: на %s нет живых заявок", DEMO_INSTRUMENT).isTrue();
        String internalId = InternalIdFactory.forExchangeBoundEntity();
        Placement placement = unexecutablePlacement("B11.2-D");
        AtomicReference<String> externalId = new AtomicReference<>();
        AtomicBoolean canceled = new AtomicBoolean(false);
        Throwable caseFailure = null;
        try {
            Answer ack = boxPost(account(DEMO_ACCOUNT, ORDERS), placement.order(internalId));
            assertThat(ack.status()).as("B11.2-D: постановка; ответ %s", ack.body()).isEqualTo(200);
            assertThat(ack.asObject().get("success")).as("B11.2-D: постановка; ответ %s", ack.body())
                    .isEqualTo(Boolean.TRUE);
            externalId.set(String.valueOf(ack.asObject().get("externalId")));

            Map<String, Object> live = awaitOrder("B11.2-D", externalId.get(), order -> Boolean.TRUE);
            assertThat(live.get("status")).as("B11.2-D: после постановки — %s", live).isEqualTo("ACTIVE");

            Answer cancel = boxPost(account(DEMO_ACCOUNT, CANCELLATIONS),
                    placement.cancellation(internalId, externalId.get()));
            assertThat(cancel.status()).as("B11.2-D: снятие; ответ %s", cancel.body()).isEqualTo(200);
            assertThat(cancel.asObject().get("success")).as("B11.2-D: снятие; ответ %s", cancel.body())
                    .isEqualTo(Boolean.TRUE);
            canceled.set(true);
            DemoRecordings.keep("trade-cancel-order", lastWriteBody(OkxConstants.TRADE_CANCEL_ORDER_PATH));

            Map<String, Object> closed = awaitOrder("B11.2-D", externalId.get(),
                    order -> isFalse(Objects.equals("ACTIVE", order.get("status"))));
            assertThat(closed.get("status")).as("B11.2-D: после снятия — %s", closed).isEqualTo("CANCELED");
            assertThat(closed.get("closeReason")).as("B11.2-D: причина закрытия проставлена границей — %s", closed)
                    .isNull();
            DemoRecordings.keep("trade-order-canceled",
                    DemoExchange.signedGet(readKeys, orderOnExchange(externalId.get())).required().body());

            Answer pending = boxGet(account(DEMO_ACCOUNT, PENDING));
            assertThat(pending.status()).as("B11.2-D: живые заявки; ответ %s", pending.body()).isEqualTo(200);
            assertThat(pending.asList()).as("B11.2-D: снятая заявка осталась в живых по ящику")
                    .noneSatisfy(order -> assertThat(order.get("externalId")).isEqualTo(externalId.get()));
            DemoExchange.Raw onExchange = DemoAccount.liveOrdersRaw(readKeys, DEMO_INSTRUMENT);
            DemoRecordings.keep("trade-orders-pending-after-cancel", onExchange.body());
            assertThat(onExchange.data()).as("B11.2-D: снятая заявка осталась в живых у площадки")
                    .noneSatisfy(order -> assertThat(order.get("ordId")).isEqualTo(externalId.get()));
        } catch (AssertionError | RuntimeException failure) {
            caseFailure = failure;
        }
        finish("B11.2-D", caseFailure, () -> {
            if (isFalse(canceled.get())) {
                cancelViaBox(internalId, externalId.get(), placement);
            }
        });
    }

    @Test
    @Order(5)
    @DisplayName("B11.4-D — превышение лимита частоты — backoff, а не отказ кейса")
    void b11_4_aRateLimitAnswerIsBackedOffNotFailed() throws Exception {
        String path = market("/time");
        Integer before = Funnel.limitedAnswers();
        ExecutorService pool = Executors.newFixedThreadPool(BURST);
        List<Answer> answers = new ArrayList<>();
        try {
            List<Future<Answer>> calls = IntStream.range(0, BURST)
                    .mapToObj(index -> pool.submit(() ->
                            Funnel.withBackoff(() -> get(path), DemoExchangeBoxTest::rateLimited, index)))
                    .collect(Collectors.toList());
            for (Future<Answer> call : calls) {
                answers.add(call.get(Funnel.BUDGET.multipliedBy(2).toSeconds(), TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(answers).as("B11.4-D: серия из %s чтений %s", BURST, path)
                .allSatisfy(answer -> assertThat(answer.status())
                        .as("B11.4-D: ответ после повтора — %s", answer.body()).isEqualTo(200));
        assertThat(Funnel.limitedAnswers() - before)
                .as("B11.4-D: серия из %s одновременных чтений не встретила ни одного ответа о лимите — backoff не"
                        + " измерен", BURST)
                .isPositive();
        DemoRecordings.keep("public-time", DemoExchange.publicGet(OkxConstants.PUBLIC_TIME_PATH).required().body());
    }

    @Test
    @Order(6)
    @DisplayName("B11.3-D — возврат счёта к исходному состоянию проверяется, а не подразумевается")
    void b11_3_theAccountReturnIsCheckedNotAssumed() {
        String failure = returnInstrument("B11.3-D", () -> { });

        if (nonNull(failure)) {
            fail(failure);
        }
        DemoAccount.snapshot(readKeys, DEMO_INSTRUMENT, "after");
    }

    @Test
    @Order(7)
    @DisplayName("B11.5-D — записи для стаба снимаются этим же прогоном")
    void b11_5_stubRecordsAreTakenByThisRun() {
        List<Path> written = DemoRecordings.flush();

        assertThat(DemoRecordings.names()).as("B11.5-D: записи прогона в %s", DemoRecordings.DIRECTORY)
                .containsAll(EXPECTED_RECORDS);
        assertThat(written).as("B11.5-D: пустая запись").allSatisfy(file -> assertThat(sizeOf(file)).isPositive());
        LOG.info("[-D] B11.5-D: записи стаба — {}", written);
    }

    // --- обвязка -----------------------------------------------------------

    /** Чтение поверхности ящика через воронку. */
    private Answer boxGet(String path) {
        return Funnel.paced(() -> get(path), DemoExchangeBoxTest::rateLimited);
    }

    /** Команда поверхности ящика через воронку: на ответе о лимите заявка не поставлена, повтор безопасен. */
    private Answer boxPost(String path, String body) {
        return Funnel.paced(() -> post(path, body), DemoExchangeBoxTest::rateLimited);
    }

    /**
     * Ответ ящика о превышении лимита площадки: класс отказа границы
     * {@code EXCHANGE_ERROR}, в сообщении — код площадки.
     */
    private static Boolean rateLimited(Answer answer) {
        if (isFalse(answer.carriesErrorDto())) {
            return Boolean.FALSE;
        }
        return Objects.equals(ExchangeFailureClass.EXCHANGE_ERROR.name(), answer.errorCode())
                && String.valueOf(answer.asObject().get("message")).contains("code=" + DemoExchange.RATE_LIMIT_CODE);
    }

    /** Неисполнимая минимальная заявка на покупку: цена и размер сняты с площадки этим прогоном. */
    private Placement unexecutablePlacement(String label) {
        String rulesPath = market("/instruments/" + DEMO_INSTRUMENT + "/rules?externalInstrumentType="
                + OkxConstants.INST_TYPE_SWAP);
        Answer rules = boxGet(rulesPath);
        assertThat(rules.status()).as("%s: правила инструмента; ответ %s", label, rules.body()).isEqualTo(200);
        BigDecimal tick = new BigDecimal(String.valueOf(rules.asObject().get("externalTickSize")));
        BigDecimal size = new BigDecimal(String.valueOf(rules.asObject().get("externalMinSize")));

        DemoExchange.Raw ticker = DemoExchange.demoPublicGet(OkxConstants.MARKET_TICKER_PATH
                + "?instId=" + DEMO_INSTRUMENT).required();
        DemoExchange.Raw limits = DemoExchange.demoPublicGet(PRICE_LIMIT_PATH + "?instId=" + DEMO_INSTRUMENT).required();
        DemoRecordings.keep("market-ticker-demo", ticker.body());
        DemoRecordings.keep("public-price-limit-demo", limits.body());
        BigDecimal bid = new BigDecimal(String.valueOf(ticker.data().getFirst().get("bidPx")));
        Object lowest = limits.data().getFirst().get("sellLmt");
        BigDecimal floor = isNull(lowest) || isBlank(String.valueOf(lowest)) ? BigDecimal.ZERO
                : new BigDecimal(String.valueOf(lowest));
        BigDecimal price = bid.multiply(BID_DISTANCE).max(floor)
                .divide(tick, 0, RoundingMode.CEILING)
                .multiply(tick);
        assertThat(price)
                .as("%s: неисполнимой цены не поставить — лучшая покупка demo %s, нижняя граница цены %s",
                        label, bid, floor)
                .isLessThan(bid);
        return new Placement(price.stripTrailingZeros(), size.stripTrailingZeros());
    }

    /** Чтение заявки ящиком, пока она не появится и не придёт в названное состояние. */
    private Map<String, Object> awaitOrder(String label, String externalId, Predicate<Map<String, Object>> settled) {
        String path = account(DEMO_ACCOUNT, "/orders/lookup?externalInstrumentId=" + DEMO_INSTRUMENT
                + "&externalId=" + externalId);
        AtomicReference<Answer> last = new AtomicReference<>();
        try {
            await().atMost(SETTLE).pollDelay(Duration.ZERO).pollInterval(POLL).until(() -> {
                Answer answer = boxGet(path);
                last.set(answer);
                if (isFalse(Objects.equals(200, answer.status())) || isBlank(answer.body())) {
                    return Boolean.FALSE;
                }
                return settled.test(answer.asObject());
            });
        } catch (ConditionTimeoutException notSettled) {
            throw new AssertionError(label + ": чтение заявки " + externalId + " не сошлось за " + SETTLE
                    + "; последний ответ " + (isNull(last.get()) ? "нет" : last.get()), notSettled);
        }
        return last.get().asObject();
    }

    /** Снятие заявки ящиком — teardown кейса. */
    private void cancelViaBox(String internalId, String externalId, Placement placement) {
        if (isBlank(externalId)) {
            return;
        }
        boxPost(account(DEMO_ACCOUNT, CANCELLATIONS), placement.cancellation(internalId, externalId));
    }

    /**
     * Teardown кейса, затем проверка конца; отказ кейса и отказ возврата
     * доезжают оба.
     */
    private void finish(String label, Throwable caseFailure, Runnable teardown) {
        String returnFailure = returnInstrument(label, teardown);
        if (nonNull(caseFailure)) {
            if (nonNull(returnFailure)) {
                caseFailure.addSuppressed(new AssertionError(returnFailure));
            }
            if (caseFailure instanceof AssertionError assertion) {
                throw assertion;
            }
            throw (RuntimeException) caseFailure;
        }
        if (nonNull(returnFailure)) {
            fail(returnFailure);
        }
    }

    /**
     * Teardown, затем сверка снимка с исходным; расхождение — принудительная
     * зачистка и повторный снимок.
     *
     * @return пусто — счёт вернулся; иначе текст жёсткого отказа
     */
    private String returnInstrument(String label, Runnable teardown) {
        try {
            teardown.run();
        } catch (AssertionError | RuntimeException failure) {
            LOG.warn("[-D] {}: teardown не прошёл, решает проверка конца — {}", label, failure.getMessage());
        }
        DemoAccount.State end = settledState();
        if (Objects.equals(initial, end)) {
            return null;
        }
        LOG.warn("[-D] {}: счёт не вернулся к исходному ({} против {}) — принудительная зачистка", label, end, initial);
        try {
            DemoAccount.forceClean(tradeKeys, readKeys, DEMO_INSTRUMENT);
        } catch (RuntimeException failure) {
            LOG.warn("[-D] {}: принудительная зачистка отказала — {}", label, failure.getMessage());
        }
        DemoAccount.State cleaned = settledState();
        if (Objects.equals(initial, cleaned)) {
            return label + ": счёт не вернулся к исходному — было " + initial + ", стало " + end
                    + "; принудительная зачистка его вернула (жёсткий отказ кейса)";
        }
        stopReason = label + ": счёт не вернулся к исходному, и принудительная зачистка не помогла — было "
                + initial + ", осталось " + cleaned + ". Прогон -D остановлен: по грязному счёту он дальше не идёт";
        return stopReason;
    }

    /** Снимок счёта после осадки площадки: ждёт совпадения с исходным до {@link #SETTLE}. */
    private DemoAccount.State settledState() {
        AtomicReference<DemoAccount.State> last = new AtomicReference<>();
        try {
            await().atMost(SETTLE).pollDelay(Duration.ZERO).pollInterval(POLL).until(() -> {
                last.set(DemoAccount.snapshot(readKeys, DEMO_INSTRUMENT, ""));
                return Objects.equals(initial, last.get());
            });
        } catch (ConditionTimeoutException notSettled) {
            LOG.warn("[-D] счёт не пришёл к исходному за {}: {}", SETTLE, last.get());
        }
        return last.get();
    }

    /** Отказ stateful-кейса, если прогон остановлен грязным счётом. */
    private static void requireRunNotStopped(String label) {
        if (nonNull(stopReason)) {
            fail(label + ": не исполняется — " + stopReason);
        }
    }

    /** Сырой ответ площадки на последнюю команду пути — из журнала записи коннектора. */
    private static String lastWriteBody(String path) {
        String mark = "[" + path + "]";
        List<ILoggingEvent> events = List.copyOf(WRITES.list);
        for (int index = events.size() - 1; index >= 0; index--) {
            String message = events.get(index).getFormattedMessage();
            if (message.contains(mark) && message.contains(WRITE_LOG_BODY)) {
                return message.substring(message.indexOf(WRITE_LOG_BODY) + WRITE_LOG_BODY.length());
            }
        }
        throw new AssertionError("B11.5-D: сырого ответа площадки на " + path + " в журнале записи нет");
    }

    /** Сверка сырой записи с инвентарём апидока: расхождение — находка, а не ожидание кейса. */
    private static void observe(String label, String doc, ApiInventory inventory, Set<String> fields) {
        Set<String> unaccounted = inventory.unaccountedIn(fields);
        Set<String> missing = inventory.missingFrom(fields);
        if (isNotEmpty(unaccounted) || isNotEmpty(missing)) {
            LOG.warn("[находка -D] {}: ответ площадки расходится с инвентарём {} — вне инвентаря {}, объявлено и"
                    + " не пришло {}. Находка в дом апидока с провенансом «рантайм», не правка ожидания",
                    label, doc, unaccounted, missing);
        }
    }

    /** Путь чтения заявки у площадки. */
    private static String orderOnExchange(String externalId) {
        return OkxConstants.TRADE_ORDER_PATH + "?instId=" + DEMO_INSTRUMENT + "&ordId=" + externalId;
    }

    /** Секрет счёта по общей форме полей: ключи держателя плюс контур {@code DEMO}. */
    private static Map<String, String> secretOf(DemoKeys keys) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put(ExchangeAccountSecretFields.API_KEY, escaped(keys.apiKey()));
        fields.put(ExchangeAccountSecretFields.SECRET, escaped(keys.secret()));
        fields.put(ExchangeAccountSecretFields.PASSPHRASE, escaped(keys.passphrase()));
        fields.put(ExchangeAccountSecretFields.CONTOUR, ExchangeAccount.Contour.DEMO.name());
        return fields;
    }

    /** Значение для строки JSON: хранилище субстрата собирает тело текстом. */
    private static String escaped(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static Long sizeOf(Path file) {
        try {
            return Files.size(file);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    /**
     * Заявка кейса: цена и размер, снятые с площадки.
     *
     * @param price неисполнимая цена покупки
     * @param size  минимальный размер, контракты
     */
    private record Placement(BigDecimal price, BigDecimal size) {

        /** Тело постановки: доменная заявка. */
        String order(String internalId) {
            return "{\"internalId\":\"" + internalId + "\",\"side\":\"BUY\",\"size\":" + size.toPlainString()
                    + ",\"price\":" + price.toPlainString() + "}";
        }

        /** Тело снятия: оба идентификатора. */
        String cancellation(String internalId, String externalId) {
            return "{\"internalId\":\"" + internalId + "\",\"externalId\":\"" + externalId + "\",\"side\":\"BUY\","
                    + "\"size\":" + size.toPlainString() + ",\"price\":" + price.toPlainString() + "}";
        }
    }
}
