package com.example.tradingcore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyDetail;
import com.example.tradingbot.domain.model.core.balance.BalanceContainer;
import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingbot.domain.model.other.DealCashFlow;
import com.example.tradingcore.config.DealContextProperties;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.deal.DealContextService;
import com.example.tradingcore.domain.market.MarketFeatureService;
import com.example.tradingcore.persistence.service.AlgoOrderDataService;
import com.example.tradingcore.persistence.service.BalanceContainerDataService;
import com.example.tradingcore.persistence.service.DealActionStateDataService;
import com.example.tradingcore.persistence.service.DealCashFlowDataService;
import com.example.tradingcore.persistence.service.DealTrancheDataService;
import com.example.tradingcore.persistence.service.ExchangeAccountDataService;
import com.example.tradingcore.persistence.service.InstrumentDataService;
import com.example.tradingcore.persistence.service.OrderDataService;
import com.example.tradingcore.persistence.service.PositionDataService;
import com.example.tradingcore.persistence.service.StrategyDataService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Сборка контекста прохода — исполнимая форма
 * docs/spec/deal-context-load.json и §«Объёмы загрузки» дома
 * (docs/components/DealContextService.md).
 *
 * <p><b>Проверяется направление ошибки, а не наличие полей.</b> Предикат
 * полноты заведён против ТИХОГО занижения: усечённая выборка и
 * недогруженная коллекция дают ноль там, где число должно было выйти
 * положительным, и ошибка идёт в разрешающую сторону. Поэтому каждый
 * случай ниже — состояние, на котором наивная сборка ответила бы
 * «полно», а обязана ответить «неполно».
 *
 * <p>Состояние собирается настоящими строками графа, а не подменёнными
 * предикатами (.claude/rules/codestyle.md §«Тесты доменных моделей»):
 * «позиция наблюдалась» вытекает из налива транша, «вход отправлялся» —
 * из durable-колонки сделки.
 */
class DealContextAssemblyTest {

    private static final Long DEAL_ID = 7L;
    private static final Long ACCOUNT_ID = 3L;
    private static final Long INSTRUMENT_ID = 5L;
    private static final Long DETAIL_ID = 11L;
    private static final Integer FLOW_LIMIT = 2;
    private static final OffsetDateTime MOMENT = OffsetDateTime.parse("2026-09-06T10:00:00Z");

    private final ExchangeAccountDataService exchangeAccountDataService = mock(ExchangeAccountDataService.class);
    private final InstrumentDataService instrumentDataService = mock(InstrumentDataService.class);
    private final StrategyDataService strategyDataService = mock(StrategyDataService.class);
    private final OrderDataService orderDataService = mock(OrderDataService.class);
    private final AlgoOrderDataService algoOrderDataService = mock(AlgoOrderDataService.class);
    private final PositionDataService positionDataService = mock(PositionDataService.class);
    private final DealTrancheDataService dealTrancheDataService = mock(DealTrancheDataService.class);
    private final BalanceContainerDataService balanceContainerDataService =
            mock(BalanceContainerDataService.class);
    private final DealActionStateDataService dealActionStateDataService =
            mock(DealActionStateDataService.class);
    private final DealCashFlowDataService dealCashFlowDataService = mock(DealCashFlowDataService.class);
    private final DealContextProperties properties = new DealContextProperties();

    private final MarketFeatureService marketFeatureService = mock(MarketFeatureService.class);

    private final DealContextService service = new DealContextService(exchangeAccountDataService,
            instrumentDataService, strategyDataService, orderDataService, algoOrderDataService,
            positionDataService, dealTrancheDataService, balanceContainerDataService,
            dealActionStateDataService, dealCashFlowDataService, marketFeatureService, properties);

    @BeforeEach
    void setUp() {
        properties.setCashFlowWindowLimit(FLOW_LIMIT);
        when(exchangeAccountDataService.getRequiredById(ACCOUNT_ID)).thenReturn(account());
        when(instrumentDataService.getRequiredById(INSTRUMENT_ID)).thenReturn(new Instrument());
        when(strategyDataService.getRequiredDetailByIdWithTree(DETAIL_ID)).thenReturn(new StrategyDetail());
        when(balanceContainerDataService.findByExchangeAccountId(ACCOUNT_ID))
                .thenReturn(Optional.of(new BalanceContainer()));
        when(dealActionStateDataService.findByDealId(DEAL_ID)).thenReturn(List.of());
        when(orderDataService.findByDealId(DEAL_ID)).thenReturn(List.of());
        when(algoOrderDataService.findByDealId(DEAL_ID)).thenReturn(List.of());
        when(positionDataService.findEpisodes(DEAL_ID)).thenReturn(List.of());
        when(dealTrancheDataService.findByDealId(DEAL_ID)).thenReturn(List.of());
        when(dealCashFlowDataService.findByDealWindow(DEAL_ID, FLOW_LIMIT)).thenReturn(List.of());
    }

    /**
     * Выборка разбивки уперлась в потолок — это НЕПОЛНОТА, а не усечение:
     * расчёт итогового числа запрещён. Различает случаи ровно лишняя
     * строка: без неё «ровно поместилось» и «не поместилось» дали бы
     * одинаковый размер результата.
     */
    @Test
    void flowWindowHittingItsCeilingForbidsComputation() {
        stubTruncationSafeGraph();
        when(dealCashFlowDataService.findByDealWindow(DEAL_ID, FLOW_LIMIT)).thenReturn(flows(FLOW_LIMIT + 1));

        DealContext context = service.build(fetchedDeal());

        assertThat(context.getGraphComplete()).isTrue();
        assertThat(context.getFlowsComplete()).isFalse();
        assertThat(context.getComputationAllowed()).isFalse();
    }

    /** Выборка, поместившаяся ровно в потолок, полна: границу не пересекли. */
    @Test
    void flowWindowFilledToTheBrimStaysComplete() {
        stubTruncationSafeGraph();
        when(dealCashFlowDataService.findByDealWindow(DEAL_ID, FLOW_LIMIT)).thenReturn(flows(FLOW_LIMIT));

        DealContext context = service.build(fetchedDeal());

        assertThat(context.getFlowsComplete()).isTrue();
        assertThat(context.getComputationAllowed()).isTrue();
    }

    /**
     * Добытость — собственный конъюнкт полноты движений, и он НЕ входит в
     * гейт начала расчёта. Разведение несущее: гейт спрашивает,
     * предъявлено ли целиком, а не выполнялась ли добыча, — иначе первый
     * же проход до звена добычи запрещал бы расчёт всему, что от движений
     * не зависит.
     */
    @Test
    void fetchedMarkIsOwnConjunctAndDoesNotGateComputation() {
        stubTruncationSafeGraph();
        Deal deal = fetchedDeal();
        deal.setBillsFetchedThrough(null);

        DealContext context = service.build(deal);

        assertThat(context.getFlowsComplete()).isFalse();
        assertThat(context.getComputationAllowed()).isTrue();
    }

    /**
     * Пустой список траншей — дефект загрузки даже на тропе без входа:
     * удостоверителя у этой пустоты нет ни одного, а по траншам обходом
     * собираются ноги.
     */
    @Test
    void emptyTranchesAreLoadingDefectEvenWithoutEntry() {
        DealContext context = service.build(fetchedDeal());

        assertThat(context.getGraphComplete()).isFalse();
        assertThat(context.getComputationAllowed()).isFalse();
    }

    /**
     * Позиция наблюдалась, а эпизодов нет — недогруз. Пустые эпизоды
     * обнулили бы результат сделки и правые операнды пар сверки, то есть
     * ошиблись бы в разрешающую сторону.
     */
    @Test
    void episodesAreRequiredOncePositionWasObserved() {
        when(dealTrancheDataService.findByDealId(DEAL_ID)).thenReturn(List.of(filledTranche()));
        when(orderDataService.findByDealId(DEAL_ID)).thenReturn(List.of(leg(1L, 21L)));

        DealContext context = service.build(fetchedDeal());

        assertThat(context.getGraphComplete()).isFalse();
    }

    /**
     * Вход отправлялся, а ног нет — недогруз. Удостоверитель здесь
     * durable-колонка сделки, а не сама коллекция ног: резолв «вход был,
     * потому что в ногах есть входная заявка» вернул бы вакуумную истину,
     * ради снятия которой удостоверитель и заведён.
     */
    @Test
    void legsAreRequiredOnceEntryWasSubmitted() {
        when(dealTrancheDataService.findByDealId(DEAL_ID)).thenReturn(List.of(emptyTranche()));
        Deal deal = fetchedDeal();
        deal.setBillsWindowBegin(MOMENT);

        DealContext context = service.build(deal);

        assertThat(context.getGraphComplete()).isFalse();
    }

    /**
     * Восстановленная сделка: закреплять было нечего, входной заявки не
     * было никогда, а эпизод есть. Контекст полон, и деталь не
     * запрашивается вовсе — требование детали оставило бы её живой риск
     * без единого прохода.
     */
    @Test
    void recoveredDealIsCompleteWithoutPinnedDetailAndWithoutLegs() {
        when(dealTrancheDataService.findByDealId(DEAL_ID)).thenReturn(List.of(emptyTranche()));
        when(positionDataService.findEpisodes(DEAL_ID)).thenReturn(List.of(new Position()));
        Deal deal = fetchedDeal();
        deal.setStrategyDetailId(null);

        DealContext context = service.build(deal);

        assertThat(context.getStrategyDetail()).isNull();
        assertThat(context.getGraphComplete()).isTrue();
        verify(strategyDataService, never()).getRequiredDetailByIdWithTree(anyLong());
    }

    /**
     * Ноги всех траншей читаются ОДНИМ запросом и раскладываются в
     * памяти. Запрос на транш отработал бы на тесте так же, а на сетке из
     * N траншей дал бы N+1 обращений — дефект, видимый только счётом
     * запросов.
     */
    @Test
    void legsOfAllTranchesAreReadByOneQueryAndLaidOutInMemory() {
        DealTranche first = filledTranche();
        DealTranche second = terminalTranche();
        when(dealTrancheDataService.findByDealId(DEAL_ID)).thenReturn(List.of(first, second));
        when(orderDataService.findByDealId(DEAL_ID))
                .thenReturn(List.of(leg(1L, first.getId()), leg(2L, second.getId()), leg(3L, null)));
        when(positionDataService.findEpisodes(DEAL_ID)).thenReturn(List.of(new Position()));

        DealContext context = service.build(fetchedDeal());

        verify(orderDataService, times(1)).findByDealId(DEAL_ID);
        assertThat(context.getDeal().getTranches()).hasSize(2);
        assertThat(first.getOrders()).extracting(Order::getId).containsExactly(1L);
        assertThat(second.getOrders()).extracting(Order::getId).containsExactly(2L);
    }

    /**
     * Терминальный транш из графа не вычёркивается: по ногам ВСЕЙ сделки
     * считаются числа риска и экспозиция, а фильтр «нетерминальные»
     * опустошал бы обе коллекции ровно в точке расчёта.
     */
    @Test
    void terminalTrancheStaysInGraph() {
        DealTranche terminal = terminalTranche();
        when(dealTrancheDataService.findByDealId(DEAL_ID)).thenReturn(List.of(terminal));
        when(positionDataService.findEpisodes(DEAL_ID)).thenReturn(List.of(new Position()));
        when(orderDataService.findByDealId(DEAL_ID)).thenReturn(List.of(leg(1L, terminal.getId())));

        DealContext context = service.build(fetchedDeal());

        assertThat(context.getDeal().getTranches()).containsExactly(terminal);
        assertThat(context.getGraphComplete()).isTrue();
    }

    /** Граф, на котором полнота истинна: транш с наливом, его эпизод и его нога. */
    private void stubTruncationSafeGraph() {
        DealTranche tranche = filledTranche();
        when(dealTrancheDataService.findByDealId(DEAL_ID)).thenReturn(List.of(tranche));
        when(positionDataService.findEpisodes(DEAL_ID)).thenReturn(List.of(new Position()));
        when(orderDataService.findByDealId(DEAL_ID)).thenReturn(List.of(leg(1L, tranche.getId())));
    }

    private Deal fetchedDeal() {
        Deal deal = new Deal();
        deal.setId(DEAL_ID);
        deal.setExchangeAccountId(ACCOUNT_ID);
        deal.setInstrumentId(INSTRUMENT_ID);
        deal.setStrategyDetailId(DETAIL_ID);
        deal.setEntryReason(Deal.EntryReason.STRATEGY);
        deal.setBillsFetchedThrough(MOMENT);
        return deal;
    }

    private ExchangeAccount account() {
        ExchangeAccount account = new ExchangeAccount();
        account.setId(ACCOUNT_ID);
        return account;
    }

    private DealTranche filledTranche() {
        DealTranche tranche = emptyTranche();
        tranche.setEntryFilled(BigDecimal.ONE);
        return tranche;
    }

    private DealTranche terminalTranche() {
        DealTranche tranche = new DealTranche();
        tranche.setId(22L);
        tranche.setDealId(DEAL_ID);
        tranche.setStatus(DealTranche.Status.CLOSED);
        tranche.setEntryFilled(BigDecimal.ONE);
        return tranche;
    }

    private DealTranche emptyTranche() {
        DealTranche tranche = new DealTranche();
        tranche.setId(21L);
        tranche.setDealId(DEAL_ID);
        tranche.setStatus(DealTranche.Status.MANAGING);
        return tranche;
    }

    private Order leg(Long id, Long trancheId) {
        Order order = new Order();
        order.setId(id);
        order.setDealId(DEAL_ID);
        order.setDealTrancheId(trancheId);
        return order;
    }

    private List<DealCashFlow> flows(Integer count) {
        return IntStream.range(0, count)
                .mapToObj(index -> new DealCashFlow())
                .toList();
    }
}
