package com.example.tradingcore.unit.fsm;

import static com.example.tradingcore.unit.fsm.FsmFixture.TRANCHE_ID;
import static com.example.tradingcore.unit.fsm.FsmFixture.attachedProtection;
import static com.example.tradingcore.unit.fsm.FsmFixture.contextBuilder;
import static com.example.tradingcore.unit.fsm.FsmFixture.deal;
import static com.example.tradingcore.unit.fsm.FsmFixture.exposed;
import static com.example.tradingcore.unit.fsm.FsmFixture.filledEntryLeg;
import static com.example.tradingcore.unit.fsm.FsmFixture.liveEntryLeg;
import static com.example.tradingcore.unit.fsm.FsmFixture.livePosition;
import static com.example.tradingcore.unit.fsm.FsmFixture.pairState;
import static com.example.tradingcore.unit.fsm.FsmFixture.tranche;
import static java.util.Objects.nonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.ServiceCommand;
import com.example.tradingcore.domain.command.ServiceCommandType;
import com.example.tradingcore.domain.command.SystemActionType;
import com.example.tradingcore.domain.fsm.DealTransition;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Активная сделка: входные проверки — группа {@code U7} документа
 * `.claude/tests/cases/trading-core-fsm.md` (дом —
 * docs/components/DealActiveHandler.md §«Входные проверки»).
 *
 * <p><b>Базовая сборка.</b> Сделка {@code ACTIVE} с одним траншем в
 * сопровождении; сумма экспозиций равна внешнему размеру живого эпизода;
 * живой эпизод один; чужого живого риска нет; каскад подменён и молчит;
 * исполнитель системных действий подменён и отдаёт пустоту.
 *
 * <p><b>Кейсы {@code U7.8} и {@code U7.9} пинят, что двух операндов
 * обработчик не читает</b> — дом развёл их по исполнителям (находка
 * {@code F-3} закрыта правкой дома): торгуемость читает преконтроль
 * действия, жёсткую ступень энфорсит петля до обработчика. Ожидание обоих
 * — исход базовой сборки.
 */
class DealActiveInputChecksTest {

    /** Сырой статус площадки у инструмента, чьи торги приостановлены. */
    private static final String SUSPENDED_EXCHANGE_STATE = "suspend";

    private final DealActiveHarness harness = new DealActiveHarness();

    @Test
    @DisplayName("U7.1 — базовая сборка: входные проверки пройдены, ступени проход не просит")
    void u7_1_theBaseStatePassesTheInputChecks() {
        DealTransition transition = harness.handle(baseContext());

        assertThat(transition.hasCommands()).isFalse();
        assertThat(transition.movesStatus()).isFalse();
        assertThat(transition.getHoldSignal()).isNull();
    }

    @Test
    @DisplayName("U7.2 — сумма экспозиций 1 при живом эпизоде 2: добыча позиции, ни работы, ни ступени")
    void u7_2_anExposureShortfallIsObservedAgainWithoutARung() {
        DealContext context = contextOf(exposedTranche("1"), livePosition("2"));

        assertReobserved(context, ServiceCommandType.REFRESH_POSITION_COMMAND);
    }

    @Test
    @DisplayName("U7.3 — сумма экспозиций 2 без живого эпизода: правая сторона сверки — ноль")
    void u7_3_anExposureWithoutALiveEpisodeIsObservedTheSameWay() {
        DealContext context = contextOf(exposedTranche("2"), null);

        assertReobserved(context, ServiceCommandType.REFRESH_POSITION_COMMAND);
    }

    @Test
    @DisplayName("U7.10 — расхождение при живой ноге транша: нога добывается первой, позиция последней")
    void u7_10_aLiveLegIsObservedBeforeThePosition() {
        DealTranche tranche = exposedTranche("1");
        tranche.getOrders().add(liveEntryLeg(30L, TRANCHE_ID));
        DealContext context = contextOf(tranche, livePosition("2"));

        assertReobserved(context, ServiceCommandType.REFRESH_ORDER_COMMAND,
                ServiceCommandType.REFRESH_POSITION_COMMAND);
    }

    @Test
    @DisplayName("U7.12 — расхождение при налитой ноге с живой встроенной защитой: носитель добывается до позиции")
    void u7_12_aFilledCarrierOfALiveProtectionIsObservedBeforeThePosition() {
        DealTranche tranche = exposedTranche("1");
        Order carrier = filledEntryLeg(30L, TRANCHE_ID, "1");
        carrier.getAttachedAlgoOrders().add(attachedProtection(60L, "1"));
        tranche.getOrders().add(carrier);
        DealContext context = contextOf(tranche, livePosition("2"));

        assertReobserved(context, ServiceCommandType.REFRESH_ORDER_COMMAND,
                ServiceCommandType.REFRESH_POSITION_COMMAND);
    }

    @Test
    @DisplayName("U7.11 — расхождение на неполном графе: позиция добывается первой, живая нога после")
    void u7_11_onAnIncompleteGraphThePositionIsObservedFirst() {
        DealTranche tranche = exposedTranche("1");
        tranche.getOrders().add(liveEntryLeg(30L, TRANCHE_ID));
        Deal active = deal(Deal.Status.ACTIVE, tranche);
        DealContext context = contextBuilder(active).graphComplete(Boolean.FALSE).build();

        assertReobserved(context, ServiceCommandType.REFRESH_POSITION_COMMAND,
                ServiceCommandType.REFRESH_ORDER_COMMAND);
    }

    @Test
    @DisplayName("U7.4 — живых эпизодов два: ошибочная тропа звеном аварийной финализации")
    void u7_4_twoLiveEpisodesTakeTheErrorPath() {
        DealContext context = contextOf(exposedTranche("2"), livePosition("2"));
        context.getDeal().getPositions().add(livePosition("2"));
        harness.givenSystemCommand(SystemActionType.FINALIZE_DEAL_ERROR_ACTION,
                ServiceCommandType.MARK_DEAL_ERROR_COMMAND);

        DealTransition transition = harness.handle(context);

        assertThat(commandTypes(transition)).containsExactly(ServiceCommandType.MARK_DEAL_ERROR_COMMAND);
        assertThat(transition.movesStatus()).isFalse();
    }

    @Test
    @DisplayName("U7.5 — живая заявка сделки, не приписанная траншу: ошибочная тропа тем же составом")
    void u7_5_anUnattributedLiveOrderTakesTheErrorPath() {
        DealContext context = contextOf(exposedTranche("2"), livePosition("2"));
        context.getDeal().getUnattributedOrders().add(liveEntryLeg(90L, null));
        harness.givenSystemCommand(SystemActionType.FINALIZE_DEAL_ERROR_ACTION,
                ServiceCommandType.MARK_DEAL_ERROR_COMMAND);

        DealTransition transition = harness.handle(context);

        assertThat(commandTypes(transition)).containsExactly(ServiceCommandType.MARK_DEAL_ERROR_COMMAND);
        assertThat(transition.movesStatus()).isFalse();
    }

    @Test
    @DisplayName("U7.6 — ошибочная тропа при живой строке звена: переход пустой")
    void u7_6_anErrorPathWithALiveExecutionRowEmitsNothing() {
        DealContext context = contextOf(exposedTranche("2"), livePosition("2"));
        context.getDeal().getPositions().add(livePosition("2"));

        DealTransition transition = harness.handle(context);

        assertThat(transition.hasCommands()).isFalse();
        assertThat(transition.movesStatus()).isFalse();
    }

    @Test
    @DisplayName("U7.7 — сделка без закреплённой детали: деталь требуется у той, у которой обязана быть")
    void u7_7_aRecoveredDealPassesTheInputChecks() {
        DealTranche managed = exposedTranche("2");
        Deal recovered = deal(Deal.Status.ACTIVE, managed);
        recovered.setEntryReason(Deal.EntryReason.RECOVERY);
        recovered.getPositions().add(livePosition("2"));
        DealContext context = contextBuilder(recovered).strategyDetail(null).build();

        DealTransition transition = harness.handle(context);

        assertThat(transition.getHoldSignal()).isNull();
        assertThat(transition.hasCommands()).isFalse();
        assertThat(transition.movesStatus()).isFalse();
    }

    /**
     * Торгуемость читает преконтроль действия, а не проход сопровождения
     * (docs/components/DealActiveHandler.md §«Жёсткая ступень и
     * торгуемость инструмента входными проверками прохода не являются»).
     * Границы справочных правил у обработчика нет вовсе; единственный
     * носитель биржевого состояния инструмента в его входе — сырой статус
     * площадки на инструменте контекста, и он ставится неторгуемым.
     */
    @Test
    @DisplayName("U7.8 — инструмент сделки не торгуется: исход базовой сборки, торгуемость проход не читает")
    void u7_8_aNonTradeableInstrumentLeavesTheInputChecksPassed() {
        DealContext context = baseContext();
        context.getInstrument().setExternalStatus(SUSPENDED_EXCHANGE_STATE);

        DealTransition transition = harness.handle(context);

        assertThat(transition.hasCommands()).isFalse();
        assertThat(transition.movesStatus()).isFalse();
        assertThat(transition.getHoldSignal()).isNull();
    }

    /**
     * Жёсткую ступень пары энфорсит шаг петли ДО обработчика
     * (docs/components/DealOrchestratorJob.md §«Цикл прохода»). Отбор шага
     * здесь настоящий — он единственный коллаборатор с тропой к строке пары
     * (гейт повтора отказавшей надобности), — и строка пары несёт жёсткую
     * ступень. Отказавших строк у базовой сборки нет, поэтому законному
     * читателю спрашивать нечего, и граница строки пары не читается вовсе.
     */
    @Test
    @DisplayName("U7.9 — на паре «счёт, инструмент» жёсткая ступень: исход базовой сборки, ступень обработчик не читает")
    void u7_9_aHardRungOnThePairLeavesTheInputChecksPassed() {
        DealActiveHarness underHardRung =
                DealActiveHarness.withPairState(pairState(Instrument.SafetyRung.TRADE_BLOCKED));

        DealTransition transition = underHardRung.handle(baseContext());

        assertThat(transition.hasCommands()).isFalse();
        assertThat(transition.movesStatus()).isFalse();
        assertThat(transition.getHoldSignal()).isNull();
        verifyNoInteractions(underHardRung.pairStateBoundary());
    }

    /**
     * Восстановленная сделка до первой добычи позиции: заявок у транша нет,
     * строки эпизода нет — сверка нулевая с обеих сторон при любом живом
     * риске. Проход добывает позицию и каскада не гонит: иначе транш уходит
     * в выход по нулевой экспозиции, а его терминал матрица отвергает на
     * неполном графе навсегда (находки {@code F13} и {@code F9} сквозного
     * набора).
     */
    @Test
    @DisplayName("Восстановленная сделка без строки эпизода: добыча позиции, каскад не идёт")
    void aRecoveredDealWithoutAnEpisodeObservesThePositionFirst() {
        Deal recovered = deal(Deal.Status.ACTIVE, tranche(TRANCHE_ID, DealTranche.Status.MANAGING));
        recovered.setEntryReason(Deal.EntryReason.RECOVERY);
        DealContext context = contextBuilder(recovered).strategyDetail(null).graphComplete(Boolean.FALSE).build();

        assertReobserved(context, ServiceCommandType.REFRESH_POSITION_COMMAND);
    }

    // --- сборка ------------------------------------------------------------

    /**
     * Обе стороны сверки добываются заново — и ничего сверх: ни ступени
     * (её поднимает детектор вне прохода на устойчивом расхождении), ни
     * ребра, ни работы каскада.
     */
    private void assertReobserved(DealContext context, ServiceCommandType... observations) {
        harness.givenFetch(ServiceCommandType.REFRESH_ORDER_COMMAND);
        harness.givenFetch(ServiceCommandType.REFRESH_POSITION_COMMAND);

        DealTransition transition = harness.handle(context);

        assertThat(commandTypes(transition)).containsExactly(observations);
        assertThat(transition.getHoldSignal()).isNull();
        assertThat(transition.movesStatus()).isFalse();
        assertThat(transition.getTrancheEdges()).isEmpty();
        harness.verifyCascadeNotRun();
    }

    private DealContext baseContext() {
        return contextOf(exposedTranche("2"), livePosition("2"));
    }

    private DealTranche exposedTranche(String exposure) {
        return exposed(tranche(TRANCHE_ID, DealTranche.Status.MANAGING), exposure);
    }

    private DealContext contextOf(DealTranche tranche, Position episode) {
        Deal active = deal(Deal.Status.ACTIVE, tranche);
        if (nonNull(episode)) {
            active.getPositions().add(episode);
        }
        return contextBuilder(active).build();
    }

    private List<ServiceCommandType> commandTypes(DealTransition transition) {
        return transition.getCommands().stream().map(ServiceCommand::getType).toList();
    }
}
