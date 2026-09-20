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
import static com.example.tradingcore.unit.fsm.FsmFixture.livePosition;
import static com.example.tradingcore.unit.fsm.FsmFixture.protection;
import static com.example.tradingcore.unit.fsm.FsmFixture.protectiveAction;
import static com.example.tradingcore.unit.fsm.FsmFixture.step;
import static com.example.tradingcore.unit.fsm.FsmFixture.strategyRow;
import static com.example.tradingcore.unit.fsm.FsmFixture.tranche;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStepType;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAction;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingcore.domain.command.DealActionStateStatus;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.ServiceCommand;
import com.example.tradingcore.domain.command.ServiceCommandType;
import com.example.tradingcore.domain.fsm.TrancheTransition;
import com.example.tradingcore.domain.safety.HoldRung;
import com.example.tradingcore.domain.safety.HoldScope;
import com.example.tradingcore.util.Constants;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Подтверждённый вход и переключение защиты — группа {@code U19}
 * документа `.claude/tests/cases/trading-core-fsm.md` (дом —
 * docs/components/TrancheEntryFinalizedHandler.md и
 * docs/components/TrancheProtectionSwitchedHandler.md).
 *
 * <p><b>Базовая сборка.</b> Сделка {@code ACTIVE}; транш
 * {@code ENTRY_FINALIZED} с объявлением, наливом входа и живым эпизодом;
 * экспозиция ненулевая, покрытие есть; гейт покрытия настоящий; рабочий
 * блок подменён и молчит.
 *
 * <p><b>Кейсы {@code U19.15} и {@code U19.16} не прогоняются</b>: два
 * обработчика держат коллаборатор диспозиции и не зовут его ни на одной
 * тропе (находка {@code F-4}) — «команды нет» верно и у них, и у
 * обработчика, где вызов вырезали бы завтра.
 *
 * <p><b>Вход {@code U19.12} уточнён заходом кода:</b> «покрытие не
 * сошлось, риска нет» собирается только как «охрана потери защиты не
 * сработала — основная защита стои́т, а покрытия её не хватает»;
 * непокрытая экспозиция по построению {@code isRiskBearing} истинна.
 */
class TrancheEntryFinalizedPassTest {

    private final TrancheHarness harness = new TrancheHarness();

    @Test
    @DisplayName("U19.1 — основной защиты нет, встроенная жива: ребро в сопровождение")
    void u19_1_anAttachedOnlyProtectionGoesStraightToManaging() {
        TrancheTransition transition = handleFinalized(coveredContext());

        assertThat(transition.getNextStatus()).isEqualTo(DealTranche.Status.MANAGING);
    }

    @Test
    @DisplayName("U19.2 — основная защита стои́т, встроенная жива: ребро в статус переключения")
    void u19_2_aStandaloneNextToAnAttachedProtectionSwitches() {
        DealContext context = coveredContext();
        context.getDeal().getTranches().getFirst().getAlgoOrders().add(protection(40L, TRANCHE_ID, "5"));

        TrancheTransition transition = handleFinalized(context);

        assertThat(transition.getNextStatus()).isEqualTo(DealTranche.Status.PROTECTION_SWITCHED);
    }

    @Test
    @DisplayName("U19.3 — основная защита стои́т, встроенной больше нет: переключение уже состоялось")
    void u19_3_aStandaloneWithoutAnAttachedProtectionGoesToManaging() {
        DealTranche subject = finalizedTranche();
        subject.getAlgoOrders().add(protection(40L, TRANCHE_ID, "5"));
        DealContext context = contextOf(subject);

        TrancheTransition transition = handleFinalized(context);

        assertThat(transition.getNextStatus()).isEqualTo(DealTranche.Status.MANAGING);
    }

    @Test
    @DisplayName("U19.4 — покрытия нет, живое обязательство есть: ход продолжается")
    void u19_4_aLiveCoverageCommitmentKeepsThePassQuiet() {
        StrategyAction protective = protectiveAction(5L);
        DealTranche subject = finalizedTranche();
        DealContext context = contextBuilder(dealWithEpisode(subject))
                .strategyDetail(detail(declaration(DECLARATION_ID, Boolean.FALSE,
                        DealTranche.Status.ENTRY_FINALIZED, step(1L, StrategyStepType.MAIN_PROTECTION, protective))))
                .actionStates(List.of(strategyRow(5L, TRANCHE_ID, 1, DealActionStateStatus.SUBMITTED)))
                .build();

        TrancheTransition transition = handleFinalized(context);

        assertThat(transition.movesStatus()).isFalse();
        assertThat(transition.getDealErrorRequested()).isFalse();
    }

    @Test
    @DisplayName("U19.5 — покрытия нет и обязательства нет: ошибочная тропа вместе со ступенью")
    void u19_5_anUncoveredExposureEscalatesWithTheAccountRung() {
        DealContext context = contextOf(finalizedTranche());

        TrancheTransition transition = handleFinalized(context);

        assertThat(transition.getDealErrorRequested()).isTrue();
        assertThat(transition.getHoldSignal().getScope()).isEqualTo(HoldScope.EXCHANGE_ACCOUNT);
        assertThat(transition.getHoldSignal().getRung()).isEqualTo(HoldRung.HARD);
        assertThat(transition.getHoldSignal().getCode())
                .isEqualTo(Constants.Hold.EXCHANGE_LIVE_RISK_UNCOVERED);
    }

    @Test
    @DisplayName("U19.6 — объявления у транша нет: гейт покрытия не спрашивался")
    void u19_6_anAbsentDeclarationEscalatesBeforeTheCoverageGate() {
        DealTranche subject = finalizedTranche();
        subject.setStrategyTrancheId(null);

        TrancheTransition transition = handleFinalized(contextOf(subject));

        assertThat(transition.getDealErrorRequested()).isTrue();
        harness.verifyCoverageGateNotAsked();
    }

    @Test
    @DisplayName("U19.7 — живого эпизода нет: подтверждённый вход без экспозиции невозможен")
    void u19_7_aFinalizedEntryWithoutALiveEpisodeEscalates() {
        DealContext context = contextBuilder(deal(Deal.Status.ACTIVE, finalizedTranche())).build();

        assertThat(handleFinalized(context).getDealErrorRequested()).isTrue();
    }

    @Test
    @DisplayName("U19.8 — рабочий блок что-то сказал: его исход возвращён как есть")
    void u19_8_theWorkBlockOutcomeIsReturnedAsIs() {
        harness.givenWork(TrancheTransition.command(command(ServiceCommandType.CREATE_ALGO_ORDER_COMMAND)));

        TrancheTransition transition = handleFinalized(coveredContext());

        assertThat(commandTypes(transition)).containsExactly(ServiceCommandType.CREATE_ALGO_ORDER_COMMAND);
        assertThat(transition.movesStatus()).isFalse();
    }

    @Test
    @DisplayName("U19.9 — переключение, риск живой, основной защиты нет: защита потеряна")
    void u19_9_aLostStandaloneProtectionEscalatesWithTheAccountRung() {
        DealContext context = contextOf(switchedTranche());

        TrancheTransition transition = handleSwitched(context);

        assertThat(transition.getDealErrorRequested()).isTrue();
        assertThat(transition.getHoldSignal().getCode())
                .isEqualTo(Constants.Hold.EXCHANGE_LIVE_RISK_UNCOVERED);
    }

    @Test
    @DisplayName("U19.10 — переключение, живого эпизода нет и риска нет: безопасный ход вперёд")
    void u19_10_aSwitchingTrancheWithoutRiskGoesToManaging() {
        DealTranche subject = tranche(TRANCHE_ID, DealTranche.Status.PROTECTION_SWITCHED);
        DealContext context = contextBuilder(deal(Deal.Status.ACTIVE, subject)).build();

        assertThat(handleSwitched(context).getNextStatus()).isEqualTo(DealTranche.Status.MANAGING);
    }

    @Test
    @DisplayName("U19.11 — переключение, покрытие сошлось: ребро в сопровождение")
    void u19_11_aCoveredSwitchingTrancheGoesToManaging() {
        DealTranche subject = switchedTranche();
        subject.getAlgoOrders().add(protection(40L, TRANCHE_ID, "5"));

        assertThat(handleSwitched(contextOf(subject)).getNextStatus())
                .isEqualTo(DealTranche.Status.MANAGING);
    }

    @Test
    @DisplayName("U19.12 — переключение, покрытие не сошлось при стоящей основной защите: переход пустой")
    void u19_12_anUnderCoveredSwitchingTrancheStaysPut() {
        DealTranche subject = switchedTranche();
        subject.getAlgoOrders().add(protection(40L, TRANCHE_ID, "3"));

        TrancheTransition transition = handleSwitched(contextOf(subject));

        assertThat(subject.isCovered()).isFalse();
        assertThat(transition.movesStatus()).isFalse();
        assertThat(transition.getDealErrorRequested()).isFalse();
    }

    @Test
    @DisplayName("U19.13 — переключение, эпизодов два: просьба ошибочной тропы")
    void u19_13_twoLiveEpisodesEscalateFromTheSwitchingStatus() {
        DealContext context = contextOf(switchedTranche());
        context.getDeal().getPositions().add(livePosition("5"));

        assertThat(handleSwitched(context).getDealErrorRequested()).isTrue();
    }

    @Test
    @DisplayName("U19.14 — любой вход: точечной отмены встроенной защиты не эмитится ни на одной тропе")
    void u19_14_theAttachedProtectionCancelIsNeverEmitted() {
        List<TrancheTransition> passes = List.of(
                handleSwitched(contextOf(switchedTranche())),
                handleSwitched(contextOf(switchedWithStandalone())),
                handleSwitched(contextBuilder(deal(Deal.Status.ACTIVE,
                        tranche(TRANCHE_ID, DealTranche.Status.PROTECTION_SWITCHED))).build()));

        assertThat(passes.stream().flatMap(pass -> commandTypes(pass).stream()).toList())
                .doesNotContain(ServiceCommandType.CANCEL_ATTACHED_PROTECTION_COMMAND);
    }

    // --- сборка ------------------------------------------------------------

    private TrancheTransition handleFinalized(DealContext context) {
        return harness.entryFinalized().handle(context, context.getDeal().getTranches().getFirst());
    }

    private TrancheTransition handleSwitched(DealContext context) {
        return harness.protectionSwitched().handle(context, context.getDeal().getTranches().getFirst());
    }

    /** Подтверждённый вход с покрытием встроенной защитой. */
    private DealContext coveredContext() {
        DealTranche subject = finalizedTranche();
        Order entry = subject.getOrders().getFirst();
        entry.getAttachedAlgoOrders().add(attachedProtection(60L, "5"));
        return contextOf(subject);
    }

    /** Транш подтверждённого входа: налив 5, экспозиция 5, защит нет. */
    private DealTranche finalizedTranche() {
        DealTranche subject = fills(tranche(TRANCHE_ID, DealTranche.Status.ENTRY_FINALIZED), "5", "0");
        subject.getOrders().add(filledEntryLeg(30L, TRANCHE_ID, "5"));
        return subject;
    }

    /** Транш переключения защиты: та же экспозиция, защит нет. */
    private DealTranche switchedTranche() {
        DealTranche subject = fills(tranche(TRANCHE_ID, DealTranche.Status.PROTECTION_SWITCHED), "5", "0");
        subject.getOrders().add(filledEntryLeg(30L, TRANCHE_ID, "5"));
        return subject;
    }

    private DealTranche switchedWithStandalone() {
        DealTranche subject = switchedTranche();
        subject.getAlgoOrders().add(protection(40L, TRANCHE_ID, "5"));
        return subject;
    }

    private DealContext contextOf(DealTranche subject) {
        return contextBuilder(dealWithEpisode(subject)).build();
    }

    private Deal dealWithEpisode(DealTranche subject) {
        Deal active = deal(Deal.Status.ACTIVE, subject);
        active.getPositions().add(livePosition("5"));
        return active;
    }

    private List<ServiceCommandType> commandTypes(TrancheTransition transition) {
        return transition.getCommands().stream().map(ServiceCommand::getType).toList();
    }
}
