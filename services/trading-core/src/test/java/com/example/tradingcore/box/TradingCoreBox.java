package com.example.tradingcore.box;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.aggregate.strategy.Strategy;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Чёрный ящик `trading-core`: реальный контекст сервиса со ВСЕМИ его
 * бинами, наблюдаемый только снаружи — своей поверхностью, базой, стабами
 * трёх соседей и журналом приложения
 * (.claude/decisions/test-contour-design-pass.md, решение 1).
 *
 * <p><b>Признак ящика — не форма запуска, а то, что ни один внутренний
 * бин не подменён.</b> Отсюда и форма обращения: свой HTTP-клиент по
 * адресу случайного порта, а не {@code MockMvc} и не автовайренный
 * {@code DealOrchestratorJob}. Наблюдатели субстрата ({@link Rows},
 * {@link PeerStub}) ходят своим соединением — ящик смотрит на субстрат
 * снаружи.
 *
 * <p><b>ТИК — ВХОД.</b> Семь проходов по расписанию производят почти всё,
 * что ядро производит, и подаёт их кейс сам — ручным фасадом
 * ({@link Tick}). Фасад асинхронен, поэтому след тика ждётся его
 * собственной записью в журнале, а не {@code Thread.sleep}: пауза платит
 * временем всегда и ничего не гарантирует.
 *
 * <p><b>ОБЪЁМ — новая ось формы этого предмета, и режет его единица
 * работы.</b> Единица кейса — одно наблюдаемое следствие одной единицы
 * работы: тик, вызов поверхности, принятое сообщение. Рёбра FSM,
 * неравенства риск-гейта и арифметика сайзинга ящику не принадлежат — их
 * предмет уровень 2 (.claude/tests/cases/trading-core.md §«Объём»).
 *
 * <p><b>Возраст данных ставится В ДАННЫХ.</b> Часы процесса не двигаются
 * ни в одном кейсе: снимок средств приезжает с нужным
 * {@code externalUpdatedAt}, строка проекции — с нужным моментом снимка,
 * отчёт — с нужным моментом заведения.
 *
 * <p><b>Свойств контекста здесь не объявлено ни одного, и это несущее.</b>
 * Перечень свойств задаёт КАЖДЫЙ класс кейсов своим методом
 * {@code @DynamicPropertySource}: положение осей конфигурации есть ВХОД
 * ящика, а унаследованный метод реестра перекрывал бы свои
 * переопределения в порядке, которого контракт реестра не обещает.
 * Цена названа — контекст на класс конфигурации, а не один на прогон;
 * классы, которым довольно штатного положения осей, берут его у
 * {@link SharedTradingCoreBox} и делят один контекст.
 *
 * <p><b>База опустошается перед каждой клеткой.</b> Вход клетки есть её
 * собственное состояние сделок, проекций и ступеней, и наследство
 * соседки сделало бы отрицания («строк не прибавилось», «команд на
 * бирже нет») утверждениями о прогоне, а не о клетке.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class TradingCoreBox {

    /** Корень поверхности сервиса. */
    protected static final String ROOT = "/api/v1/trading-core";

    /** Сделки счёта окном и сделка со своими траншами. */
    protected static final String DEALS = ROOT + "/deals";

    /** Торговое состояние биржевого счёта. */
    protected static final String SAFETY_STATES = ROOT + "/safety/states";

    /** Ручная постановка ступени. */
    protected static final String HALTS = ROOT + "/safety/halts";

    /** Ручное снятие ступени. */
    protected static final String HALT_CLEARANCES = ROOT + "/safety/halt-clearances";

    /** Числа риск-аппетита тенанта. */
    protected static final String RISK_APPETITES = ROOT + "/risk-appetites";

    /** Назначение торговых настроек счёта на инструменте. */
    protected static final String PAIR_SETTINGS = ROOT + "/pair-settings";

    /** Рабочее плечо, которое назначают кейсы, доводящие вход до команды. */
    protected static final Integer WORKING_LEVERAGE = 10;

    /** Проверка пары «счёт × инструмент». */
    protected static final String PAIR_CHECKS = ROOT + "/pair-checks";

    /** Ручной запуск тиков. */
    protected static final String JOBS = ROOT + "/jobs";

    /** Проба живости: единственная открытая точка контура доступа. */
    protected static final String HEALTH = "/actuator/health";

    /** Реестр биржевых счетов у владельца: путь его поверхности. */
    protected static final String PEER_ACCOUNTS = "/api/v1/auth/exchange-accounts";

    /** Каталог инструментов у владельца рыночных данных. */
    protected static final String PEER_INSTRUMENTS = "/api/v1/market-data/instruments";

    /** Биржевой момент у коннектора. */
    protected static final String PEER_SERVER_TIME = "/api/v1/market/time";

    /** Идентичность счёта, которым ходит большинство кейсов. */
    protected static final String ACCOUNT = "A1";

    /** Второй счёт: им наблюдается независимость счетов в обходе. */
    protected static final String SECOND_ACCOUNT = "A2";

    /** Тенант-владелец счетов прогона. */
    protected static final String TENANT = "T1";

    /** Инструмент, которым ходит большинство кейсов. */
    protected static final String INSTRUMENT = "I1";

    /** Второй инструмент: им наблюдается поинструментный обход. */
    protected static final String SECOND_INSTRUMENT = "I2";

    /** Имя первого инструмента у площадки. */
    protected static final String EXTERNAL_INSTRUMENT = "BTC-USDT-SWAP";

    /** Имя второго инструмента у площадки. */
    protected static final String SECOND_EXTERNAL_INSTRUMENT = "ETH-USDT-SWAP";

    /** Класс отказа «негодный вход вызова» единого error-DTO. */
    protected static final String INVALID_REQUEST = "INVALID_REQUEST";

    /** Класс отказа «сосед по ярусу недоступен» единого error-DTO. */
    protected static final String PEER_SERVICE_UNAVAILABLE = "PEER_SERVICE_UNAVAILABLE";

    /** Класс события, которым приезжает копия определения. */
    protected static final String STRATEGY_ACTIVATED = "STRATEGY_ACTIVATED";

    /** Класс события, которым копия уводится в неактивное состояние. */
    protected static final String STRATEGY_DEACTIVATED = "STRATEGY_DEACTIVATED";

    /** Класс события, которым копия уводится в логический терминал. */
    protected static final String STRATEGY_DELETED = "STRATEGY_DELETED";

    /** Потолок ожидания следа тика: он асинхронен, но не бесконечен. */
    private static final Duration TICK_TIMEOUT = Duration.ofSeconds(60);

    /**
     * Счётчик идентичностей событий прогона.
     *
     * <p><b>Идентичность назначается сквозной по ПРОГОНУ, а не по
     * клетке.</b> Тема брокера клетку переживает, и одинаковые
     * идентичности у соседних клеток сделали бы повтор доставки —
     * предмет отдельной клетки — неотличимым от нормального хода.
     */
    private static final AtomicInteger EVENTS = new AtomicInteger();

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    @LocalServerPort
    private Integer port;

    /** Наблюдатель строк базы субстрата. */
    protected final Rows rows = Rows.shared();

    /** Стаб и наблюдатель поверхности коннектора. */
    protected final PeerStub connector = PeerStub.connector();

    /** Стаб и наблюдатель поверхности владельца реестра счетов. */
    protected final PeerStub auth = PeerStub.auth();

    /** Стаб и наблюдатель поверхности владельца рыночных данных. */
    protected final PeerStub marketData = PeerStub.marketData();

    /** Стаб провайдера идентичности: им подписываются токены кейсов. */
    protected final IdentityStub identity = IdentityStub.stub();

    /**
     * Перед каждой клеткой: база пуста, стабы забыли и заготовки, и
     * записи.
     *
     * <p>Опустошение идёт БЕЗ сброса последовательностей — довод у
     * {@link Rows#clear()}.
     */
    @BeforeEach
    void resetSubstrate() {
        rows.clear();
        PeerStub.all().forEach(PeerStub::reset);
    }

    /**
     * Заводит счета в проекции ТРОПОЙ ЯЩИКА — тиком синка реестров — и
     * отдаёт их числовые ключи в порядке имён.
     *
     * <p><b>Предусловие ставится тиком, а не вставкой в базу:</b> иначе
     * кейс опирался бы на строку, которой сервис не производил, и зелёный
     * прогон говорил бы о нашей вставке, а не о его поведении.
     *
     * <p><b>Записи стабов после этого забываются.</b> Чтения предусловия
     * ушли в тот же журнал, что и чтения клетки, и без разделения
     * отрицание «запросов нет» не сошлось бы никогда.
     *
     * @param internalIds идентичности счетов у владельца реестра
     */
    protected void provisionAccounts(String... internalIds) {
        Map<String, String> byTenant = new LinkedHashMap<>();
        for (String internalId : internalIds) {
            byTenant.put(internalId, TENANT);
        }
        provisionAccountsOfTenants(byTenant);
    }

    /**
     * Заводит счета НАЗВАННЫХ тенантов тем же тиком синка.
     *
     * <p><b>Тенант — вход, а не умолчание:</b> ключ партиции события есть
     * тенант, и клетка о порядке публикации внутри тенанта без второго
     * тенанта предмета не имеет.
     *
     * @param tenantByAccount идентичность счёта → идентичность его тенанта,
     *                        в порядке заведения
     */
    protected void provisionAccountsOfTenants(Map<String, String> tenantByAccount) {
        List<String> listed = new ArrayList<>();
        tenantByAccount.forEach((internalId, tenantInternalId) ->
                listed.add(Feed.account(internalId, tenantInternalId, "DEMO", "ACTIVE")));
        auth.answers(PEER_ACCOUNTS, Feed.array(listed));
        marketData.answers(PEER_INSTRUMENTS, Feed.emptyArray());
        tick(Tick.REGISTRY_PROJECTIONS);
        PeerStub.all().forEach(PeerStub::forgetRequests);
    }

    /**
     * Заводит счета и инструменты в проекциях ТРОПОЙ ЯЩИКА — тем же тиком
     * синка реестров.
     *
     * @param accounts    идентичности счетов
     * @param instruments идентичности инструментов и их имена у площадки
     */
    protected void provision(List<String> accounts, Map<String, String> instruments) {
        List<String> listedAccounts = new ArrayList<>();
        accounts.forEach(internalId -> listedAccounts.add(Feed.account(internalId, TENANT, "DEMO", "ACTIVE")));
        List<String> listedInstruments = new ArrayList<>();
        instruments.forEach((internalId, externalId) -> {
            listedInstruments.add(Feed.instrument(internalId, externalId));
            marketData.answers(PEER_INSTRUMENTS + "/" + internalId + "/rules", Feed.instrumentRules(externalId));
        });
        auth.answers(PEER_ACCOUNTS, Feed.array(listedAccounts));
        marketData.answers(PEER_INSTRUMENTS, Feed.array(listedInstruments));
        tick(Tick.REGISTRY_PROJECTIONS);
        PeerStub.all().forEach(PeerStub::forgetRequests);
    }

    /**
     * Назначает рабочее плечо пары своей поверхностью: без него
     * risk-creating действие по паре отвергается преконтролем
     * (docs/rules/trading-constraints.md). Инструмент к этому моменту
     * обязан стоять в проекции — пара адресуется его идентичностью.
     *
     * <p>Стаб коннектора принимает настройку плеча тем же ходом: назначенное
     * плечо исполнитель постановки пишет площадке перед каждым входом.
     */
    protected void assignLeverage(String accountInternalId, String instrumentInternalId) {
        assertThat(put(PAIR_SETTINGS + "/" + accountInternalId + "/" + instrumentInternalId,
                Bodies.pairSettings(WORKING_LEVERAGE)).status()).isEqualTo(200);
        connector.answers("/api/v1/accounts/" + accountInternalId + "/leverage", Feed.leverageAck());
    }

    /**
     * Ставит копию определения ТРОПОЙ ЯЩИКА — сообщением активации в тему
     * владельца определений — и ждёт, пока слушатель её заведёт.
     *
     * <p><b>Сообщением, а не вставкой дерева в базу:</b> строки копии
     * производит слушатель, и вставка опиралась бы на строку, которой
     * сервис не производил (.claude/tests/cases/trading-core.md §«Где
     * живёт код кейсов»).
     *
     * <p><b>Записи стабов после этого забываются</b> — по тому же доводу,
     * что у предусловия проекций: приём определения чужих вызовов не
     * делает, но кейс о нём утверждает вместе с тиками, которые делают.
     *
     * @param definition снимок определения
     * @return идентичность события, которой оно было доставлено
     */
    protected String activate(Strategy definition) {
        String eventId = "ev-" + EVENTS.incrementAndGet();
        Wire.publishStrategyFact(strategyTopic(), eventId, STRATEGY_ACTIVATED,
                Definitions.activated(definition), TENANT);
        awaitStrategyStatus(definition.getInternalId(), Strategy.Status.ACTIVE.name());
        PeerStub.all().forEach(PeerStub::forgetRequests);
        return eventId;
    }

    /**
     * Кладёт событие жизненного цикла копии и ждёт, пока её статус не
     * встанет в ожидаемый.
     *
     * <p>Дерева содержимое такого события не несёт — у копии оно уже
     * лежит и неизменяемо, — поэтому предмет клетки здесь один: статус.
     *
     * @param eventType      класс события жизненного цикла
     * @param definition     снимок определения: из него берётся радиус
     * @param expectedStatus статус, которого клетка ждёт
     * @return идентичность события, которой оно было доставлено
     */
    protected String moveDefinition(String eventType, Strategy definition, String expectedStatus) {
        String eventId = "ev-" + EVENTS.incrementAndGet();
        Wire.publishStrategyFact(strategyTopic(), eventId, eventType,
                Definitions.lifecycle(definition), TENANT);
        awaitStrategyStatus(definition.getInternalId(), expectedStatus);
        return eventId;
    }

    /**
     * Кладёт активацию НАЗВАННОЙ идентичностью события: вход клеток о
     * повторной доставке.
     *
     * <p>Ждать здесь нечего: предмет такой клетки — что следствия НЕ
     * прибавилось, а «не прибавилось» наблюдается барьером
     * ({@link #barrier()}), а не паузой.
     *
     * @param eventId    идентичность события — та же, что у первой доставки
     * @param definition снимок определения
     */
    protected void redeliverActivation(String eventId, Strategy definition) {
        Wire.publishStrategyFact(strategyTopic(), eventId, STRATEGY_ACTIVATED,
                Definitions.activated(definition), TENANT);
    }

    /**
     * Барьер приёма: кладёт заведомо применимое сообщение и ждёт его
     * отметки в inbox.
     *
     * <p><b>Чем барьер держится.</b> Партия темы упорядочена, и обработка
     * идёт по порядку смещений: как только отметка барьера легла, всё
     * положенное ДО него обработку прошло. Без барьера утверждение «второй
     * копии не появилось» было бы утверждением о том, что мы не успели
     * посмотреть.
     *
     * <p><b>Барьером служит событие об определении, которого нет.</b> Его
     * исход штатен и наблюдаем одной строкой inbox, а копий он не трогает
     * вовсе — то есть барьер не подменяет предмета клетки.
     *
     * @return идентичность события барьера
     */
    protected String barrier() {
        String eventId = "ev-" + EVENTS.incrementAndGet();
        Strategy absent = Definitions.withDetail("absent-" + eventId, ACCOUNT, INSTRUMENT);
        Wire.publishStrategyFact(strategyTopic(), eventId, STRATEGY_DELETED,
                Definitions.lifecycle(absent), TENANT);
        Awaitility.await().atMost(TICK_TIMEOUT).pollInterval(Duration.ofMillis(50))
                .until(() -> rows.countWhere("inbox_events", "event_id", eventId) == 1L);
        return eventId;
    }

    /**
     * Тема владельца определений, которую читает ЭТОТ контекст.
     *
     * <p>Штатная — общая; класс со своим контекстом переопределяет её
     * своей ({@link TradingCoreSubstrate#registerOwn}), и довод у этого
     * тот же, что у своей группы потребителя: свежая группа читает тему с
     * начала, а тема переживает и клетку, и контекст.
     */
    protected String strategyTopic() {
        return TradingCoreSubstrate.STRATEGY_TOPIC;
    }

    /** Ждёт, пока копия определения не встанет в названный статус. */
    protected void awaitStrategyStatus(String internalId, String status) {
        Awaitility.await().atMost(TICK_TIMEOUT).pollInterval(Duration.ofMillis(50))
                .until(() -> Objects.equals(status, rows.row("strategies", "internal_id", internalId)
                        .get("status")));
    }

    /** Числовой ключ счёта проекции по его идентичности. */
    protected Long accountId(String internalId) {
        Object id = rows.row("exchange_accounts", "internal_id", internalId).get("id");
        if (Objects.isNull(id)) {
            throw new AssertionError("Предусловие не поставлено: счёта " + internalId + " в проекции нет");
        }
        return ((Number) id).longValue();
    }

    /** Числовой ключ инструмента проекции по его идентичности. */
    protected Long instrumentId(String internalId) {
        Object id = rows.row("instruments", "internal_id", internalId).get("id");
        if (Objects.isNull(id)) {
            throw new AssertionError("Предусловие не поставлено: инструмента " + internalId + " в каталоге нет");
        }
        return ((Number) id).longValue();
    }

    /** Семь проходов по расписанию: путь ручного фасада и след его конца. */
    protected enum Tick {

        /** Проход сопровождения нетерминальных сделок окна. */
        DEAL_ORCHESTRATOR("/deal-orchestrator", "Manual DealOrchestratorJob trigger finished"),

        /** Отбор входа по активным определениям. */
        ENTRY_SCANNER("/entry-scanner", "Manual EntryScannerJob trigger finished"),

        /** Проактивная детекция: живые факты площадки против наших строк. */
        ANOMALY_DETECTION("/anomaly-detection", "Manual AnomalyJob trigger finished"),

        /** Реле outbox: публикация накопленного. */
        OUTBOX_RELAY("/outbox-relay", "Manual OutboxRelayJob trigger finished"),

        /** Синк проекций чужих реестров: счета у `auth`, каталог у `market-data`. */
        REGISTRY_PROJECTIONS("/registry-projections", "Manual RegistryProjectionJob trigger finished"),

        /** Объявление потребности рядов и идентичностей владельцу данных. */
        STRATEGY_DEMAND("/strategy-demand", "Manual StrategyDemandJob trigger finished"),

        /** Синк ставок комиссии: приватное чтение у коннектора. */
        TRADE_FEE_RATES("/trade-fee-rates", "Manual TradeFeeRateSyncJob trigger finished");

        private final String suffix;
        private final String finishedMark;

        Tick(String suffix, String finishedMark) {
            this.suffix = suffix;
            this.finishedMark = finishedMark;
        }

        /** Путь ручного запуска. */
        String path() {
            return ROOT + "/jobs" + suffix;
        }

        /** Запись фасада о конце запуска: ею наблюдается, что тик отработал. */
        String finishedMark() {
            return finishedMark;
        }
    }

    /**
     * Подаёт тик ручным фасадом и ждёт, пока он отработает.
     *
     * <p><b>Ждётся запись ФАСАДА о конце запуска, а не пауза.</b> Фасад
     * асинхронен, и его ответ говорит только о запуске
     * (docs/rules/error-handling-policy.md); след работы наблюдается тем,
     * что фасад дописал в журнал.
     *
     * @param tick какой из семи проходов подаётся
     * @return ответ фасада на запуск
     */
    protected Answer tick(Tick tick) {
        Integer mark = AppLog.mark();
        Answer answer = post(tick.path(), "");
        awaitFinished(tick, mark, 1);
        return answer;
    }

    /**
     * Подаёт тик фасадом ДРУГОГО процесса ядра и ждёт его конца.
     *
     * <p>Вход тот же, что у {@link #tick(Tick)}, — ручной фасад под
     * сервисным токеном; меняется только процесс, который его принимает
     * ({@link CoreReplica}).
     *
     * @param processPort порт поверхности процесса
     * @param tick        какой из семи проходов подаётся
     * @return ответ фасада на запуск
     */
    protected Answer tickAt(Integer processPort, Tick tick) {
        Integer mark = AppLog.mark();
        Answer answer = send(authorized(request(processPort, tick.path()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("")));
        awaitFinished(tick, mark, 1);
        return answer;
    }

    /**
     * Подаёт тик и ждёт его конца НАЗВАННЫМ потолком.
     *
     * <p><b>Свой потолок — у тика, чья цена названа снаружи.</b> Тик
     * против недоступного брокера стои́т потолка ожидания раскладки у
     * публикующего клиента, а тот величиной конфигурации сервиса не
     * объявлен ({@link BrokerGate}); штатный потолок ожидания следа тика
     * короче, и клетка упёрлась бы в него, ничего не измерив.
     *
     * @param tick  какой из семи проходов подаётся
     * @param atMost потолок ожидания следа
     * @return ответ фасада на запуск
     */
    protected Answer tick(Tick tick, Duration atMost) {
        Integer mark = AppLog.mark();
        Answer answer = post(tick.path(), "");
        Awaitility.await().atMost(atMost).pollInterval(Duration.ofMillis(200))
                .until(countOf(tick, mark), count -> count >= 1);
        return answer;
    }

    /**
     * Поднимает МЯГКУЮ ступень счёта тропой поверхности: ею ставятся
     * строки outbox там, где сделки ещё не нужны.
     *
     * <p><b>Тропой поверхности, а не вставкой в outbox:</b> писатель строки
     * события — тот код, который пишет решение, и вставленная руками строка
     * говорила бы о нашей вставке, а не о его поведении
     * (.claude/tests/cases/trading-core.md §«Где живёт код кейсов»).
     *
     * <p>Класс {@code FREEZE} синхронен, поэтому ответ поверхности уже
     * означает применённую ступень.
     *
     * @param accountInternalId идентичность счёта — объект счётного радиуса
     * @return ответ поверхности на постановку
     */
    protected Answer freeze(String accountInternalId) {
        return post(HALTS, Bodies.halt("FREEZE", accountInternalId));
    }

    /**
     * Поднимает ЖЁСТКУЮ ступень счёта той же тропой и ждёт конца
     * асинхронного доведения: полный класс уходит фасадом и отвечает
     * {@code 202}.
     *
     * @param accountInternalId идентичность счёта
     */
    protected void fullHalt(String accountInternalId) {
        Integer mark = AppLog.mark();
        post(HALTS, Bodies.halt("FULL", accountInternalId));
        awaitLog(mark, "Holder full halt finished accountInternalId=" + accountInternalId);
    }

    /**
     * Подаёт тик названное число раз подряд, ждёт конца каждого.
     *
     * @param tick  какой из семи проходов подаётся
     * @param times сколько раз подряд
     */
    protected void ticks(Tick tick, Integer times) {
        for (int index = 0; index < times; index++) {
            tick(tick);
        }
    }

    /**
     * Подаёт два запуска подряд, НЕ дожидаясь конца первого: вход клеток о
     * перекрытии. Ожидание конца обоих — после подачи.
     *
     * <p><b>Ответы отдаются оба</b>, потому что предмет клетки о
     * перекрытии — и число ответов фасада, и число исполненных работ:
     * фасад отвечает о запуске, а исход работы наружу не транслируется
     * (docs/rules/error-handling-policy.md).
     *
     * @param tick какой из семи проходов подаётся
     * @return ответы фасада на оба запуска в порядке подачи
     */
    protected List<Answer> overlappingTicks(Tick tick) {
        Integer mark = AppLog.mark();
        Answer first = post(tick.path(), "");
        Answer second = post(tick.path(), "");
        awaitFinished(tick, mark, 2);
        return List.of(first, second);
    }

    private void awaitFinished(Tick tick, Integer mark, Integer times) {
        Awaitility.await().atMost(TICK_TIMEOUT).pollInterval(Duration.ofMillis(50))
                .until(countOf(tick, mark), count -> count >= times);
    }

    private Callable<Integer> countOf(Tick tick, Integer mark) {
        return () -> {
            String written = AppLog.since(mark);
            int count = 0;
            int at = written.indexOf(tick.finishedMark());
            while (at >= 0) {
                count++;
                at = written.indexOf(tick.finishedMark(), at + 1);
            }
            return count;
        };
    }

    /**
     * Ждёт, пока журнал приложения после отметки не понесёт названный
     * след: им наблюдаются исходы асинхронных ходов, у которых нет ни
     * поверхности, ни строки.
     *
     * @param mark отметка журнала, снятая до хода
     * @param mark2 след, которого ждём
     */
    protected void awaitLog(Integer mark, String mark2) {
        Awaitility.await().atMost(TICK_TIMEOUT).pollInterval(Duration.ofMillis(50))
                .until(() -> AppLog.since(mark).contains(mark2));
    }

    /** Чтение под сервисным токеном. */
    protected Answer get(String path) {
        return send(authorized(request(path)).GET());
    }

    /** Чтение без предъявленного принципала. */
    protected Answer getAnonymously(String path) {
        return send(request(path).GET());
    }

    /** Чтение под названным токеном. */
    protected Answer getWith(String path, String token) {
        return send(request(path).header("Authorization", "Bearer " + token).GET());
    }

    /**
     * Чтение под сервисным токеном с НАЗВАННЫМ заголовком сверх него.
     *
     * <p>Им подаётся контекст тенанта: заголовок есть отдельный операнд
     * вызова, и утверждение «его не принимают» проверяется только подачей
     * (docs/architecture/contracts.md §«Контекст тенанта в вызове»).
     *
     * @param path  путь поверхности
     * @param name  имя заголовка
     * @param value значение заголовка
     * @return ответ поверхности
     */
    protected Answer getWithHeader(String path, String name, String value) {
        return send(authorized(request(path)).header(name, value).GET());
    }

    /** Мутирующий вызов под сервисным токеном. */
    protected Answer post(String path, String body) {
        return send(authorized(request(path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)));
    }

    /** Мутирующий вызов без предъявленного принципала. */
    protected Answer postAnonymously(String path, String body) {
        return send(request(path)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)));
    }

    /** Назначение снимком намерения под сервисным токеном. */
    protected Answer put(String path, String body) {
        return send(authorized(request(path))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body)));
    }

    /** Вызов неподдержанным методом под сервисным токеном. */
    protected Answer delete(String path) {
        return send(authorized(request(path)).DELETE());
    }

    private HttpRequest.Builder request(String path) {
        return request(port, path);
    }

    private static HttpRequest.Builder request(Integer processPort, String path) {
        return HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + processPort + path))
                .timeout(Duration.ofSeconds(60));
    }

    private HttpRequest.Builder authorized(HttpRequest.Builder request) {
        return request.header("Authorization", "Bearer " + identity.serviceToken());
    }

    private static Answer send(HttpRequest.Builder request) {
        try {
            HttpResponse<String> answer = CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofString());
            return new Answer(answer.statusCode(), answer.body());
        } catch (IOException failure) {
            throw new IllegalStateException("Поверхность ящика не ответила", failure);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Ожидание ответа поверхности прервано", failure);
        }
    }

    /**
     * Ответ поверхности: код и тело дословно.
     *
     * <p><b>Запись, а не разобранный объект:</b> часть кейсов утверждает о
     * ТЕЛЕ — что в нём нет числового ключа базы, нет значения исходящего
     * токена, нет сырого статуса площадки, — и разбор в типизованную форму
     * такие ожидания выразить не даёт.
     *
     * @param status код ответа
     * @param body   тело ответа дословно
     */
    protected record Answer(Integer status, String body) {

        /** Тело как объект. */
        Map<String, Object> asObject() {
            return JsonParserFactory.getJsonParser().parseMap(body);
        }

        /** Тело как перечень объектов. */
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> asList() {
            return JsonParserFactory.getJsonParser().parseList(body).stream()
                    .map(item -> (Map<String, Object>) item)
                    .toList();
        }

        /** Единственный объект перечня. */
        Map<String, Object> single() {
            List<Map<String, Object>> items = asList();
            if (items.size() != 1) {
                throw new AssertionError("Ожидался ровно один объект, пришло " + items.size() + ": " + body);
            }
            return items.getFirst();
        }

        /** Вложенный перечень тела по имени поля; пустой, когда его нет. */
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nestedList(String field) {
            Object value = asObject().get(field);
            return Objects.isNull(value) ? List.of() : (List<Map<String, Object>>) value;
        }

        /**
         * Несёт ли тело единый error-DTO поверхности: класс отказа и
         * момент.
         *
         * <p>Форма читается по ПОЛЯМ, а не по коду ответа: контейнерный
         * отказ отдаёт своё тело с тем же кодом.
         */
        Boolean carriesErrorDto() {
            if (Objects.isNull(body) || body.isBlank()) {
                return Boolean.FALSE;
            }
            // Тело, которое объектом не является (перечень моделей, текст
            // контейнера), формы не несёт — и это ОТВЕТ клетки, а не её
            // отказ: иначе красная клетка падала бы разбором и переставала
            // называть, чем именно ожидание не сошлось.
            try {
                Map<String, Object> parsed = asObject();
                return parsed.containsKey("code") && parsed.containsKey("occurredAt");
            } catch (RuntimeException notAnObject) {
                return Boolean.FALSE;
            }
        }

        /** Класс отказа единого error-DTO. */
        String errorCode() {
            return String.valueOf(asObject().get("code"));
        }
    }
}
