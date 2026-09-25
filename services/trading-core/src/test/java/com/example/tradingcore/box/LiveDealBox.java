package com.example.tradingcore.box;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.aggregate.strategy.Strategy;
import com.example.tradingbot.domain.model.trade.market_phase.MarketPhase;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import org.awaitility.Awaitility;

/**
 * Базовая сборка ЖИВОЙ СДЕЛКИ: вход, налитый целиком, живой эпизод позиции
 * и встроенная защита, развёрнутая источником в самостоятельную заявку, —
 * общая для групп, чьи клетки стоят на живой экспозиции транша
 * (.claude/tests/cases/trading-core.md §«Кейсы, не прогоняемые сегодня»).
 *
 * <p><b>Тропа строится ЦЕЛИКОМ тропами ящика</b>, как у отправки входа:
 * проекции — тиком синка, ставка комиссии — тиком её синка, числа
 * риск-аппетита и плечо — своей поверхностью, сделка — тиком отбора
 * входа, создание и отправка ноги — проходами сопровождения. Налив
 * наблюдается ТОЙ ЖЕ тропой, какой он наблюдается в проде: обработчик
 * отправленного входа отдаёт добычу ноги вместе с позицией, а стаб
 * коннектора отвечает налитой ногой, живым эпизодом и материализованной
 * защитой (docs/components/TrancheEntrySubmittedHandler.md §«Налив
 * наблюдается добычей»). Прямой записи в сборке нет ни одной.
 *
 * <p><b>Базовая сборка общая, и потому живёт своим классом</b>, а не
 * копией в каждой группе (.claude/skills/test-code.md §«Уровень 2 — юниты
 * библиотеки или модуля» — то же требование к харнесу): клетки трёх групп
 * расходятся только тем, что делают с живой сделкой.
 *
 * <p><b>Свойств контекста класс не объявляет, и это несущее.</b> Метод
 * {@code @DynamicPropertySource} есть часть ключа кэша контекста, а два
 * метода на одной цепочке наследования регистрировали бы одни ключи
 * дважды — в порядке, которого контракт реестра не обещает. Поэтому
 * штатное положение осей у групп живой сделки даёт
 * {@link SharedLiveDealBox}, а класс со своим положением осей наследует
 * сборку отсюда и объявляет свой метод сам.
 */
abstract class LiveDealBox extends TradingCoreBox {

    /** Основа идентичности определения живой сделки. */
    protected static final String DEFINITION = "S-LIVE";

    /** Биржевой момент, который отдаёт коннектор. */
    protected static final String EXCHANGE_MOMENT = "2026-09-20T10:00:00Z";

    /** Последняя цена момента: от неё считаются и уровень, и размер. */
    protected static final String LAST_PRICE = "100";

    /** Биржевой момент открытия эпизода: адрес эпизода вместе с идентификатором. */
    protected static final String POSITION_CREATED_AT = "2026-09-20T10:00:03Z";

    /** Биржевой момент закрытия эпизода. */
    protected static final String CLOSED_AT = "2026-09-20T11:00:00Z";

    /** Биржевой момент после закрытия: им коннектор отвечает на выходе. */
    protected static final String AFTER_CLOSE = "2026-09-20T12:00:00Z";

    /** Сценарий стаба: позиция сделки у площадки. */
    private static final String POSITION_SCENARIO = "position";

    /** Сценарий стаба: материализованная встроенная защита у площадки. */
    private static final String PROTECTION_SCENARIO = "protection";

    /** Сценарий стаба: отдельные условные заявки сделки у площадки. */
    private static final String ALGO_SCENARIO = "algo";

    /** Сценарий стаба: входная нога сделки у площадки. */
    private static final String ENTRY_SCENARIO = "entry";

    /** Состояние сценария позиции после исполненного закрытия. */
    private static final String CLOSED = "closed";

    /** Состояние сценария защиты после исполненного снятия. */
    private static final String CANCELED = "canceled";

    /** Жёсткая ступень биржевого счёта. */
    protected static final String TRADE_BLOCKED = "TRADE_BLOCKED";

    /** Отсутствие ступени: рабочее состояние объекта. */
    protected static final String NO_RUNG = "ACTIVE";

    /**
     * Потолок ожидания статуса: проход дробит работу по звену за раз, и
     * число проходов до статуса — свойство тропы, а не клетки.
     */
    private static final Duration PASS_HORIZON = Duration.ofSeconds(90);

    /**
     * Предел серии убыточных закрытий, которым сборка назначает числа
     * риск-аппетита; клетка о серии ставит свой до открытия сделки.
     */
    protected String consecutiveLossLimit = "4";

    /**
     * Сделка с траншем в предвходовой проверке, чей вход доходит до
     * команды: все входы расчёта и преконтроля поставлены тропами ящика, а
     * снимок средств снят первым тиком сопровождения.
     *
     * @param definition определение, которым сделка заводится
     */
    protected void openCommandDeal(Strategy definition) {
        provision(List.of(ACCOUNT), Map.of(INSTRUMENT, EXTERNAL_INSTRUMENT));
        assignRiskAppetite();
        assignLeverage(ACCOUNT, INSTRUMENT);
        syncFeeRate();
        marketData.answers(featuresPath(INSTRUMENT),
                Feed.featuresWithPrice(MarketPhase.Type.BULL_TREND.name(), LAST_PRICE));
        connector.answers(balancePath(ACCOUNT), balanceBody());
        connector.answers(PEER_SERVER_TIME, Feed.serverTime(EXCHANGE_MOMENT));
        Long dealsBefore = rows.count("deals");
        activate(definition);
        tick(Tick.ENTRY_SCANNER);
        assertThat(rows.count("deals")).isEqualTo(dealsBefore + 1);
        // Первый тик сопровождения снимает снимок средств: предвходовая
        // проверка обеспечивает его ДО работы. У первой сделки клетки
        // работы на этом тике нет; у следующих снимок счёта уже свеж, и тот
        // же тик заводит ногу — поэтому ногу ждёт submitEntry, а не этот
        // шаг.
        tick(Tick.DEAL_ORCHESTRATOR);
        PeerStub.all().forEach(PeerStub::forgetRequests);
    }

    /** Штатное определение живой сделки: вход по фазе со встроенным стопом. */
    protected Strategy workingDefinition() {
        return Definitions.withEntryCommandOnPhase(DEFINITION, ACCOUNT, INSTRUMENT, MarketPhase.Type.BULL_TREND);
    }

    /**
     * Доводит ногу входа до ОТПРАВЛЕННОЙ: создание, затем подтверждённое
     * площадкой размещение — обоими проходами тропы ящика.
     */
    protected void submitEntry(Strategy definition) {
        openCommandDeal(definition);
        if (ordersOfDeal().isEmpty()) {
            tick(Tick.DEAL_ORCHESTRATOR);
        }
        assertThat(ordersOfDeal()).hasSize(1);
        connector.answers(placementPath(ACCOUNT), Feed.ack(entryExternalId(), entryClientId()));
        tick(Tick.DEAL_ORCHESTRATOR);
        assertThat(entryStatus()).isEqualTo("PENDING");
        PeerStub.all().forEach(PeerStub::forgetRequests);
    }

    /**
     * Живая сделка: вход налит целиком, эпизод позиции жив, встроенная
     * защита материализована — транш в сопровождении, сделка активна.
     *
     * <p>Статус сопровождения и есть наблюдаемое предусловие: его
     * обработчик пускает транш дальше, только когда покрытие сошлось и
     * живой эпизод есть (docs/components/TrancheEntryFinalizedHandler.md).
     */
    protected void openLiveDeal() {
        openLiveDeal(workingDefinition());
    }

    /** Та же живая сделка по названному определению. */
    protected void openLiveDeal(Strategy definition) {
        openLiveDeal(definition, () -> {
        });
    }

    /**
     * Та же живая сделка, у которой между отправкой входа и его наливом
     * ставится названное состояние.
     *
     * <p><b>Окно выбрано не произвольно.</b> Состояние, запрещающее вход
     * (ступень пары, ступень счёта), нельзя поставить раньше: отбор входа
     * сделку не завёл бы, а преконтроль отверг бы ногу. Позже тоже нельзя:
     * шаги сопровождения исполняются первым же проходом после налива, и
     * клетка наблюдала бы их исход в рабочем состоянии, а не под ступенью.
     *
     * @param definition определение, которым сделка заводится
     * @param beforeFill что ставится у отправленного, но не налитого входа
     */
    protected void openLiveDeal(Strategy definition, Runnable beforeFill) {
        submitEntry(definition);
        beforeFill.run();
        standFilledEntry();
        passesUntil(() -> Objects.equals("MANAGING", trancheStatus()));
        assertThat(dealStatus()).isEqualTo("ACTIVE");
        PeerStub.all().forEach(PeerStub::forgetRequests);
    }

    /**
     * Ставит у стаба коннектора факты налитого входа: ногу, живой эпизод
     * позиции её размером и материализованную защиту.
     */
    protected void standFilledEntry() {
        String size = entrySize();
        connector.answers(lookupPath(ACCOUNT),
                Feed.filledOrder(entryExternalId(), entryClientId(), size, LAST_PRICE));
        connector.answers(positionPath(ACCOUNT), livePositionOf(size));
        connector.answers(pendingProtectionsPath(ACCOUNT), Feed.array(Feed.materializedProtection(
                protectionClientId(), protectionExternalId(), size, protectionTrigger())));
    }

    /**
     * Живая сделка с ЧАСТИЧНО налитым входом: нога жива и налита
     * половиной, эпизод позиции открыт её наливом, встроенная защита
     * материализована — транш в отправленном входе, сделка активна.
     *
     * <p><b>Это предусловие «живая входная нога при ненулевой
     * экспозиции»</b> — вход клеток о порядке снятия риска: у налитого
     * целиком входа живой ноги нет, и порядок «нога раньше экспозиции»
     * наблюдать нечем.
     *
     * <p><b>Отмена ноги переключает площадку</b> ({@link PeerStub#flipsOn}):
     * после команды нога отдаётся снятой с тем же наливом, до неё — живой.
     * Позицию и защиту по исходу команд ставит
     * {@link #standExchangeFollowingCommands(String, String, String)} размером
     * налива.
     *
     * <p><b>Транш не уходит в сопровождение, и это проверяется:</b> вход
     * подтверждённым объявляется только полным наливом
     * (docs/components/TrancheEntrySubmittedHandler.md), а частичный
     * наблюдается добычей каждым проходом.
     */
    protected void openPartiallyFilledDeal() {
        submitEntry(workingDefinition());
        String filled = partialFill();
        connector.flipsOn(ENTRY_SCENARIO, cancellationPath(ACCOUNT), Feed.ack(entryExternalId(), entryClientId()),
                CANCELED);
        connector.answersInState(ENTRY_SCENARIO, lookupPath(ACCOUNT), PeerStub.INITIAL,
                partiallyFilledEntry("PARTIALLY_COMPLETED", filled));
        connector.answersInState(ENTRY_SCENARIO, lookupPath(ACCOUNT), CANCELED,
                partiallyFilledEntry("CANCELED", filled));
        connector.answers(positionPath(ACCOUNT), livePositionOf(filled));
        passesUntil(() -> Objects.equals("PARTIALLY_COMPLETED", entryStatus())
                && Objects.equals("ACTIVE", String.valueOf(protectionRow().get("status")))
                && rows.count("positions") == 1L);
        assertThat(trancheStatus()).isEqualTo("ENTRY_SUBMITTED");
        assertThat(dealStatus()).isEqualTo("ACTIVE");
        assertThat(new BigDecimal(String.valueOf(rows.all("positions").getFirst().get("external_size"))))
                .isEqualByComparingTo(filled);
        PeerStub.all().forEach(PeerStub::forgetRequests);
    }

    /** Налив частично налитого входа: половина размера ноги. */
    protected String partialFill() {
        return new BigDecimal(entrySize()).divide(BigDecimal.valueOf(2)).toPlainString();
    }

    /** Входная нога, налитая частью, в названном статусе. */
    private String partiallyFilledEntry(String status, String filled) {
        return Feed.partiallyFilledOrder(entryExternalId(), entryClientId(), status, entrySize(), filled,
                protectionClientId(), protectionTrigger());
    }

    /**
     * Живая сделка на ДВУХ траншах: вход каждого налит целиком, эпизод
     * позиции один — их суммой, встроенные защиты обоих материализованы;
     * оба транша в сопровождении.
     *
     * <p><b>Уровней объявлено два, и отправляются они одним проходом</b>
     * (материализация сетки эагерна): площадка принимает обе команды по
     * очереди и отвечает каждой своим биржевым идентификатором, а добыча
     * различает ноги по нему.
     */
    protected void openTwoTrancheLiveDeal() {
        openTwoTrancheLiveDeal(Definitions.withEntryCommandLevelsOnPhase(DEFINITION, ACCOUNT, INSTRUMENT,
                MarketPhase.Type.BULL_TREND, 2));
    }

    /** Та же сделка на двух траншах по названному определению. */
    protected void openTwoTrancheLiveDeal(Strategy definition) {
        submitTwoEntries(definition);
        standTwoFilledEntries();
        passesUntil(() -> tranchesOfDeal().stream().allMatch(row -> Objects.equals("MANAGING", row.get("status"))));
        assertThat(dealStatus()).isEqualTo("ACTIVE");
        PeerStub.all().forEach(PeerStub::forgetRequests);
    }

    /**
     * Ставит у стаба коннектора факты налитых входов двухтраншевой сделки:
     * каждую ногу налитой целиком, живой эпизод их суммой и
     * материализованные защиты обеих.
     */
    protected void standTwoFilledEntries() {
        List<Map<String, Object>> legs = ordersOfDeal();
        for (int index = 0; index < legs.size(); index++) {
            connector.answersWhen(lookupPath(ACCOUNT), "externalId", legExternalId(index),
                    Feed.filledOrder(legExternalId(index), String.valueOf(legs.get(index).get("internal_id")),
                            sizeOf(legs.get(index)), LAST_PRICE));
        }
        connector.answers(positionPath(ACCOUNT), livePositionOf(dealEntrySize()));
        connector.answers(pendingProtectionsPath(ACCOUNT), Feed.array(protectionRecords().toArray(String[]::new)));
    }

    /**
     * Доводит ОБЕ ноги входа двухтраншевой сделки до отправленных: создание,
     * затем подтверждённое площадкой размещение каждой.
     */
    protected void submitTwoEntries(Strategy definition) {
        openCommandDeal(definition);
        if (ordersOfDeal().isEmpty()) {
            tick(Tick.DEAL_ORCHESTRATOR);
        }
        List<Map<String, Object>> legs = ordersOfDeal();
        assertThat(legs).hasSize(2);
        connector.answersInTurn(placementPath(ACCOUNT),
                Feed.ack(legExternalId(0), String.valueOf(legs.get(0).get("internal_id"))),
                Feed.ack(legExternalId(1), String.valueOf(legs.get(1).get("internal_id"))));
        connector.answers(lookupPath(ACCOUNT), Feed.absent());
        tick(Tick.DEAL_ORCHESTRATOR);
        assertThat(ordersOfDeal()).allMatch(row -> Objects.equals("PENDING", row.get("status")));
        PeerStub.all().forEach(PeerStub::forgetRequests);
    }

    /** Биржевой идентификатор ноги входа по её порядку в сделке. */
    protected String legExternalId(Integer index) {
        return index == 0 ? entryExternalId() : entryExternalId() + "-" + index;
    }

    /** Транши последней сделки в порядке заведения. */
    protected List<Map<String, Object>> tranchesOfDeal() {
        return rows.select("select * from deal_tranches where deal_id = (select max(id) from deals) order by id");
    }

    /** Суммарный размер ног входа последней сделки — им открыт эпизод. */
    protected String dealEntrySize() {
        return ordersOfDeal().stream()
                .filter(row -> Objects.equals(Boolean.FALSE, row.get("position_reducing_only")))
                .map(row -> new BigDecimal(sizeOf(row)))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .toPlainString();
    }

    /**
     * Материализованные записи встроенных защит всех ног входа последней
     * сделки: клиентский идентификатор и уровень — те, что объявило ядро.
     */
    protected List<String> protectionRecords() {
        List<Map<String, Object>> protections = rows.select("select a.* from attached_algo_orders a"
                + " join orders o on o.id = a.order_id where o.deal_id = (select max(id) from deals) order by a.id");
        List<String> records = new ArrayList<>();
        for (int index = 0; index < protections.size(); index++) {
            Map<String, Object> protection = protections.get(index);
            records.add(Feed.materializedProtection(String.valueOf(protection.get("internal_id")),
                    index == 0 ? protectionExternalId() : protectionExternalId() + "-" + index,
                    new BigDecimal(String.valueOf(protection.get("size"))).toPlainString(),
                    new BigDecimal(String.valueOf(protection.get("stop_loss_trigger_price"))).toPlainString()));
        }
        return records;
    }

    /** Размер строки заявки в контрактах, без хвостовых нулей шкалы. */
    private static String sizeOf(Map<String, Object> order) {
        return new BigDecimal(String.valueOf(order.get("size"))).toPlainString();
    }

    /**
     * Площадка, отвечающая по ИСХОДУ команд: закрытие позиции снимает
     * эпизод и отдаёт запись его закрытия, снятие защиты убирает её из
     * живых и кладёт в историю снятых.
     *
     * <p><b>Чтения переключает команда, а не их счёт</b>
     * ({@link PeerStub#flipsOn}): сколько раз проход спросит позицию до
     * закрытия — свойство нарезки прохода, и клетка, его пиньнувшая, мерила
     * бы нарезку, а не предмет.
     *
     * @param realizedProfit реализованный результат закрытого эпизода — он
     *                       же сумма движения его закрытия
     */
    protected void standExchangeFollowingCommands(String realizedProfit) {
        standExchangeFollowingCommands(realizedProfit, realizedProfit);
    }

    /**
     * Та же площадка, у которой запись закрытия и движение закрытия
     * расходятся: вход клеток о сверке результата.
     *
     * @param recordProfit реализованный результат в записи закрытия эпизода
     * @param billAmount   сумма движения закрытия
     */
    protected void standExchangeFollowingCommands(String recordProfit, String billAmount) {
        standExchangeFollowingCommands(recordProfit, billAmount, dealEntrySize());
    }

    /**
     * Та же площадка, у которой живой эпизод названного размера: вход клеток
     * о частично налитом входе, где эпизод открыт наливом, а не размером
     * ноги.
     *
     * @param recordProfit реализованный результат в записи закрытия эпизода
     * @param billAmount   сумма движения закрытия
     * @param size         размер живого эпизода в контрактах
     */
    protected void standExchangeFollowingCommands(String recordProfit, String billAmount, String size) {
        String[] protections = protectionRecords().toArray(String[]::new);
        String closeId = "ex-close-" + dealOrdinal();
        connector.resetScenarios();
        connector.flipsOn(POSITION_SCENARIO, closurePath(ACCOUNT), Feed.ack(closeId, "close-" + dealOrdinal()),
                CLOSED);
        connector.answersInState(POSITION_SCENARIO, positionPath(ACCOUNT), PeerStub.INITIAL, livePositionOf(size));
        connector.answersInState(POSITION_SCENARIO, positionPath(ACCOUNT), CLOSED, Feed.absent());
        connector.answersInState(POSITION_SCENARIO, closedPositionsPath(ACCOUNT), PeerStub.INITIAL,
                Feed.emptyArray());
        connector.answersInState(POSITION_SCENARIO, closedPositionsPath(ACCOUNT), CLOSED, Feed.array(
                Feed.closedPosition(positionExternalId(), POSITION_CREATED_AT, CLOSED_AT, recordProfit)));
        connector.answersInState(POSITION_SCENARIO, positionsPath(ACCOUNT), PeerStub.INITIAL,
                Feed.array(livePositionOf(size)));
        connector.answersInState(POSITION_SCENARIO, positionsPath(ACCOUNT), CLOSED, Feed.emptyArray());
        connector.answers(billsPath(ACCOUNT), Feed.array(Feed.bill(closeBillId(), "2", null, billAmount,
                closeId, CLOSED_AT)));
        // Биржевой момент ПОСЛЕ закрытия: окно движений кончается им, и
        // движение закрытия в него попадает.
        connector.answers(PEER_SERVER_TIME, Feed.serverTime(AFTER_CLOSE));
        connector.flipsOn(PROTECTION_SCENARIO, attachedCancellationPath(ACCOUNT),
                Feed.ack(protectionExternalId(), protectionClientId()), CANCELED);
        connector.answersInState(PROTECTION_SCENARIO, pendingProtectionsPath(ACCOUNT), PeerStub.INITIAL,
                Feed.array(protections));
        connector.answersInState(PROTECTION_SCENARIO, pendingProtectionsPath(ACCOUNT), CANCELED, Feed.emptyArray());
        connector.answers(protectionHistoryPath(ACCOUNT), Feed.emptyArray());
        connector.answersInStateWhen(PROTECTION_SCENARIO, protectionHistoryPath(ACCOUNT), CANCELED, "leg",
                "CANCELED", Feed.array(protections));
        connector.answers(pendingOrdersPath(ACCOUNT), Feed.emptyArray());
        connector.answers(pendingAlgoOrdersPath(ACCOUNT), Feed.emptyArray());
        connector.answers(pendingPath(ACCOUNT), Feed.emptyArray());
    }

    /**
     * Площадка для ОТДЕЛЬНЫХ условных заявок сделки: каждое размещение
     * принимается своим биржевым идентификатором по очереди, поиск отвечает
     * живой заявкой, а снятие переключает её в снятую — тем же приёмом,
     * что у позиции и защиты ({@link PeerStub#flipsOn}).
     *
     * <p><b>Сценарий снятия один на все заявки сделки, и это названо:</b>
     * снятие разводилось бы по телу команды, а клетки, стоящие на этой
     * площадке, снимают не больше одной отдельной заявки.
     *
     * @param count сколько размещений клетка ждёт
     */
    protected void standAlgoOrdersFollowingCommands(Integer count) {
        String[] acks = new String[count];
        for (int index = 0; index < count; index++) {
            acks[index] = Feed.ack(algoExternalId(index), "algo-" + index);
        }
        connector.answersInTurn(algoPlacementPath(ACCOUNT), acks);
        connector.flipsOn(ALGO_SCENARIO, algoCancellationPath(ACCOUNT), Feed.ack(algoExternalId(0), "algo-0"),
                CANCELED);
        connector.answersInState(ALGO_SCENARIO, algoLookupPath(ACCOUNT), PeerStub.INITIAL,
                Feed.algoOrderInStatus("ACTIVE"));
        connector.answersInState(ALGO_SCENARIO, algoLookupPath(ACCOUNT), CANCELED,
                Feed.algoOrderInStatus("CANCELED"));
    }

    /** Биржевой идентификатор отдельной условной заявки последней сделки по её порядку. */
    protected String algoExternalId(Integer index) {
        return "ex-algo-" + dealOrdinal() + "-" + index;
    }

    /** Отдельные условные заявки последней сделки в порядке заведения. */
    protected List<Map<String, Object>> algoOrdersOfDeal() {
        return rows.select("select * from algo_orders where deal_id = (select max(id) from deals) order by id");
    }

    /**
     * Живая сделка, закрытая координированным выходом с названным
     * результатом: открытие, площадка по исходу команд, удаление
     * определения, проходы до терминала.
     *
     * <p><b>Идентичность определения своя у каждой сделки серии:</b>
     * удалённое определение нового входа не даёт, и следующая сделка той
     * же клетки заводится своим.
     *
     * @param definitionId   идентичность определения этой сделки
     * @param realizedProfit реализованный результат её эпизода
     */
    protected void closeLiveDealWith(String definitionId, String realizedProfit) {
        closeLiveDealWith(definitionId, realizedProfit, realizedProfit);
    }

    /**
     * Та же сделка, у которой запись закрытия и движение закрытия
     * расходятся: вход клеток о сверке результата.
     *
     * @param definitionId идентичность определения этой сделки
     * @param recordProfit реализованный результат в записи закрытия
     * @param billAmount   сумма движения закрытия
     */
    protected void closeLiveDealWith(String definitionId, String recordProfit, String billAmount) {
        Strategy definition = Definitions.withEntryCommandOnPhase(definitionId, ACCOUNT, INSTRUMENT,
                MarketPhase.Type.BULL_TREND);
        openLiveDeal(definition);
        standExchangeFollowingCommands(recordProfit, billAmount);
        exitByDeletion(definition);
        passesUntilDealTerminal();
        PeerStub.all().forEach(PeerStub::forgetRequests);
    }

    /**
     * Уводит живую сделку в координированный выход удалением её
     * определения — той же тропой, что кейс {@code B2.9}.
     *
     * @param definition определение, которым сделка заведена
     */
    protected void exitByDeletion(Strategy definition) {
        moveDefinition(STRATEGY_DELETED, definition, Strategy.Status.DELETED.name());
    }

    /** Тикает проход сопровождения, пока сделка не встанет в терминал. */
    protected void passesUntilDealTerminal() {
        passesUntil(() -> List.of("CLOSED", "EMERGENCY_CLOSED").contains(dealStatus()));
    }

    /** Живой эпизод позиции названным размером. */
    protected String livePositionOf(String size) {
        return Feed.livePosition(positionExternalId(), EXTERNAL_INSTRUMENT, size, LAST_PRICE,
                POSITION_CREATED_AT);
    }

    /**
     * Порядковый номер последней сделки клетки: им разводятся биржевые
     * идентификаторы сделок одной серии — у площадки они не повторяются.
     */
    protected Long dealOrdinal() {
        return rows.count("deals");
    }

    /** Биржевой идентификатор входной ноги последней сделки. */
    protected String entryExternalId() {
        return "ex-" + dealOrdinal();
    }

    /** Биржевой идентификатор материализованной встроенной защиты последней сделки. */
    protected String protectionExternalId() {
        return "ex-sl-" + dealOrdinal();
    }

    /** Биржевой идентификатор живого эпизода последней сделки. */
    protected String positionExternalId() {
        return "pos-" + dealOrdinal();
    }

    /** Биржевой идентификатор движения закрытия последней сделки. */
    protected String closeBillId() {
        return "bill-close-" + dealOrdinal();
    }

    /**
     * Тикает проход сопровождения до наступления названного состояния.
     *
     * <p><b>Число тиков не пиньнуто намеренно:</b> проход дробит работу по
     * звену за раз, а ребро транша едет проходом позже своей команды
     * (.claude/skills/test-code.md, ловушка TC-182) — пиньнутое число мерило
     * бы нарезку прохода, а не предмет клетки.
     *
     * @param reached условие, по достижении которого тики прекращаются
     */
    protected void passesUntil(Callable<Boolean> reached) {
        Awaitility.await().atMost(PASS_HORIZON).pollInterval(Duration.ofMillis(200))
                .until(() -> {
                    tick(Tick.DEAL_ORCHESTRATOR);
                    return reached.call();
                });
    }

    /** Числа риск-аппетита тенанта: операнды преконтроля, своей поверхностью. */
    protected void assignRiskAppetite() {
        assertThat(put(RISK_APPETITES + "/" + TENANT, Bodies.riskAppetite("5", "10", consecutiveLossLimit))
                .status()).isEqualTo(200);
    }

    /** Ставка комиссии комиссионного уровня счёта: тиком её синка. */
    protected void syncFeeRate() {
        connector.answers(feeRatePath(ACCOUNT), Feed.array(Feed.tradeFeeRate()));
        tick(Tick.TRADE_FEE_RATES);
        assertThat(rows.count("trade_fee_rates")).isEqualTo(1L);
    }

    /** Путь чтения связки фич момента у владельца рыночных данных. */
    protected String featuresPath(String instrumentInternalId) {
        return PEER_INSTRUMENTS + "/" + instrumentInternalId + "/features";
    }

    /** Корень путей счёта у коннектора. */
    protected String accountPath(String accountInternalId) {
        return "/api/v1/accounts/" + accountInternalId;
    }

    /** Путь чтения снимка средств у коннектора. */
    protected String balancePath(String accountInternalId) {
        return accountPath(accountInternalId) + "/balance";
    }

    /** Путь приватного чтения ставок комиссии у коннектора. */
    protected String feeRatePath(String accountInternalId) {
        return accountPath(accountInternalId) + "/trade-fee-rates";
    }

    /** Путь размещения обычной заявки у коннектора. */
    protected String placementPath(String accountInternalId) {
        return accountPath(accountInternalId) + "/orders";
    }

    /** Путь размещения условной заявки у коннектора. */
    protected String algoPlacementPath(String accountInternalId) {
        return accountPath(accountInternalId) + "/algo-orders";
    }

    /** Путь отмены обычной заявки у коннектора. */
    protected String cancellationPath(String accountInternalId) {
        return accountPath(accountInternalId) + "/orders/cancellations";
    }

    /** Путь отмены условной заявки у коннектора. */
    protected String algoCancellationPath(String accountInternalId) {
        return accountPath(accountInternalId) + "/algo-orders/cancellations";
    }

    /** Путь поиска условной заявки у коннектора: первая нога её добычи. */
    protected String algoLookupPath(String accountInternalId) {
        return accountPath(accountInternalId) + "/algo-orders/lookup";
    }

    /** Путь снятия встроенной защиты у коннектора. */
    protected String attachedCancellationPath(String accountInternalId) {
        return accountPath(accountInternalId) + "/attached-protections/cancellations";
    }

    /** Путь закрытия позиции у коннектора. */
    protected String closurePath(String accountInternalId) {
        return accountPath(accountInternalId) + "/positions/closures";
    }

    /** Первая нога лестницы добычи: поиск ноги по идентификатору. */
    protected String lookupPath(String accountInternalId) {
        return accountPath(accountInternalId) + "/orders/lookup";
    }

    /** Вторая нога: живые заявки инструмента. */
    protected String pendingPath(String accountInternalId) {
        return accountPath(accountInternalId) + "/orders/pending/instrument";
    }

    /** Третья нога: история заявок инструмента. */
    protected String historyPath(String accountInternalId) {
        return accountPath(accountInternalId) + "/orders/history";
    }

    /** Живые материализованные встроенные защиты инструмента. */
    protected String pendingProtectionsPath(String accountInternalId) {
        return accountPath(accountInternalId) + "/attached-protections/pending";
    }

    /** История материализованных встроенных защит инструмента. */
    protected String protectionHistoryPath(String accountInternalId) {
        return accountPath(accountInternalId) + "/attached-protections/history";
    }

    /** Живой эпизод позиции инструмента. */
    protected String positionPath(String accountInternalId) {
        return accountPath(accountInternalId) + "/positions/instrument";
    }

    /** Записи закрытия эпизодов позиции инструмента. */
    protected String closedPositionsPath(String accountInternalId) {
        return accountPath(accountInternalId) + "/positions/closed";
    }

    /** Движения средств счёта окном. */
    protected String billsPath(String accountInternalId) {
        return accountPath(accountInternalId) + "/bills";
    }

    /** Живые заявки счёта целиком: первый срез проактивной детекции. */
    protected String pendingOrdersPath(String accountInternalId) {
        return accountPath(accountInternalId) + "/orders/pending";
    }

    /** Живые отдельные условные заявки счёта целиком: второй срез. */
    protected String pendingAlgoOrdersPath(String accountInternalId) {
        return accountPath(accountInternalId) + "/algo-orders/pending";
    }

    /** Живые позиции счёта целиком: третий срез. */
    protected String positionsPath(String accountInternalId) {
        return accountPath(accountInternalId) + "/positions";
    }

    /**
     * Отодвигает назад момент СТОЯЩИХ наблюдательных строк, чтобы
     * следующий тик детекции читал их подтверждением признака.
     *
     * <p><b>Строк она не заводит и содержания их не трогает:</b> предмет
     * правки — только возраст строки, которую завёл сам сервис; довод —
     * шапка {@link ProactiveDetectionBoxTest}.
     */
    protected void ageObservations() {
        rows.put("update anomaly_reports set created_at = created_at - interval '2 minutes'");
    }

    /** Заявки последней сделки клетки в порядке заведения. */
    protected List<Map<String, Object>> ordersOfDeal() {
        return rows.select("select * from orders where deal_id = (select max(id) from deals) order by id");
    }

    /** Входная нога последней сделки — первая её заведённая заявка. */
    protected Map<String, Object> entryRow() {
        return ordersOfDeal().getFirst();
    }

    /** Клиентский идентификатор входной ноги. */
    protected String entryClientId() {
        return String.valueOf(entryRow().get("internal_id"));
    }

    /** Статус входной ноги, как его видит база. */
    protected String entryStatus() {
        return String.valueOf(entryRow().get("status"));
    }

    /** Размер входной ноги в контрактах — им налит вход и открыт эпизод. */
    protected String entrySize() {
        return new BigDecimal(String.valueOf(entryRow().get("size"))).toPlainString();
    }

    /** Строка встроенной защиты входной ноги. */
    protected Map<String, Object> protectionRow() {
        return rows.select("select * from attached_algo_orders where order_id = ? order by id",
                entryRow().get("id")).getFirst();
    }

    /** Клиентский идентификатор встроенной защиты входной ноги. */
    protected String protectionClientId() {
        return String.valueOf(protectionRow().get("internal_id"));
    }

    /** Цена срабатывания встроенной защиты, как её объявило ядро. */
    protected String protectionTrigger() {
        return new BigDecimal(String.valueOf(protectionRow().get("stop_loss_trigger_price")))
                .toPlainString();
    }

    /** Строка первого транша последней сделки. */
    protected Map<String, Object> trancheRow() {
        return rows.select("select * from deal_tranches where deal_id = (select max(id) from deals)"
                + " order by id").getFirst();
    }

    /** Статус первого транша последней сделки. */
    protected String trancheStatus() {
        return String.valueOf(trancheRow().get("status"));
    }

    /** Строка последней сделки клетки. */
    protected Map<String, Object> dealRow() {
        return rows.allOrderedBy("deals", "id").getLast();
    }

    /** Статус последней сделки клетки. */
    protected String dealStatus() {
        return String.valueOf(dealRow().get("status"));
    }

    /** Строка биржевого счёта, как её видит база. */
    protected Map<String, Object> accountRow() {
        return rows.row("exchange_accounts", "internal_id", ACCOUNT);
    }

    /** Ступень счёта, как её видит база. */
    protected String accountRung() {
        return String.valueOf(accountRow().get("safety_rung"));
    }

    /** Ступень пары «счёт, инструмент»; строки пары нет — рабочее состояние. */
    protected String pairRung(String instrumentInternalId) {
        List<Map<String, Object>> found = rows.select("select * from account_instrument_states"
                        + " where exchange_account_id = ? and instrument_id = ?",
                accountId(ACCOUNT), instrumentId(instrumentInternalId));
        return found.isEmpty() ? NO_RUNG : String.valueOf(found.getFirst().get("safety_rung"));
    }

    /** Сколько строк outbox несёт названный класс. */
    protected Long countEvents(String eventType) {
        return eventTypes().stream().filter(eventType::equals).count();
    }

    /** Коды причин заведённых отчётов аномалий в порядке заведения. */
    protected List<String> codesOfReports() {
        return rows.allOrderedBy("anomaly_reports", "id").stream()
                .map(row -> String.valueOf(row.get("code")))
                .toList();
    }

    /** Классы событий строк outbox в порядке записи. */
    protected List<String> eventTypes() {
        return rows.allOrderedBy("outbox_events", "id").stream()
                .map(row -> String.valueOf(row.get("event_type")))
                .toList();
    }

    /**
     * Обращения к коннектору, МЕНЯЮЩИЕ что-либо на площадке, в порядке
     * ухода: размещения, отмены, снятия защит, закрытия позиции.
     */
    protected List<String> commandCalls() {
        return connector.paths().stream()
                .filter(path -> path.endsWith("/orders") || path.endsWith("/algo-orders")
                        || path.endsWith("/cancellations") || path.endsWith("/closures"))
                .toList();
    }

    /** Снимок средств моментом прогона: возраст ставится В ДАННЫХ. */
    protected String balanceBody() {
        String moment = OffsetDateTime.now(ZoneOffset.UTC).toString();
        return """
                {
                  "externalUpdatedAt": "%s",
                  "externalTotalEquity": "100000",
                  "externalAdjustedEquity": "100000",
                  "externalAvailableEquity": "100000",
                  "balances": [
                    {
                      "externalCurrency": "USDT",
                      "externalUpdatedAt": "%s",
                      "externalEquity": "100000",
                      "externalCashBalance": "100000",
                      "externalAvailableBalance": "100000",
                      "externalFrozenBalance": "0"
                    }
                  ]
                }
                """.formatted(moment, moment);
    }
}
