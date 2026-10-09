package com.example.tradingcore;

import static com.example.platform.util.Constants.Audit.SYSTEM_PRINCIPAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.platform.security.ActorProvider;
import com.example.tradingbot.domain.event.CoreEventType;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.Strategy;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyDetail;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyTradeDirection;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.algo_order.Condition;
import com.example.tradingbot.domain.model.core.algo_order.Trigger;
import com.example.tradingbot.domain.model.core.algo_order.TriggerPrice;
import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingbot.domain.model.core.tenant.Tenant;
import com.example.tradingbot.domain.model.trade.market_phase.MarketPhase;
import com.example.tradingcore.config.AnomalyReportProperties;
import com.example.tradingcore.config.ExchangeContourProperties;
import com.example.tradingcore.config.PnlReconciliationProperties;
import com.example.tradingcore.domain.account.AccountInstrumentState;
import com.example.tradingcore.domain.command.DealActionState;
import com.example.tradingcore.domain.command.DealActionStateStatus;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.ServiceCommand;
import com.example.tradingcore.domain.command.ServiceCommandType;
import com.example.tradingcore.domain.command.SystemActionType;
import com.example.tradingcore.domain.command.TargetEntityType;
import com.example.tradingcore.domain.command.calc.DealReconciliationCalculator;
import com.example.tradingcore.domain.command.calc.DealTerminalFeaturesWriter;
import com.example.tradingcore.domain.command.executor.CreateAlgoOrderExecutor;
import com.example.tradingcore.domain.command.executor.CreateOrderExecutor;
import com.example.tradingcore.domain.command.executor.MarkDealClosedExecutor;
import com.example.tradingcore.domain.command.payload.CreateAlgoOrderCommandPayload;
import com.example.tradingcore.domain.command.payload.CreateOrderCommandPayload;
import com.example.tradingcore.domain.command.risk.DealRiskNumbersService;
import com.example.tradingcore.domain.deal.DealContextService;
import com.example.tradingcore.domain.deal.DealOpeningService;
import com.example.tradingcore.domain.deal.DealStatusEdgeService;
import com.example.tradingcore.domain.deal.DealTerminalGate;
import com.example.tradingcore.domain.safety.AnomalyReport;
import com.example.tradingcore.domain.safety.AnomalyReportService;
import com.example.tradingcore.domain.safety.HoldRung;
import com.example.tradingcore.domain.safety.HoldRungEdgeService;
import com.example.tradingcore.domain.safety.HoldScope;
import com.example.tradingcore.domain.safety.HoldService;
import com.example.tradingcore.domain.safety.HoldSignal;
import com.example.tradingcore.domain.safety.LossStreakCounter;
import com.example.tradingcore.domain.safety.ManualHaltClass;
import com.example.tradingcore.domain.safety.ManualHaltService;
import com.example.tradingcore.domain.safety.PositionSliceReader;
import com.example.tradingcore.domain.safety.SafetyHoldCoordinator;
import com.example.tradingcore.integration.internal.api.exchange.ExchangeOperationsClient;
import com.example.tradingcore.integration.internal.event.CoreEventWriter;
import com.example.tradingcore.mapping.CoreEventMessageMapper;
import com.example.tradingcore.mapping.CoreEventMessageMapperImpl;
import com.example.tradingcore.persistence.model.OutboxEntity;
import com.example.tradingcore.persistence.service.AccountInstrumentStateDataService;
import com.example.tradingcore.persistence.service.AlgoOrderDataService;
import com.example.tradingcore.persistence.service.AnomalyReportDataService;
import com.example.tradingcore.persistence.service.DealActionStateDataService;
import com.example.tradingcore.persistence.service.DealDataService;
import com.example.tradingcore.persistence.service.DealTrancheDataService;
import com.example.tradingcore.persistence.service.ExchangeAccountDataService;
import com.example.tradingcore.persistence.service.InstrumentDataService;
import com.example.tradingcore.persistence.service.OrderDataService;
import com.example.tradingcore.persistence.service.OutboxDataService;
import com.example.tradingcore.persistence.service.StrategyDataService;
import com.example.tradingcore.persistence.service.TenantRiskAppetiteDataService;
import com.example.tradingcore.util.Constants;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Состав того, что ядро кладёт на провод: конверт колонками строки outbox и
 * содержимое её телом.
 *
 * <p><b>Почему охрана — прогон, а не чтение.</b> Форму содержимого знает
 * только производитель, общего носителя имён у сторон нет намеренно, и цена
 * решения названа: расхождение не ловится ничем, кроме теста у писателя
 * формы (docs/architecture/contracts.md §«Носителя имён в общей библиотеке
 * не заводится, и это решение»). Недоложенный операнд при этом не роняет
 * ничего — величина выходит пустой, а строка агрегата остаётся
 * <b>правдоподобной</b>.
 *
 * <p><b>Тест собирает НАСТОЯЩИХ писателей</b> — исполнитель терминала,
 * создатель сделки, исполнители заявки и условной заявки, ребро подъёма
 * ступени (и точку входа блокировки над ним), тропу снятия ручной
 * поверхности и сервис отчёта, — вместе с живым
 * сериализатором: предмет проверки лежит ровно на стыке, где содержимое
 * собирается одним звеном, а телом строки становится другим.
 *
 * <p><b>Идентичности радиуса здесь проверяются на ВЕРХНЕМ уровне у одного
 * класса события — терминала сделки</b> (у создания сделки — только
 * определение стратегии; у прочих классов этот тест имён идентичностей не
 * проверяет): читатель журнала форм не знает и достаёт из содержимого только
 * одноимённые компоненты верхнего уровня
 * (docs/models/domain/other/AuditRecord.md). У прочих классов, которые несёт
 * статистика, имя биржевого счёта связывает проба её стороны —
 * {@code WireWordContractTest} в services/statistics: она собирает содержимое
 * компонентами форм, которые строит маппер этого писателя, и требует, чтобы
 * читатель статистики нашёл их своими константами.
 */
class CoreEventFormTest {

    /** Маппер domain → message: формы событий строит граница, а не домен. */
    private static final CoreEventMessageMapper EVENT_MESSAGES = new CoreEventMessageMapperImpl();

    private static final String EXCHANGE = "OKX";
    private static final String SETTLE = "USDT";
    private static final String TENANT = "tn-0001";
    private static final String ACCOUNT_INTERNAL_ID = "ea-0001";
    private static final String INSTRUMENT_INTERNAL_ID = "in-0001";
    private static final String STRATEGY_INTERNAL_ID = "st-0001";
    private static final String DEAL_INTERNAL_ID = "dl-0001";
    private static final String TRANCHE_INTERNAL_ID = "dt-0001";
    private static final String PRESENTED_PRINCIPAL = "holder";
    private static final Long ACCOUNT_ID = 4L;
    private static final Long INSTRUMENT_ID = 5L;
    private static final Long DEAL_ID = 1L;
    private static final Long TRANCHE_ID = 2L;
    private static final Long DETAIL_ID = 31L;
    private static final Long ANCHOR_ID = 12L;

    /**
     * <b>Слова провода, по которым ветвится ЧУЖОЙ сервис.</b> Агрегат
     * статистики считает жёсткие остановки предикатом
     * {@code fact.hold_rung = 'HARD'}, а критические происшествия —
     * {@code fact.anomaly_severity = 'CRITICAL'}: колонки раскладывает приём
     * из полей содержимого {@code rung} и {@code severity}
     * (FactAggregateSourceRepository, docs/spec/statistics-aggregates.json).
     *
     * <p><b>Почему пин здесь словом, а не символом перечня, и по какому
     * признаку форма выбирается</b> — .claude/rules/codestyle.md, «Пин
     * значения, пересекающего провод: слово или символ». Здесь — только
     * сами слова; значения, по которым потребитель НЕ ветвится, пинятся
     * символом по месту.
     */
    private static final String HARD_RUNG_ON_THE_WIRE = "HARD";
    private static final String CRITICAL_SEVERITY_ON_THE_WIRE = "CRITICAL";

    /**
     * Причина закрытия стопом — слово, по которому агрегат статистики
     * отбирает сделки, закрытые стопом, в пару счётчиков проскока
     * (docs/rules/statistics-aggregates.md): потребитель ветвится по нему, и
     * пин — словом, тем же правилом, что у двух слов выше.
     */
    private static final String STOP_LOSS_CLOSE_REASON_ON_THE_WIRE = "STOP_LOSS";

    private final OutboxDataService outboxDataService = mock(OutboxDataService.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final CoreEventWriter coreEventWriter = new CoreEventWriter(outboxDataService, objectMapper, EVENT_MESSAGES);
    private final ActorProvider actorProvider = new ActorProvider();

    private final DealDataService dealDataService = mock(DealDataService.class);
    private final DealActionStateDataService actionStates = mock(DealActionStateDataService.class);
    private final AnomalyReportService reports = mock(AnomalyReportService.class);
    private final ExchangeAccountDataService accounts = mock(ExchangeAccountDataService.class);
    private final TenantRiskAppetiteDataService tenants = mock(TenantRiskAppetiteDataService.class);
    private final StrategyDataService strategies = mock(StrategyDataService.class);
    private final InstrumentDataService instruments = mock(InstrumentDataService.class);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    // --- терминал сделки --------------------------------------------------

    /**
     * Терминал везёт операнды отчёта и признаки отбора: без них зерно
     * агрегата собирается <b>ложным</b> — денежные суммы отбираются
     * предикатом, чьих операндов в содержимом нет, а строка выходит
     * правдоподобной.
     */
    @Test
    @DisplayName("Содержимое терминала несёт операнды отчёта, признаки отбора и радиус верхним уровнем")
    void theTerminalContentCarriesTheReportOperandsAndTheSelectionFeatures() throws Exception {
        Deal deal = enteredDeal();
        DealContext context = context(deal, true, definition());

        closedExecutor().execute(terminalCommand(), anchor(), context);

        JsonNode content = contentOfWrittenRow(CoreEventType.DEAL_CLOSED);
        assertThat(content.path("dealInternalId").textValue()).isEqualTo(DEAL_INTERNAL_ID);
        assertThat(content.path("exchangeAccountInternalId").textValue()).isEqualTo(ACCOUNT_INTERNAL_ID);
        assertThat(content.path("instrumentInternalId").textValue()).isEqualTo(INSTRUMENT_INTERNAL_ID);
        assertThat(content.path("strategyInternalId").textValue())
                .as("определение — ключ зерна агрегата: без него строка уходит под ключ «стратегии нет»")
                .isEqualTo(STRATEGY_INTERNAL_ID);
        assertThat(content.path("status").textValue()).isEqualTo(Deal.Status.CLOSED.name());
        assertThat(content.path("tookRisk").booleanValue())
                .as("популяция долей — сделки, принявшие риск")
                .isTrue();
        assertThat(content.path("graphComplete").booleanValue())
                .as("третий операнд результата до финансирования: на усечённом графе сумма нулевая молча")
                .isTrue();
        assertThat(content.path("result").decimalValue()).isEqualByComparingTo("-7");
        assertThat(content.path("resultCurrency").textValue()).isEqualTo(SETTLE);
        assertThat(content.path("funding").decimalValue())
                .as("финансирование в домене уже нормализовано издержкой")
                .isEqualByComparingTo("3");
        assertThat(content.path("fee").decimalValue())
                .as("у комиссии в домене СЫРОЙ знак: издержкой её делает писатель")
                .isEqualByComparingTo("2");
        assertThat(content.path("liquidationPenalty").decimalValue())
                .as("штраф входит в оба публикуемых числа вычтенным и задним числом невосстановим")
                .isEqualByComparingTo("5");
        assertThat(content.path("closeOutcome").textValue())
                .isEqualTo(Deal.CloseOutcome.NORMAL_EXIT.name());
        assertThat(content.path("reconciliationStatus").textValue())
                .as("два признака из четырёх означают «число известно ненадёжным»")
                .isEqualTo(Deal.ReconciliationStatus.MATCHED.name());
        assertThat(content.path("breakdownIncomplete").textValue())
                .isEqualTo(Deal.BreakdownCompleteness.COMPLETE.name());
        assertThat(content.path("riskBenchmarkAvailability").textValue())
                .isEqualTo(Deal.RiskBenchmarkAvailability.AVAILABLE.name());
    }

    /**
     * Сделка, закрытая стопом, везёт проскок выхода по стопу, посчитанный
     * писателем терминала по графу прохода, и причину закрытия словом, по
     * которому статистика ветвится (docs/spec/stop-exit-slippage.json).
     */
    @Test
    @DisplayName("Терминал сделки, закрытой стопом, несёт проскок выхода и причину словом провода")
    void theStopClosedTerminalCarriesTheStopExitSlippage() throws Exception {
        Deal deal = stopClosedDeal();
        DealContext context = context(deal, true, definition());

        closedExecutor().execute(terminalCommand(), anchor(), context);

        JsonNode content = contentOfWrittenRow(CoreEventType.DEAL_CLOSED);
        assertThat(content.path("closeReason").textValue()).isEqualTo(STOP_LOSS_CLOSE_REASON_ON_THE_WIRE);
        assertThat(content.path("stopExitSlippage").decimalValue())
                .as("уровень 2910, выход 2880, 100 контрактов по 0.1: неблагоприятный проскок положителен")
                .isEqualByComparingTo("300");
    }

    /**
     * Пустая величина едет ПУСТОЙ, а не текстом.
     *
     * <p>Проба о том, чего в содержимом нет: {@code String.valueOf} пустого
     * даёт литерал {@code "null"}, предикат «значения нет» у читателя
     * становится тождественно ложным, и недоступное входит в суммы под
     * видом значения (docs/architecture/contracts.md §«Пустое значение едет
     * пустым, а не текстом»).
     */
    @Test
    @DisplayName("Незаданный операнд терминала едет пустым, а не литералом «null»")
    void anAbsentTerminalOperandTravelsAbsent() throws Exception {
        Deal deal = enteredDeal();
        DealContext context = context(deal, true, null);

        closedExecutor().execute(terminalCommand(), anchor(), context);

        JsonNode content = contentOfWrittenRow(CoreEventType.DEAL_CLOSED);
        assertThat(content.path("plannedRisk").isNull())
                .as("сайзинга на тропе не было — знаменатель пуст, и пустота есть значение")
                .isTrue();
        assertThat(content.path("strategyInternalId").isNull())
                .as("сделка заведена восстановлением: определения у неё не было")
                .isTrue();
        assertThat(content.path("plannedRisk").textValue()).isNotEqualTo("null");
        assertThat(content.path("strategyInternalId").textValue()).isNotEqualTo("null");
        assertThat(content.path("stopExitSlippage").isNull())
                .as("сделка закрыта не стопом — мера неприменима, и пустота нулём не читается")
                .isTrue();
    }

    /**
     * Класс события и версия формы приезжают на строку ИЗ конверта: вторая
     * запись одного и того же разошлась бы с первой первой же правкой.
     */
    @Test
    @DisplayName("Класс события и версия формы приезжают на строку из конверта")
    void theOutboxRowTakesItsDiscriminatorAndVersionFromTheEnvelope() {
        Deal deal = enteredDeal();
        DealContext context = context(deal, true, definition());

        closedExecutor().execute(terminalCommand(), anchor(), context);

        OutboxEntity row = writtenRow(CoreEventType.DEAL_CLOSED);
        assertThat(row.getEventType()).isEqualTo(CoreEventType.DEAL_CLOSED.name());
        assertThat(row.getVersion())
                .as("версия поднимается один раз на ход, а не на форму: третья принесла предшественника "
                        + "и два новых класса")
                .isEqualTo(3);
        assertThat(row.getTenantId()).isEqualTo(TENANT);
        assertThat(row.getTopic()).isEqualTo("trading-core.facts");
    }

    // --- создание сделки --------------------------------------------------

    /** Создание везёт контекст входа: фазу и идентичность определения. */
    @Test
    @DisplayName("Содержимое создания несёт фазу входа и идентичность определения")
    void theOpenedContentCarriesTheEntryContext() throws Exception {
        when(dealDataService.existsActiveOnPair(ACCOUNT_ID, INSTRUMENT_ID)).thenReturn(false);
        when(dealDataService.create(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(strategies.getRequiredStrategyInternalIdByDetailId(DETAIL_ID))
                .thenReturn(STRATEGY_INTERNAL_ID);

        openingService().openDeal(account(), instrument(), detail(), StrategyTradeDirection.LONG,
                MarketPhase.Type.BULL_TREND, OffsetDateTime.now(ZoneOffset.UTC));

        JsonNode content = contentOfWrittenRow(CoreEventType.DEAL_OPENED);
        assertThat(content.path("strategyInternalId").textValue()).isEqualTo(STRATEGY_INTERNAL_ID);
        assertThat(content.path("entryMarketPhase").textValue())
                .as("деталь закреплена по фазе, и фаза остаётся атрибутом входа")
                .isEqualTo(MarketPhase.Type.BULL_TREND.name());
        assertThat(content.path("entryReason").textValue()).isEqualTo(Deal.EntryReason.STRATEGY.name());
    }

    /**
     * У восстановленной сделки обе половины пары ПУСТЫ, и это значение:
     * входа по объявлению не было.
     */
    @Test
    @DisplayName("У восстановленной сделки контекст входа пуст, а не подставлен")
    void theRecoveredDealCarriesAnEmptyEntryContext() throws Exception {
        when(dealDataService.existsActiveOnPair(ACCOUNT_ID, INSTRUMENT_ID)).thenReturn(false);
        when(dealDataService.create(any())).thenAnswer(invocation -> invocation.getArgument(0));

        openingService().recoverDeal(account(), instrument(), StrategyTradeDirection.LONG,
                OffsetDateTime.now(ZoneOffset.UTC));

        JsonNode content = contentOfWrittenRow(CoreEventType.DEAL_OPENED);
        assertThat(content.path("strategyInternalId").isNull()).isTrue();
        assertThat(content.path("entryMarketPhase").isNull()).isTrue();
        assertThat(content.path("entryReason").textValue()).isEqualTo(Deal.EntryReason.RECOVERY.name());
    }

    // --- решение о заявке -------------------------------------------------

    /**
     * Решение о заявке везёт идентичность транша: без неё при нескольких
     * уровнях входа заявки к траншам неатрибутируемы. Цена рыночной заявки
     * при этом пуста законно.
     */
    @Test
    @DisplayName("Решение о заявке несёт транш, а пустая цена едет пустой")
    void theOrderDecisionCarriesItsTrancheAndAnAbsentPrice() throws Exception {
        Deal deal = enteredDeal();
        DealContext context = context(deal, true, definition());
        DealRiskNumbersService riskNumbers = mock(DealRiskNumbersService.class);
        when(riskNumbers.recompute(any())).thenReturn(true);
        OrderDataService orders = mock(OrderDataService.class);
        when(orders.save(any())).thenAnswer(invocation -> {
            Order saved = invocation.getArgument(0);
            saved.setId(77L);
            return saved;
        });
        CreateOrderExecutor executor = new CreateOrderExecutor(orders, actionStates, dealDataService,
                riskNumbers, coreEventWriter);

        executor.execute(createOrderCommand(), createAnchor(), context);

        JsonNode content = contentOfWrittenRow(CoreEventType.ORDER_DECIDED);
        assertThat(content.path("dealTrancheInternalId").textValue()).isEqualTo(TRANCHE_INTERNAL_ID);
        assertThat(content.path("plannedEntryPrice").isNull())
                .as("у рыночной заявки цены нет: литерал «null» завёл бы значение, которого нет в домене")
                .isTrue();
        assertThat(content.path("plannedSizeContracts").decimalValue()).isEqualByComparingTo("3");
        assertThat(content.path("replacesInternalId").isNull())
                .as("исполнителя замещения нет: заявка заведена первичной постановкой, и пустота есть значение")
                .isTrue();
    }

    /**
     * Предшественник в цепочке замещений едет С ЗАЯВКИ, а не выводится:
     * исполнителя замещения в ядре ещё нет, и значение предъявляется моделью
     * напрямую — как его положит будущий писатель ремодела.
     */
    @Test
    @DisplayName("Решение о заявке несёт предшественника в цепочке замещений")
    void theOrderDecisionCarriesItsPredecessor() throws Exception {
        Order order = new Order();
        order.setInternalId("vtb-new");
        order.setReplacesInternalId("vtb-old");

        coreEventWriter.orderDecided(TENANT, order, DEAL_INTERNAL_ID, TRANCHE_INTERNAL_ID,
                ACCOUNT_INTERNAL_ID, INSTRUMENT_INTERNAL_ID);

        JsonNode content = contentOfWrittenRow(CoreEventType.ORDER_DECIDED);
        assertThat(content.path("replacesInternalId").textValue()).isEqualTo("vtb-old");
        assertThat(content.path("orderInternalId").textValue()).isEqualTo("vtb-new");
    }

    // --- решение об отдельной условной заявке -----------------------------

    /**
     * Решение об условной заявке везёт идентичности верхним уровнем, тип
     * условия, сторону, размер и параметры условия — ради них решение о
     * защите и журналируется. Нога, которой у условия нет, и
     * предшественник первичной постановки едут пустыми, а не текстом.
     */
    @Test
    @DisplayName("Решение об условной заявке несёт радиус, условие и пустого предшественника")
    void theAlgoOrderDecisionCarriesItsRadiusAndItsCondition() throws Exception {
        Deal deal = enteredDeal();
        DealContext context = context(deal, true, definition());
        DealRiskNumbersService riskNumbers = mock(DealRiskNumbersService.class);
        when(riskNumbers.recompute(any())).thenReturn(true);
        AlgoOrderDataService algoOrders = mock(AlgoOrderDataService.class);
        when(algoOrders.save(any())).thenAnswer(invocation -> {
            AlgoOrder saved = invocation.getArgument(0);
            saved.setId(88L);
            return saved;
        });
        CreateAlgoOrderExecutor executor = new CreateAlgoOrderExecutor(algoOrders, actionStates, riskNumbers,
                coreEventWriter);

        executor.execute(createAlgoOrderCommand(), createAnchor(), context);

        JsonNode content = contentOfWrittenRow(CoreEventType.ALGO_ORDER_DECIDED);
        assertThat(content.path("algoOrderInternalId").textValue()).isNotBlank();
        assertThat(content.path("dealInternalId").textValue()).isEqualTo(DEAL_INTERNAL_ID);
        assertThat(content.path("dealTrancheInternalId").textValue()).isEqualTo(TRANCHE_INTERNAL_ID);
        assertThat(content.path("exchangeAccountInternalId").textValue()).isEqualTo(ACCOUNT_INTERNAL_ID);
        assertThat(content.path("instrumentInternalId").textValue()).isEqualTo(INSTRUMENT_INTERNAL_ID);
        assertThat(content.path("conditionType").textValue()).isEqualTo(AlgoOrder.ConditionType.STOP_LOSS.name());
        assertThat(content.path("direction").textValue()).isEqualTo(AlgoOrder.Direction.SELL.name());
        assertThat(content.path("sizeContracts").decimalValue()).isEqualByComparingTo("3");
        assertThat(content.path("stopLossTriggerPrice").decimalValue()).isEqualByComparingTo("95");
        assertThat(content.path("stopLossTriggerPriceType").textValue())
                .isEqualTo(AlgoOrder.TriggerPriceType.MARK.name());
        assertThat(content.path("takeProfitTriggerPrice").isNull())
                .as("ноги фиксации прибыли у стопа нет: пустота есть значение")
                .isTrue();
        assertThat(content.path("trailingPercents").isNull()).isTrue();
        assertThat(content.path("replacesInternalId").isNull())
                .as("первичная постановка: предшественника нет")
                .isTrue();
        assertThat(content.has("actor"))
                .as("ручной тропы у класса нет — актора в содержимом нет")
                .isFalse();
    }

    /** Повтор звена по уже заведённой строке события не производит: решение принято однажды. */
    @Test
    @DisplayName("Повтор создания условной заявки события не пишет")
    void aRetriedAlgoOrderCreationWritesNoEvent() {
        Deal deal = enteredDeal();
        DealContext context = context(deal, true, definition());
        DealRiskNumbersService riskNumbers = mock(DealRiskNumbersService.class);
        when(riskNumbers.recompute(any())).thenReturn(true);
        AlgoOrderDataService algoOrders = mock(AlgoOrderDataService.class);
        AlgoOrder existing = new AlgoOrder();
        existing.setId(88L);
        existing.setDealTrancheId(TRANCHE_ID);
        when(algoOrders.getRequiredById(88L)).thenReturn(existing);
        DealActionState retried = createAnchor();
        retried.targetAt(TargetEntityType.ALGO_ORDER, 88L);
        CreateAlgoOrderExecutor executor = new CreateAlgoOrderExecutor(algoOrders, actionStates, riskNumbers,
                coreEventWriter);

        executor.execute(createAlgoOrderCommand(), retried, context);

        verify(outboxDataService, never()).save(any());
    }

    // --- подъём ступени и отчёт о происшествии ----------------------------

    /**
     * Подъём ступени везёт актора: ту же ступень ставит и проход, и
     * держатель, и без актора журнал не отвечает на «кто остановил
     * контур».
     */
    @Test
    @DisplayName("Подъём ступени ручной тропой несёт имя предъявленного принципала")
    void theHoldRaisedContentCarriesThePresentedPrincipal() throws Exception {
        givenPresentedPrincipal();
        AccountInstrumentStateDataService pairStates = mock(AccountInstrumentStateDataService.class);
        when(pairStates.raiseRung(anyLong(), anyLong(), any())).thenReturn(true);
        HoldService holdService = new HoldService(reports, mock(SafetyHoldCoordinator.class),
                new HoldRungEdgeService(pairStates, accounts, actorProvider, coreEventWriter));

        holdService.raise(HoldSignal.instrumentSoft(Constants.Hold.MANUAL_HALT_REQUESTED),
                context(enteredDeal(), true, definition()));

        JsonNode content = contentOfWrittenRow(CoreEventType.HOLD_RAISED);
        assertThat(content.path("actor").textValue()).isEqualTo(PRESENTED_PRINCIPAL);
        assertThat(content.path("code").textValue())
                .as("разрез «ручная против автоматической» идёт по КОДУ, а не по актору")
                .isEqualTo(Constants.Hold.MANUAL_HALT_REQUESTED);
        assertThat(content.path("rung").textValue())
                .as("ступень СИГНАЛА, а не лестницы объекта: оттуда сюда приехало бы ENTRY_BLOCKED")
                .isEqualTo(HoldRung.SOFT.name());
    }

    /**
     * <b>Жёсткая ступень едет словом, которое СЧИТАЕТ чужой сервис.</b>
     * Ребро подъёма пишет ступень сигнала, а рядом с этой строкой стои́т
     * выбор ступени ЛЕСТНИЦЫ объекта радиуса — {@code TRADE_BLOCKED} на том
     * же слове «ступень». Подстановка соседа отсюда — обычный ход, и
     * потребитель принял бы её молча: его предикат не совпал бы ни с одной
     * строкой, а нулевой счётчик правдоподобен.
     */
    @Test
    @DisplayName("Жёсткая ступень едет словом, по которому агрегат считает остановки")
    void theHardRungTravelsAsTheWordTheAggregateCounts() throws Exception {
        AccountInstrumentStateDataService pairStates = mock(AccountInstrumentStateDataService.class);
        when(pairStates.raiseRung(anyLong(), anyLong(), any())).thenReturn(true);
        HoldRungEdgeService rungEdges = new HoldRungEdgeService(pairStates, accounts, actorProvider,
                coreEventWriter);

        rungEdges.raise(HoldSignal.instrument(Constants.Hold.INSTRUMENT_RETRY_BUDGET_EXHAUSTED),
                context(enteredDeal(), true, definition()));

        assertThat(contentOfWrittenRow(CoreEventType.HOLD_RAISED).path("rung").textValue())
                .isEqualTo(HARD_RUNG_ON_THE_WIRE);
    }

    /** Тот же подъём собственным проходом несёт класс контура, а не пустоту. */
    @Test
    @DisplayName("Подъём ступени проходом несёт класс контура")
    void theHoldRaisedContentCarriesTheContourClassWithoutAPrincipal() throws Exception {
        AccountInstrumentStateDataService pairStates = mock(AccountInstrumentStateDataService.class);
        when(pairStates.raiseRung(anyLong(), anyLong(), any())).thenReturn(true);
        HoldService holdService = new HoldService(reports, mock(SafetyHoldCoordinator.class),
                new HoldRungEdgeService(pairStates, accounts, actorProvider, coreEventWriter));

        holdService.raise(HoldSignal.instrumentSoft(Constants.Hold.INSTRUMENT_RETRY_BUDGET_EXHAUSTED),
                context(enteredDeal(), true, definition()));

        assertThat(contentOfWrittenRow(CoreEventType.HOLD_RAISED).path("actor").textValue())
                .as("пусто в контексте означает «внешнего инициатора нет» — это признак, а не умолчание")
                .isEqualTo(SYSTEM_PRINCIPAL);
    }

    /**
     * <b>Факт остановки везёт актора по тому же признаку.</b> Ручная тропа
     * у класса есть: держатель сворачивает радиус той же ступенью, а
     * первый ход её энфорсмента уводит сделки радиуса тем же ребром, что и
     * проход (docs/spec/event-actor-presence.json). Без актора журнал не
     * отвечает, кем остановлена сделка.
     */
    @Test
    @DisplayName("Факт остановки сделки ручной тропой несёт имя предъявленного принципала")
    void theDealShutdownContentCarriesThePresentedPrincipal() throws Exception {
        givenPresentedPrincipal();
        Deal deal = enteredDeal();
        when(dealDataService.enforceHardRung(eq(DEAL_ID), any())).thenReturn(true);
        when(accounts.getRequiredTenantInternalIdById(ACCOUNT_ID)).thenReturn(TENANT);
        when(accounts.getRequiredInternalIdById(ACCOUNT_ID)).thenReturn(ACCOUNT_INTERNAL_ID);
        when(instruments.getRequiredInternalIdById(INSTRUMENT_ID)).thenReturn(INSTRUMENT_INTERNAL_ID);
        deal.setExchangeAccountId(ACCOUNT_ID);
        deal.setInstrumentId(INSTRUMENT_ID);
        DealStatusEdgeService statusEdges = new DealStatusEdgeService(dealDataService, accounts,
                instruments, strategies, actorProvider, coreEventWriter);

        statusEdges.enforceHardRung(deal, Deal.ShutdownReason.EXCHANGE_HOLD);

        JsonNode content = contentOfWrittenRow(CoreEventType.DEAL_SHUTDOWN_INITIATED);
        assertThat(content.path("actor").textValue()).isEqualTo(PRESENTED_PRINCIPAL);
        assertThat(content.path("shutdownReason").textValue())
                .isEqualTo(Deal.ShutdownReason.EXCHANGE_HOLD.name());
    }

    // --- снятие ступени --------------------------------------------------

    /**
     * Снятие везёт пару подъёма и актора — без кода причины: основание
     * снятия одно, а код ручной тропы едет журнальной строкой той же
     * транзакции. Мягкий класс счёта снимает МЯГКУЮ ступень, и инструмента у
     * счётного радиуса нет.
     */
    @Test
    @DisplayName("Снятие ступени несёт радиус, снятую ступень и актора, а кода причины не несёт")
    void theHoldReleasedContentCarriesTheRadiusTheReleasedRungAndTheActor() throws Exception {
        givenPresentedPrincipal();
        when(accounts.getRequiredByInternalId(ACCOUNT_INTERNAL_ID)).thenReturn(account());
        when(accounts.clearRung(any(), any(), any())).thenReturn(true);

        manualHalt().clear(ManualHaltClass.FREEZE, ACCOUNT_INTERNAL_ID, null);

        JsonNode content = contentOfWrittenRow(CoreEventType.HOLD_RELEASED);
        assertThat(content.path("exchangeAccountInternalId").textValue()).isEqualTo(ACCOUNT_INTERNAL_ID);
        assertThat(content.path("instrumentInternalId").isNull())
                .as("счётный радиус инструмента не называет по построению")
                .isTrue();
        assertThat(content.path("scope").textValue()).isEqualTo(HoldScope.EXCHANGE_ACCOUNT.name());
        assertThat(content.path("rung").textValue()).isEqualTo(HoldRung.SOFT.name());
        assertThat(content.path("actor").textValue()).isEqualTo(PRESENTED_PRINCIPAL);
        assertThat(content.has("code"))
                .as("кода причины у снятия нет: направление несёт класс")
                .isFalse();
    }

    /** Снятие сворачивания пары снимает ЖЁСТКУЮ ступень и называет инструмент радиуса. */
    @Test
    @DisplayName("Снятие сворачивания пары несёт жёсткую ступень и инструмент радиуса")
    void thePairTeardownReleaseCarriesTheHardRungAndTheInstrument() throws Exception {
        givenPresentedPrincipal();
        AccountInstrumentStateDataService pairStates = mock(AccountInstrumentStateDataService.class);
        AccountInstrumentState pair = new AccountInstrumentState();
        pair.setExchangeAccountId(ACCOUNT_ID);
        pair.setInstrumentId(INSTRUMENT_ID);
        pair.setSafetyRung(Instrument.SafetyRung.TRADE_BLOCKED);
        when(pairStates.getRequiredByPair(ACCOUNT_ID, INSTRUMENT_ID)).thenReturn(pair);
        when(pairStates.clearRung(any(), any(), any(), any())).thenReturn(true);
        when(accounts.getRequiredByInternalId(ACCOUNT_INTERNAL_ID)).thenReturn(account());
        when(instruments.getRequiredByInternalId(INSTRUMENT_INTERNAL_ID)).thenReturn(instrument());
        PositionSliceReader slices = mock(PositionSliceReader.class);
        when(slices.livePositions(any(), any())).thenReturn(new ArrayList<>());

        manualHalt(pairStates, slices).clear(ManualHaltClass.FULL, ACCOUNT_INTERNAL_ID, INSTRUMENT_INTERNAL_ID);

        JsonNode content = contentOfWrittenRow(CoreEventType.HOLD_RELEASED);
        assertThat(content.path("instrumentInternalId").textValue()).isEqualTo(INSTRUMENT_INTERNAL_ID);
        assertThat(content.path("scope").textValue()).isEqualTo(HoldScope.INSTRUMENT.name());
        assertThat(content.path("rung").textValue()).isEqualTo(HoldRung.HARD.name());
    }

    /** Холостое снятие строки outbox не заводит: ступень не переставлена — ничего не произошло. */
    @Test
    @DisplayName("Холостое снятие события не пишет")
    void aNoopClearanceWritesNoEvent() {
        when(accounts.getRequiredByInternalId(ACCOUNT_INTERNAL_ID)).thenReturn(account());
        when(accounts.clearRung(any(), any(), any())).thenReturn(false);

        manualHalt().clear(ManualHaltClass.FREEZE, ACCOUNT_INTERNAL_ID, null);

        verify(outboxDataService, never()).save(any());
    }

    /** Отчёт о происшествии везёт актора по тому же признаку ручной тропы. */
    @Test
    @DisplayName("Отчёт о происшествии несёт актора хода")
    void theAnomalyReportedContentCarriesTheActor() throws Exception {
        givenPresentedPrincipal();
        AnomalyReportDataService reportData = mock(AnomalyReportDataService.class);
        when(reportData.existsStanding(any(), any(), any(), anyString(), any(), any(), any()))
                .thenReturn(false);
        when(reportData.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        AnomalyReportService service = new AnomalyReportService(reportData,
                mock(ExchangeOperationsClient.class), objectMapper, new AnomalyReportProperties(),
                actorProvider, coreEventWriter);

        service.journalState(context(enteredDeal(), true, definition()),
                HoldSignal.instrumentJournal(Constants.Hold.MANUAL_HALT_CLEARED), null);

        JsonNode content = contentOfWrittenRow(CoreEventType.ANOMALY_REPORTED);
        assertThat(content.path("actor").textValue()).isEqualTo(PRESENTED_PRINCIPAL);
        assertThat(content.path("severity").textValue())
                .isEqualTo(AnomalyReport.Severity.NON_CRITICAL.name());
        assertThat(content.path("scope").textValue()).isEqualTo(HoldScope.INSTRUMENT.name());
    }

    /**
     * <b>Критический класс едет словом, которое СЧИТАЕТ чужой сервис.</b>
     * Тот же разрез, что у жёсткой ступени, и та же цена ошибки: счётчик
     * критических происшествий встал бы на нуле, а ноль от «критических не
     * было» не отличается ничем.
     */
    @Test
    @DisplayName("Критический класс происшествия едет словом, по которому агрегат считает")
    void theCriticalSeverityTravelsAsTheWordTheAggregateCounts() throws Exception {
        AnomalyReportDataService reportData = mock(AnomalyReportDataService.class);
        when(reportData.existsStanding(any(), any(), any(), anyString(), any(), any(), any()))
                .thenReturn(false);
        when(reportData.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        AnomalyReportService service = new AnomalyReportService(reportData,
                mock(ExchangeOperationsClient.class), objectMapper, new AnomalyReportProperties(),
                actorProvider, coreEventWriter);

        service.journalState(context(enteredDeal(), true, definition()),
                HoldSignal.instrument(Constants.Hold.INSTRUMENT_RETRY_BUDGET_EXHAUSTED), null);

        assertThat(contentOfWrittenRow(CoreEventType.ANOMALY_REPORTED).path("severity").textValue())
                .isEqualTo(CRITICAL_SEVERITY_ON_THE_WIRE);
    }

    // --- сборка -----------------------------------------------------------

    private MarkDealClosedExecutor closedExecutor() {
        ExchangeContourProperties contourProperties = new ExchangeContourProperties();
        contourProperties.setExchanges(new LinkedHashMap<>(
                Map.of(EXCHANGE, new ExchangeContourProperties.Contour())));
        DealReconciliationCalculator reconciliationCalculator =
                new DealReconciliationCalculator(contourProperties, new PnlReconciliationProperties());
        DealTerminalFeaturesWriter featuresWriter =
                new DealTerminalFeaturesWriter(reconciliationCalculator, contourProperties, reports);
        when(tenants.findByTenantInternalId(anyString())).thenReturn(Optional.<Tenant>empty());
        // Терминальное ребро применилось: умолчание мока обратное, и факт
        // на неприменившемся ребре не публикуется по построению.
        when(dealDataService.applyTerminalEdge(any(), any())).thenReturn(true);
        return new MarkDealClosedExecutor(dealDataService, actionStates, reconciliationCalculator,
                featuresWriter, new DealTerminalGate(), new LossStreakCounter(accounts, tenants),
                reports, coreEventWriter);
    }

    private DealOpeningService openingService() {
        return new DealOpeningService(dealDataService, mock(DealTrancheDataService.class),
                strategies, coreEventWriter);
    }

    /** Ручная поверхность на счётном радиусе: к паре и площадке снятие мягкой ступени не ходит. */
    private ManualHaltService manualHalt() {
        return manualHalt(mock(AccountInstrumentStateDataService.class), mock(PositionSliceReader.class));
    }

    /**
     * Ручная поверхность с НАСТОЯЩИМ писателем событий: предмет — состав
     * того, что снятие кладёт в outbox. Сделок у радиуса нет — выборка
     * службы сделок по умолчанию пуста.
     */
    private ManualHaltService manualHalt(AccountInstrumentStateDataService pairStates, PositionSliceReader slices) {
        return new ManualHaltService(accounts, instruments, pairStates, dealDataService,
                mock(DealContextService.class), new DealTerminalGate(), mock(SafetyHoldCoordinator.class),
                mock(HoldService.class), reports, slices, actorProvider, coreEventWriter);
    }

    /** Строка, которую писатель отдал границе персистентности. */
    private OutboxEntity writtenRow(CoreEventType type) {
        ArgumentCaptor<OutboxEntity> captor = ArgumentCaptor.forClass(OutboxEntity.class);
        verify(outboxDataService, atLeastOnce()).save(captor.capture());
        return captor.getAllValues().stream()
                .filter(row -> Objects.equals(type.name(), row.getEventType()))
                .reduce((first, second) -> second)
                .orElseThrow(() -> new AssertionError("Событие класса " + type + " не записано"));
    }

    /** Тело строки разобранным документом — так его увидит потребитель. */
    private JsonNode contentOfWrittenRow(CoreEventType type) throws Exception {
        return objectMapper.readTree(writtenRow(type).getPayload());
    }

    private void givenPresentedPrincipal() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                PRESENTED_PRINCIPAL, "n/a", AuthorityUtils.createAuthorityList("ROLE_USER")));
    }

    /**
     * Вошедшая сделка, готовая к терминалу: транш терминален, живого риска
     * нет, число посчитано, издержки эпизода наблюдены.
     */
    private static Deal enteredDeal() {
        Deal deal = new Deal();
        deal.setId(DEAL_ID);
        deal.setInternalId(DEAL_INTERNAL_ID);
        deal.setStatus(Deal.Status.EXIT_PENDING);
        deal.setEntryReason(Deal.EntryReason.RECOVERY);
        deal.setResultProfit(new BigDecimal("-7"));
        deal.setResultProfitCurrency(SETTLE);
        deal.setCloseOutcome(Deal.CloseOutcome.NORMAL_EXIT);
        deal.setReconciliationStatus(Deal.ReconciliationStatus.MATCHED);
        deal.setBreakdownIncomplete(Deal.BreakdownCompleteness.COMPLETE);
        deal.setRiskBenchmarkAvailability(Deal.RiskBenchmarkAvailability.AVAILABLE);
        deal.setTranches(List.of(terminalTranche()));
        deal.setPositions(List.of(closedEpisode()));
        return deal;
    }

    /**
     * Вошедшая сделка, закрытая стопом: налитая входная нога транша, её
     * встроенная защита сработала на уровне 2910 и закрыла налитое, эпизод
     * закрыт по средней цене 2880. Налив и закрытое защитой стоят на транше
     * так, как их выводит сборка графа.
     */
    private static Deal stopClosedDeal() {
        AttachedAlgoOrder protection = new AttachedAlgoOrder();
        protection.setStatus(AttachedAlgoOrder.Status.COMPLETED);
        protection.setCloseReason(AttachedAlgoOrder.CloseReason.TRIGGERED);
        protection.setSize(new BigDecimal("100"));
        protection.setStopLossTriggerPrice(new BigDecimal("2910"));
        Order entry = new Order();
        entry.setStatus(Order.Status.COMPLETED);
        entry.setPositionReducingOnly(false);
        entry.setAccumulatedFillSize(new BigDecimal("100"));
        entry.setPlannedContractValue(new BigDecimal("0.1"));
        entry.setAttachedAlgoOrders(new ArrayList<>(List.of(protection)));
        DealTranche tranche = terminalTranche();
        tranche.setCloseReason(DealTranche.CloseReason.STOP_LOSS);
        tranche.setOrders(new ArrayList<>(List.of(entry)));
        tranche.setEntryFilled(new BigDecimal("100"));
        tranche.setProtectionClosed(new BigDecimal("100"));
        Position episode = closedEpisode();
        episode.setExternalCloseType("2");
        episode.setExternalCloseAveragePrice(new BigDecimal("2880"));
        Deal deal = enteredDeal();
        deal.setDirection(StrategyTradeDirection.LONG);
        deal.setTranches(List.of(tranche));
        deal.setPositions(List.of(episode));
        return deal;
    }

    private static DealTranche terminalTranche() {
        DealTranche tranche = new DealTranche();
        tranche.setId(TRANCHE_ID);
        tranche.setInternalId(TRANCHE_INTERNAL_ID);
        tranche.setStatus(DealTranche.Status.CLOSED);
        tranche.setCloseReason(DealTranche.CloseReason.STRATEGY_EXIT);
        return tranche;
    }

    /**
     * Закрытый эпизод с издержками СЫРЫХ знаков источника: комиссия и штраф
     * приходят отрицательными, финансирование — уже издержкой.
     */
    private static Position closedEpisode() {
        Position episode = new Position();
        episode.setStatus(Position.Status.CLOSED);
        episode.setExternalSize(BigDecimal.ZERO);
        episode.setExternalRealizedProfit(new BigDecimal("-7"));
        episode.setExternalCloseType("1");
        episode.setExternalFee(new BigDecimal("-2"));
        episode.setExternalFundingCost(new BigDecimal("3"));
        episode.setExternalLiquidationPenalty(new BigDecimal("-5"));
        return episode;
    }

    private static Strategy definition() {
        Strategy definition = new Strategy();
        definition.setInternalId(STRATEGY_INTERNAL_ID);
        definition.setTenantId(TENANT);
        return definition;
    }

    private static StrategyDetail detail() {
        StrategyDetail detail = new StrategyDetail();
        detail.setId(DETAIL_ID);
        return detail;
    }

    private static ExchangeAccount account() {
        ExchangeAccount account = new ExchangeAccount();
        account.setId(ACCOUNT_ID);
        account.setInternalId(ACCOUNT_INTERNAL_ID);
        account.setExchangeCode(EXCHANGE);
        account.setTenantId(TENANT);
        account.setConsecutiveLossCount(0);
        return account;
    }

    private static Instrument instrument() {
        Instrument instrument = new Instrument();
        instrument.setId(INSTRUMENT_ID);
        instrument.setInternalId(INSTRUMENT_INTERNAL_ID);
        instrument.setExternalSettlementCurrency(SETTLE);
        return instrument;
    }

    private static DealContext context(Deal deal, boolean graphComplete, Strategy definition) {
        return DealContext.builder()
                .deal(deal)
                .exchangeAccount(account())
                .instrument(instrument())
                .strategy(definition)
                .actionStates(new ArrayList<>())
                .cashFlows(new ArrayList<>())
                .graphComplete(graphComplete)
                .flowsComplete(true)
                .build();
    }

    private static ServiceCommand terminalCommand() {
        return ServiceCommand.builder()
                .type(ServiceCommandType.MARK_DEAL_CLOSED_COMMAND)
                .dealId(DEAL_ID)
                .dealActionStateId(ANCHOR_ID)
                .build();
    }

    private static ServiceCommand createOrderCommand() {
        return ServiceCommand.builder()
                .type(ServiceCommandType.CREATE_ORDER_COMMAND)
                .dealId(DEAL_ID)
                .dealActionStateId(ANCHOR_ID)
                .payload(CreateOrderCommandPayload.builder()
                        .dealTrancheId(TRANCHE_ID)
                        .orderType(Order.Type.ENTRY)
                        .side(Order.Side.BUY)
                        .sizeContracts(new BigDecimal("3"))
                        .sendPriceToExchange(false)
                        .price(new BigDecimal("100"))
                        .build())
                .build();
    }

    /** Постановка стопа отдельной заявкой: одна нога — остановки убытка, по марк-цене. */
    private static ServiceCommand createAlgoOrderCommand() {
        TriggerPrice stopLoss = new TriggerPrice();
        stopLoss.setType(AlgoOrder.TriggerPriceType.MARK);
        stopLoss.setValue(new BigDecimal("95"));
        return ServiceCommand.builder()
                .type(ServiceCommandType.CREATE_ALGO_ORDER_COMMAND)
                .dealId(DEAL_ID)
                .dealActionStateId(ANCHOR_ID)
                .payload(CreateAlgoOrderCommandPayload.builder()
                        .dealTrancheId(TRANCHE_ID)
                        .conditionType(AlgoOrder.ConditionType.STOP_LOSS)
                        .direction(AlgoOrder.Direction.SELL)
                        .positionReducingOnly(true)
                        .sizeContracts(new BigDecimal("3"))
                        .condition(new Condition(AlgoOrder.ConditionType.STOP_LOSS,
                                new Trigger(stopLoss, null), null))
                        .build())
                .build();
    }

    private static DealActionState anchor() {
        DealActionState state = new DealActionState();
        state.setId(ANCHOR_ID);
        state.setDealId(DEAL_ID);
        state.setSystemActionType(SystemActionType.FINALIZE_DEAL_EXIT_ACTION);
        state.setStatus(DealActionStateStatus.PLANNED);
        return state;
    }

    private static DealActionState createAnchor() {
        DealActionState state = new DealActionState();
        state.setId(ANCHOR_ID);
        state.setDealId(DEAL_ID);
        state.setStatus(DealActionStateStatus.PLANNED);
        return state;
    }
}
