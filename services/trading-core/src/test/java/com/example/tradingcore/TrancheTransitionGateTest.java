package com.example.tradingcore;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyDetail;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyTranche;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.fsm.TrancheTransitionGate;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Матрица статусных рёбер транша — исполнимая форма
 * docs/spec/deal-tranche-lifecycle.json.
 *
 * <p><b>Предмет — направление ошибки, а не наличие метода.</b> Каждый
 * случай ниже — состояние, на котором незащищённый переход прошёл бы
 * зелёным: вход посреди сворачивания сделки набирает риск, который она
 * уже снимает; терминал на недогруженном графе закрывает транш, у
 * которого обработчика больше нет, оставив заявку на бирже.
 *
 * <p>Состояние собирается настоящими полями — статусами ног, наливом,
 * объявлением — и предикаты считаются сами
 * (.claude/rules/codestyle.md §«Тесты доменных моделей»).
 */
class TrancheTransitionGateTest {

    private static final Long DECLARATION_ID = 11L;

    private final TrancheTransitionGate gate = new TrancheTransitionGate();

    /** Штатная тропа: предвходовая проверка ведёт к отправке входа. */
    @Test
    void precheckLeadsToSubmittedEntry() {
        DealTranche tranche = tranche(DealTranche.Status.PRECHECK);
        DealContext context = context(Deal.Status.ACTIVE, tranche, false);

        assertThat(gate.transitionAllowed(context, tranche, DealTranche.Status.ENTRY_SUBMITTED)).isTrue();
    }

    /** Ошибочного статуса и аварийного терминала у транша нет: рёбер в них не существует. */
    @Test
    void errorAndEmergencyEdgesDoNotExist() {
        assertThat(gate.edgeDeclared(DealTranche.Status.MANAGING, DealTranche.Status.PRECHECK)).isFalse();
        assertThat(gate.edgeDeclared(DealTranche.Status.ENTRY_SUBMITTED, DealTranche.Status.MANAGING)).isFalse();
        assertThat(gate.edgeDeclared(DealTranche.Status.CLOSED, DealTranche.Status.MANAGING)).isFalse();
    }

    /**
     * Вход из предвходовой проверки посреди сворачивания сделки не
     * выпускается, хотя ребро объявлено и вне сворачивания законно.
     */
    @Test
    void entryUnderCollapseIsRefused() {
        DealTranche tranche = tranche(DealTranche.Status.PRECHECK);
        DealContext context = context(Deal.Status.EXIT_PENDING, tranche, false);

        assertThat(gate.edgeDeclared(DealTranche.Status.PRECHECK, DealTranche.Status.ENTRY_SUBMITTED)).isTrue();
        assertThat(gate.transitionAllowed(context, tranche, DealTranche.Status.ENTRY_SUBMITTED)).isFalse();
    }

    /**
     * Переоткрытие посреди сворачивания не выпускается тоже — вторая тропа
     * того же ребра-цели.
     *
     * <p>Пара примеров доказывает, что энфорсер стои́т на ЦЕЛИ перехода, а
     * не на одном из двух рёбер: здесь переоткрытие разрешено объявлением,
     * экспозиция схлопнулась и живая входная нога есть.
     */
    @Test
    void reopenUnderCollapseIsRefusedToo() {
        DealTranche tranche = tranche(DealTranche.Status.MANAGING);
        tranche.setEntryFilled(new BigDecimal("5"));
        tranche.setReduceOnlyFilled(new BigDecimal("5"));
        tranche.setOrders(new ArrayList<>(List.of(liveEntryOrder())));
        DealContext context = context(Deal.Status.EXIT_PENDING, tranche, true);

        assertThat(gate.reopenPermitted(context, tranche)).isTrue();
        assertThat(gate.transitionAllowed(context, tranche, DealTranche.Status.ENTRY_SUBMITTED)).isFalse();
    }

    /** Терминал посреди сворачивания энфорсер не запирает: он гасит только набор риска. */
    @Test
    void terminalUnderCollapseIsAllowed() {
        DealTranche tranche = tranche(DealTranche.Status.PRECHECK);
        DealContext context = context(Deal.Status.EXIT_PENDING, tranche, false);

        assertThat(gate.transitionAllowed(context, tranche, DealTranche.Status.CLOSED)).isTrue();
    }

    /** Переоткрытие запрещено объявлением: то же ребро не применяется. */
    @Test
    void reopenForbiddenByDeclaration() {
        DealTranche tranche = tranche(DealTranche.Status.MANAGING);
        tranche.setOrders(new ArrayList<>(List.of(liveEntryOrder())));
        DealContext context = context(Deal.Status.ACTIVE, tranche, false);

        assertThat(gate.reopenPermitted(context, tranche)).isFalse();
        assertThat(gate.transitionAllowed(context, tranche, DealTranche.Status.ENTRY_SUBMITTED)).isFalse();
    }

    /** Переоткрытия нет при живой экспозиции, даже когда объявление разрешает. */
    @Test
    void reopenNeedsCollapsedExposure() {
        DealTranche tranche = tranche(DealTranche.Status.MANAGING);
        tranche.setEntryFilled(new BigDecimal("5"));
        tranche.setOrders(new ArrayList<>(List.of(liveEntryOrder())));
        DealContext context = context(Deal.Status.ACTIVE, tranche, true);

        assertThat(gate.reopenPermitted(context, tranche)).isFalse();
    }

    /** Терминал при живой отдельной защите не применяется: экспозиции нет, условная заявка жива. */
    @Test
    void terminalRefusedWhileStandaloneProtectionIsLive() {
        DealTranche tranche = tranche(DealTranche.Status.EXIT_PENDING);
        tranche.setAlgoOrders(new ArrayList<>(List.of(liveStop())));
        DealContext context = context(Deal.Status.ACTIVE, tranche, false);

        assertThat(gate.terminalContract(tranche, true)).isFalse();
        assertThat(gate.transitionAllowed(context, tranche, DealTranche.Status.CLOSED)).isFalse();
    }

    /**
     * Терминал при живой ВСТРОЕННОЙ защите не применяется тоже.
     *
     * <p>Случай направленный: родитель уже терминален, экспозиция нулевая,
     * отдельной защиты нет — то есть все прочие операнды говорят «риска
     * нет». Встроенная защита при непустом наливе родителя живёт на бирже
     * самостоятельной заявкой и переживает его терминал; читайся живой
     * риск одними лишь отдельными заявками, транш ушёл бы в {@code CLOSED}
     * со стопом на бирже, а обработчика у него больше нет.
     */
    @Test
    void terminalRefusedWhileAttachedProtectionIsLive() {
        Order filledParent = new Order();
        filledParent.setId(31L);
        filledParent.setStatus(Order.Status.COMPLETED);
        filledParent.setCloseReason(Order.CloseReason.FILLED);
        filledParent.setAccumulatedFillSize(new BigDecimal("5"));
        filledParent.setPositionReducingOnly(Boolean.FALSE);
        AttachedAlgoOrder protection = new AttachedAlgoOrder();
        protection.setId(41L);
        protection.setStatus(AttachedAlgoOrder.Status.ACTIVE);
        protection.setSize(new BigDecimal("5"));
        filledParent.setAttachedAlgoOrders(new ArrayList<>(List.of(protection)));

        DealTranche tranche = tranche(DealTranche.Status.EXIT_PENDING);
        tranche.setEntryFilled(new BigDecimal("5"));
        tranche.setReduceOnlyFilled(new BigDecimal("5"));
        tranche.setOrders(new ArrayList<>(List.of(filledParent)));

        assertThat(tranche.exposure()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(tranche.hasLiveEntryOrder()).isFalse();
        assertThat(gate.terminalContract(tranche, true)).isFalse();
    }

    /** Терминал применяется, когда живого риска у транша нет. */
    @Test
    void terminalAppliesWithoutLiveRisk() {
        DealTranche tranche = tranche(DealTranche.Status.EXIT_PENDING);
        tranche.setEntryFilled(new BigDecimal("5"));
        tranche.setReduceOnlyFilled(new BigDecimal("5"));
        DealContext context = context(Deal.Status.ACTIVE, tranche, false);

        assertThat(gate.transitionAllowed(context, tranche, DealTranche.Status.CLOSED)).isTrue();
    }

    /**
     * Тот же терминал на НЕПОЛНОМ графе не применяется: заявки и защиты
     * загружены не целиком, и «живого риска нет» ложно молча.
     */
    @Test
    void terminalRefusedOnIncompleteGraph() {
        DealTranche tranche = tranche(DealTranche.Status.EXIT_PENDING);
        tranche.setEntryFilled(new BigDecimal("5"));
        tranche.setReduceOnlyFilled(new BigDecimal("5"));
        DealContext context = DealContext.builder()
                .deal(deal(Deal.Status.ACTIVE, tranche))
                .graphComplete(Boolean.FALSE)
                .build();

        assertThat(gate.terminalContract(tranche, false)).isFalse();
        assertThat(gate.transitionAllowed(context, tranche, DealTranche.Status.CLOSED)).isFalse();
    }

    // --- сборка состояния -----------------------------------------------

    private DealTranche tranche(DealTranche.Status status) {
        DealTranche tranche = new DealTranche();
        tranche.setId(21L);
        tranche.setDealId(7L);
        tranche.setStatus(status);
        tranche.setEpisodeSeq(1);
        tranche.setStrategyTrancheId(DECLARATION_ID);
        return tranche;
    }

    private Order liveEntryOrder() {
        Order order = new Order();
        order.setId(30L);
        order.setStatus(Order.Status.ACTIVE);
        order.setPositionReducingOnly(Boolean.FALSE);
        return order;
    }

    private AlgoOrder liveStop() {
        AlgoOrder algoOrder = new AlgoOrder();
        algoOrder.setId(40L);
        algoOrder.setStatus(AlgoOrder.Status.ACTIVE);
        algoOrder.setConditionType(AlgoOrder.ConditionType.STOP_LOSS);
        algoOrder.setSize(new BigDecimal("5"));
        return algoOrder;
    }

    private Deal deal(Deal.Status status, DealTranche tranche) {
        Deal deal = new Deal();
        deal.setId(7L);
        deal.setStatus(status);
        deal.setTranches(new ArrayList<>(List.of(tranche)));
        return deal;
    }

    private DealContext context(Deal.Status status, DealTranche tranche, boolean reopenAllowed) {
        StrategyTranche declaration = new StrategyTranche();
        declaration.setId(DECLARATION_ID);
        declaration.setPositionReopenAllowed(reopenAllowed);
        StrategyDetail detail = new StrategyDetail();
        detail.setTranches(new ArrayList<>(List.of(declaration)));
        return DealContext.builder()
                .deal(deal(status, tranche))
                .strategyDetail(detail)
                .graphComplete(Boolean.TRUE)
                .build();
    }
}
