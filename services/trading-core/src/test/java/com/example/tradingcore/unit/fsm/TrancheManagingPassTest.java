package com.example.tradingcore.unit.fsm;

import static com.example.tradingcore.unit.fsm.DealActiveHarness.command;
import static com.example.tradingcore.unit.fsm.FsmFixture.DECLARATION_ID;
import static com.example.tradingcore.unit.fsm.FsmFixture.TRANCHE_ID;
import static com.example.tradingcore.unit.fsm.FsmFixture.attachedProtection;
import static com.example.tradingcore.unit.fsm.FsmFixture.contextBuilder;
import static com.example.tradingcore.unit.fsm.FsmFixture.deal;
import static com.example.tradingcore.unit.fsm.FsmFixture.declaration;
import static com.example.tradingcore.unit.fsm.FsmFixture.detail;
import static com.example.tradingcore.unit.fsm.FsmFixture.filledEntryLeg;
import static com.example.tradingcore.unit.fsm.FsmFixture.fills;
import static com.example.tradingcore.unit.fsm.FsmFixture.liveEntryLeg;
import static com.example.tradingcore.unit.fsm.FsmFixture.livePosition;
import static com.example.tradingcore.unit.fsm.FsmFixture.tranche;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.ServiceCommand;
import com.example.tradingcore.domain.command.ServiceCommandType;
import com.example.tradingcore.domain.fsm.TrancheTransition;
import com.example.tradingcore.domain.fsm.tranche.TrancheManagingHandler;
import com.example.tradingcore.util.Constants;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Сопровождение — группа {@code U20} документа
 * `.claude/tests/cases/trading-core-fsm.md` (дом —
 * docs/components/TrancheManagingHandler.md).
 *
 * <p><b>Базовая сборка.</b> Сделка {@code ACTIVE}; транш {@code MANAGING}
 * с объявлением, ненулевой экспозицией и покрытием; чужого риска нет,
 * эпизод один; рабочий блок подменён и молчит.
 *
 * <p><b>Наблюдение нулевой экспозиции ветвится ОБЪЯВЛЕНИЕМ:</b> пустое
 * объявление читается как запрет переоткрытия — разрешающее прочтение
 * отправило бы транш открывать риск, которого никто не объявлял.
 */
class TrancheManagingPassTest {

    private final TrancheHarness harness = new TrancheHarness();

    @Test
    @DisplayName("U20.1 — базовая сборка: работы нет, ребра нет")
    void u20_1_theBaseStateLeavesThePassEmpty() {
        TrancheTransition transition = handle(coveredContext());

        assertThat(transition.movesStatus()).isFalse();
        assertThat(transition.hasCommands()).isFalse();
        assertThat(transition.getDealErrorRequested()).isFalse();
    }

    @Test
    @DisplayName("U20.2 — чужой живой риск либо второй эпизод: просьба ошибочной тропы")
    void u20_2_aForeignLiveRiskEscalates() {
        DealContext context = coveredContext();
        context.getDeal().getOrders().add(liveEntryLeg(90L, null));

        assertThat(handle(context).getDealErrorRequested()).isTrue();
    }

    @Test
    @DisplayName("U20.3 — сделка сворачивается: экспозиция и покрытие не спрашиваются")
    void u20_3_aCollapsingDealSendsTheTrancheToItsExit() {
        DealTranche subject = coveredTranche();
        DealContext context = contextBuilder(dealWithEpisode(Deal.Status.EXIT_PENDING, subject)).build();

        TrancheTransition transition = handle(context);

        assertThat(transition.getNextStatus()).isEqualTo(DealTranche.Status.EXIT_PENDING);
        harness.verifyCoverageGateNotAsked();
    }

    @Test
    @DisplayName("U20.4 — экспозиция схлопнулась, переоткрытие разрешено, нога жива: ребро переоткрытия")
    void u20_4_aCollapsedExposureWithAPermittedReopenGoesBackToTheEntry() {
        DealTranche subject = tranche(TRANCHE_ID, DealTranche.Status.MANAGING);
        subject.getOrders().add(liveEntryLeg(30L, TRANCHE_ID));

        assertThat(handle(contextOf(subject, Boolean.TRUE)).getNextStatus())
                .isEqualTo(DealTranche.Status.ENTRY_SUBMITTED);
    }

    @Test
    @DisplayName("U20.5 — экспозиция схлопнулась, переоткрытие запрещено: ребро в выход транша")
    void u20_5_aCollapsedExposureWithAForbiddenReopenGoesToTheExit() {
        DealTranche subject = tranche(TRANCHE_ID, DealTranche.Status.MANAGING);
        subject.getOrders().add(liveEntryLeg(30L, TRANCHE_ID));

        assertThat(handle(contextOf(subject, Boolean.FALSE)).getNextStatus())
                .isEqualTo(DealTranche.Status.EXIT_PENDING);
    }

    @Test
    @DisplayName("U20.6 — переоткрытие разрешено, живой входной ноги нет: ребро в выход транша")
    void u20_6_aCollapsedExposureWithoutALiveLegGoesToTheExit() {
        DealTranche subject = tranche(TRANCHE_ID, DealTranche.Status.MANAGING);

        assertThat(handle(contextOf(subject, Boolean.TRUE)).getNextStatus())
                .isEqualTo(DealTranche.Status.EXIT_PENDING);
    }

    @Test
    @DisplayName("U20.7 — транш без объявления при нулевой экспозиции: пустота читается как запрет")
    void u20_7_aRecoveredTrancheGoesToTheExit() {
        DealTranche subject = tranche(TRANCHE_ID, DealTranche.Status.MANAGING);
        subject.setStrategyTrancheId(null);
        subject.getOrders().add(liveEntryLeg(30L, TRANCHE_ID));

        assertThat(handle(contextOf(subject, Boolean.TRUE)).getNextStatus())
                .isEqualTo(DealTranche.Status.EXIT_PENDING);
    }

    @Test
    @DisplayName("U20.8 — экспозиция ненулевая, покрытия и обязательства нет: ошибочная тропа со ступенью")
    void u20_8_anUncoveredExposureEscalatesWithTheAccountRung() {
        DealTranche subject = exposedTranche();

        TrancheTransition transition = handle(contextOf(subject, Boolean.FALSE));

        assertThat(transition.getDealErrorRequested()).isTrue();
        assertThat(transition.getHoldSignal().getCode())
                .isEqualTo(Constants.Hold.EXCHANGE_LIVE_RISK_UNCOVERED);
    }

    @Test
    @DisplayName("U20.9 — экспозиция ноль, покрытия нет: наблюдение покрытия не срабатывает")
    void u20_9_aZeroExposureSkipsTheCoverageObservation() {
        DealTranche subject = tranche(TRANCHE_ID, DealTranche.Status.MANAGING);

        TrancheTransition transition = handle(contextOf(subject, Boolean.FALSE));

        assertThat(transition.getNextStatus()).isEqualTo(DealTranche.Status.EXIT_PENDING);
        assertThat(transition.getDealErrorRequested()).isFalse();
        harness.verifyCoverageGateNotAsked();
    }

    @Test
    @DisplayName("U20.10 — рабочий блок что-то сказал: его исход возвращён как есть")
    void u20_10_theWorkBlockOutcomeIsReturnedAsIs() {
        harness.givenWork(TrancheTransition.command(command(ServiceCommandType.CREATE_ALGO_ORDER_COMMAND)));

        TrancheTransition transition = handle(coveredContext());

        assertThat(commandTypes(transition)).containsExactly(ServiceCommandType.CREATE_ALGO_ORDER_COMMAND);
    }

    @Test
    @DisplayName("U20.11 — транш без объявления при ненулевой экспозиции: проверки на фактах действуют")
    void u20_11_aRecoveredTrancheStillFailsTheCoverageObservation() {
        DealTranche subject = exposedTranche();
        subject.setStrategyTrancheId(null);

        TrancheTransition transition = handle(contextOf(subject, Boolean.FALSE));

        assertThat(transition.getDealErrorRequested()).isTrue();
        assertThat(transition.getHoldSignal().getCode())
                .isEqualTo(Constants.Hold.EXCHANGE_LIVE_RISK_UNCOVERED);
    }

    // --- сборка ------------------------------------------------------------

    private TrancheTransition handle(DealContext context) {
        TrancheManagingHandler handler = harness.managing();
        return handler.handle(context, context.getDeal().getTranches().getFirst());
    }

    /** Сопровождение с экспозицией 5 и покрытием встроенной защитой. */
    private DealContext coveredContext() {
        return contextOf(coveredTranche(), Boolean.FALSE);
    }

    private DealTranche coveredTranche() {
        DealTranche subject = exposedTranche();
        Order entry = subject.getOrders().getFirst();
        entry.getAttachedAlgoOrders().add(attachedProtection(60L, "5"));
        return subject;
    }

    /** Сопровождение с экспозицией 5 и налитой входной ногой. */
    private DealTranche exposedTranche() {
        DealTranche subject = fills(tranche(TRANCHE_ID, DealTranche.Status.MANAGING), "5", "0");
        subject.getOrders().add(filledEntryLeg(30L, TRANCHE_ID, "5"));
        return subject;
    }

    private DealContext contextOf(DealTranche subject, Boolean reopenAllowed) {
        return contextBuilder(dealWithEpisode(Deal.Status.ACTIVE, subject))
                .strategyDetail(detail(declaration(DECLARATION_ID, reopenAllowed)))
                .build();
    }

    private Deal dealWithEpisode(Deal.Status status, DealTranche subject) {
        Deal active = deal(status, subject);
        active.getPositions().add(livePosition("5"));
        return active;
    }

    private List<ServiceCommandType> commandTypes(TrancheTransition transition) {
        return transition.getCommands().stream().map(ServiceCommand::getType).toList();
    }
}
