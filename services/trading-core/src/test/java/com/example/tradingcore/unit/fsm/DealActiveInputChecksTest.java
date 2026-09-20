package com.example.tradingcore.unit.fsm;

import static com.example.tradingcore.unit.fsm.FsmFixture.TRANCHE_ID;
import static com.example.tradingcore.unit.fsm.FsmFixture.contextBuilder;
import static com.example.tradingcore.unit.fsm.FsmFixture.deal;
import static com.example.tradingcore.unit.fsm.FsmFixture.exposed;
import static com.example.tradingcore.unit.fsm.FsmFixture.liveEntryLeg;
import static com.example.tradingcore.unit.fsm.FsmFixture.livePosition;
import static com.example.tradingcore.unit.fsm.FsmFixture.tranche;
import static java.util.Objects.nonNull;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.ServiceCommand;
import com.example.tradingcore.domain.command.ServiceCommandType;
import com.example.tradingcore.domain.command.SystemActionType;
import com.example.tradingcore.domain.fsm.DealTransition;
import com.example.tradingcore.domain.safety.HoldRung;
import com.example.tradingcore.domain.safety.HoldScope;
import com.example.tradingcore.util.Constants;
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
 * <p><b>Кейсы {@code U7.8} и {@code U7.9} не прогоняются</b> — дом
 * называет обработчика исполнителем двух проверок, которых он не делает
 * (находка {@code F-3}): торгуемость инструмента он не читает вовсе, а
 * жёсткую ступень энфорсит петля до него. Ожидания у них нет, и кода они
 * не получили.
 */
class DealActiveInputChecksTest {

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
    @DisplayName("U7.2 — сумма экспозиций 1 при живом эпизоде 2: только просьба биржевой ступени")
    void u7_2_anExposureShortfallRequestsTheAccountRung() {
        DealContext context = contextOf(exposedTranche("1"), livePosition("2"));

        assertUncoveredRiskRung(context);
    }

    @Test
    @DisplayName("U7.3 — сумма экспозиций 2 без живого эпизода: правая сторона сверки — ноль")
    void u7_3_anExposureWithoutALiveEpisodeRequestsTheSameRung() {
        DealContext context = contextOf(exposedTranche("2"), null);

        assertUncoveredRiskRung(context);
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
        context.getDeal().getOrders().add(liveEntryLeg(90L, null));
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

    // --- сборка ------------------------------------------------------------

    /** Просьба биржевой ступени 2 — и ничего сверх неё. */
    private void assertUncoveredRiskRung(DealContext context) {
        DealTransition transition = harness.handle(context);

        assertThat(transition.getHoldSignal().getScope()).isEqualTo(HoldScope.EXCHANGE_ACCOUNT);
        assertThat(transition.getHoldSignal().getRung()).isEqualTo(HoldRung.HARD);
        assertThat(transition.getHoldSignal().getCode())
                .isEqualTo(Constants.Hold.EXCHANGE_LIVE_RISK_UNCOVERED);
        assertThat(transition.hasCommands()).isFalse();
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
