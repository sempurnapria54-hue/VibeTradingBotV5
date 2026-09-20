package com.example.tradingcore.unit.fsm;

import static com.example.tradingcore.unit.fsm.DealActiveHarness.command;
import static com.example.tradingcore.unit.fsm.FsmFixture.TRANCHE_ID;
import static com.example.tradingcore.unit.fsm.FsmFixture.contextBuilder;
import static com.example.tradingcore.unit.fsm.FsmFixture.deal;
import static com.example.tradingcore.unit.fsm.FsmFixture.fills;
import static com.example.tradingcore.unit.fsm.FsmFixture.tranche;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.ServiceCommand;
import com.example.tradingcore.domain.command.ServiceCommandType;
import com.example.tradingcore.domain.deal.DealTerminalGate;
import com.example.tradingcore.domain.deal.ProtectionCoverageGate;
import com.example.tradingcore.domain.fsm.DealHandler;
import com.example.tradingcore.domain.fsm.DealStateMachine;
import com.example.tradingcore.domain.fsm.DealTrancheHandler;
import com.example.tradingcore.domain.fsm.DealTrancheStateMachine;
import com.example.tradingcore.domain.fsm.DealTransition;
import com.example.tradingcore.domain.fsm.DealTransitionGate;
import com.example.tradingcore.domain.fsm.TrancheCascade;
import com.example.tradingcore.domain.fsm.TrancheTransition;
import com.example.tradingcore.domain.fsm.TrancheTransitionGate;
import com.example.tradingcore.domain.fsm.deal.ErrorHandler;
import com.example.tradingcore.domain.safety.HoldSignal;
import com.example.tradingcore.util.Constants;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Отсутствие выходов у предмета — группа {@code U25} документа
 * `.claude/tests/cases/trading-core-fsm.md` (дом —
 * docs/components/DealStateMachine.md §Границы,
 * docs/components/DealTrancheStateMachine.md §Границы,
 * docs/processes/fsm-execution-layering.md).
 *
 * <p><b>Утверждение об отсутствии читается по коллабораторам, а не по
 * одному проходу:</b> «на биржу не ходит» и «ступень не поднимает» — это
 * свойства класса, и прогон, на котором вызова не случилось, их не
 * устанавливает. Поэтому клетки ниже спрашивают перечень полей предмета
 * там, где предмет ожидания есть отсутствие способности.
 */
class FsmAbsentOutputsTest {

    /** Классы предмета, чьи границы группа и мерит. */
    private static final List<Class<?>> SUBJECTS = List.of(
            DealTransitionGate.class, DealTerminalGate.class,
            TrancheTransitionGate.class, ProtectionCoverageGate.class,
            DealStateMachine.class, DealTrancheStateMachine.class, TrancheCascade.class);

    private final DealTerminalGate terminalGate = new DealTerminalGate();

    private final DealTransitionGate dealGate = new DealTransitionGate(terminalGate);

    private final TrancheTransitionGate trancheGate = new TrancheTransitionGate();

    @Test
    @DisplayName("U25.1 — любой проход: статус на моделях сделки и транша не меняется")
    void u25_1_noPassWritesTheStatusOnTheModels() {
        DealContext context = context();
        DealTranche subject = context.getDeal().getTranches().getFirst();

        dealMachine(DealTransition.moveTo(Deal.Status.EXIT_PENDING)).run(context);
        trancheMachine(TrancheTransition.moveTo(DealTranche.Status.EXIT_PENDING)).run(context, subject);

        assertThat(context.getDeal().getStatus()).isEqualTo(Deal.Status.ACTIVE);
        assertThat(subject.getStatus()).isEqualTo(DealTranche.Status.MANAGING);
    }

    @Test
    @DisplayName("U25.2 — ступень радиуса не поднимается: переход её только просит")
    void u25_2_noSubjectCanRaiseASafetyRung() {
        assertThat(fieldTypeNames()).noneMatch(name -> name.contains("Safety") || name.contains("Hold"));

        DealContext context = context();
        HoldSignal rung = HoldSignal.instrument(Constants.Hold.INSTRUMENT_MARKET_DATA_EXPIRED);

        DealTransition transition = dealMachine(DealTransition.requestRung(rung)).run(context);

        assertThat(transition.getHoldSignal()).isEqualTo(rung);
    }

    @Test
    @DisplayName("U25.3 — ни один гейт и ни одна машина не строит команды сами")
    void u25_3_noSubjectBuildsItsOwnCommands() {
        assertThat(fieldTypeNames()).noneMatch(name -> name.contains("Executor"));

        DealContext context = context();
        DealTranche subject = context.getDeal().getTranches().getFirst();

        assertThat(dealMachine(DealTransition.stay()).run(context).hasCommands()).isFalse();
        assertThat(trancheMachine(TrancheTransition.stay()).run(context, subject).hasCommands()).isFalse();
    }

    @Test
    @DisplayName("U25.4 — на биржу не ходит ни один класс предмета: коллабораторов границы у них нет")
    void u25_4_noSubjectTalksToTheExchange() {
        assertThat(SUBJECTS.stream()
                .flatMap(subject -> List.of(subject.getDeclaredFields()).stream())
                .map(field -> field.getType().getName())
                .toList())
                .noneMatch(name -> name.contains(".integration."));
    }

    @Test
    @DisplayName("U25.5 — проход сделки в ошибочном состоянии: каскад не создан")
    void u25_5_theErrorPassNeverRunsTheTrancheMachine() {
        List<Class<?>> fieldTypes = List.of(ErrorHandler.class.getDeclaredFields()).stream()
                .map(Field::getType)
                .toList();

        assertThat(fieldTypes).doesNotContain(TrancheCascade.class, DealTrancheStateMachine.class);
    }

    @Test
    @DisplayName("U25.6 — проход транша ребра СДЕЛКИ не предлагает: такого поля у перехода нет")
    void u25_6_theTrancheTransitionCarriesNoDealStatus() {
        assertThat(List.of(TrancheTransition.class.getDeclaredFields()).stream()
                .map(Field::getType)
                .toList())
                .doesNotContain(Deal.Status.class);
    }

    @Test
    @DisplayName("U25.7 — ребра в ошибочный статус у транша нет: такого статуса у него не существует")
    void u25_7_theTrancheHasNoErrorStatus() {
        assertThat(List.of(DealTranche.Status.values()))
                .noneMatch(status -> status.name().contains("ERROR")
                        || status.name().contains("EMERGENCY"));
    }

    @Test
    @DisplayName("U25.8 — терминал транша одобрен: сделка от этого не закрывается")
    void u25_8_anApprovedTrancheTerminalDoesNotCloseTheDeal() {
        DealContext context = context();
        DealTranche subject = context.getDeal().getTranches().getFirst();
        subject.setStatus(DealTranche.Status.EXIT_PENDING);

        TrancheTransition transition = new DealTrancheStateMachine(
                List.of(trancheHandler(DealTranche.Status.EXIT_PENDING,
                        TrancheTransition.close(DealTranche.CloseReason.STRATEGY_EXIT))),
                trancheGate).run(context, subject);

        assertThat(transition.getNextStatus()).isEqualTo(DealTranche.Status.CLOSED);
        assertThat(context.getDeal().getStatus()).isEqualTo(Deal.Status.ACTIVE);
        assertThat(context.getDeal().allTranchesTerminal()).isFalse();
    }

    @Test
    @DisplayName("U25.9 — потолков риска предмет не считает: их считает преконтроль по всей сделке")
    void u25_9_noSubjectComputesRiskCeilings() {
        assertThat(fieldTypeNames()).noneMatch(name -> name.contains("Risk"));
    }

    @Test
    @DisplayName("U25.10 — два прохода с одним состоянием: состояния между ними предмет не держит")
    void u25_10_theSubjectsHoldNoStateBetweenPasses() {
        DealContext context = context();
        DealStateMachine machine = dealMachine(DealTransition.moveTo(Deal.Status.EXIT_PENDING));

        DealTransition first = machine.run(context);
        DealTransition second = machine.run(context);

        assertThat(second.getNextStatus()).isEqualTo(first.getNextStatus());
        assertThat(SUBJECTS.stream()
                .flatMap(subject -> List.of(subject.getDeclaredFields()).stream())
                .filter(field -> isFalse(Modifier.isStatic(field.getModifiers())))
                .toList())
                .allMatch(field -> Modifier.isFinal(field.getModifiers()));
    }

    @Test
    @DisplayName("U25.11 — проход сделки без рёбер траншей: перечень пустой, а не пустое значение")
    void u25_11_theTrancheEdgeListIsEmptyRatherThanAbsent() {
        DealTransition transition = dealMachine(DealTransition.stay()).run(context());

        assertThat(transition.getTrancheEdges()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("U25.12 — ребро отвергнуто: причины сняты, а ступень и команды — нет")
    void u25_12_aRefusedEdgeDropsOnlyItsOwnHalf() {
        HoldSignal rung = HoldSignal.instrument(Constants.Hold.INSTRUMENT_MARKET_DATA_EXPIRED);
        DealTransition proposed = DealTransition
                .collapse(Deal.ShutdownReason.RISK_POLICY, Deal.CloseReason.RISK_CONTROL)
                .withCommand(command(ServiceCommandType.CREATE_ORDER_COMMAND))
                .withRung(rung);
        DealContext context = contextBuilder(deal(Deal.Status.ERROR,
                fills(tranche(TRANCHE_ID, DealTranche.Status.MANAGING), "0", "0"))).build();

        DealTransition transition = new DealStateMachine(
                List.of(dealHandler(Deal.Status.ERROR, proposed)), dealGate).run(context);

        assertThat(transition.movesStatus()).isFalse();
        assertThat(transition.getShutdownReason()).isNull();
        assertThat(transition.getCloseReason()).isNull();
        assertThat(transition.getHoldSignal()).isEqualTo(rung);
        assertThat(transition.getCommands().stream().map(ServiceCommand::getType).toList())
                .containsExactly(ServiceCommandType.CREATE_ORDER_COMMAND);
    }

    // --- сборка ------------------------------------------------------------

    private List<String> fieldTypeNames() {
        return SUBJECTS.stream()
                .flatMap(subject -> List.of(subject.getDeclaredFields()).stream())
                .map(field -> field.getType().getSimpleName())
                .toList();
    }

    private DealStateMachine dealMachine(DealTransition proposed) {
        return new DealStateMachine(List.of(dealHandler(Deal.Status.ACTIVE, proposed)), dealGate);
    }

    private DealTrancheStateMachine trancheMachine(TrancheTransition proposed) {
        return new DealTrancheStateMachine(
                List.of(trancheHandler(DealTranche.Status.MANAGING, proposed)), trancheGate);
    }

    private DealHandler dealHandler(Deal.Status status, DealTransition proposed) {
        return new DealHandler() {

            @Override
            public Deal.Status handledStatus() {
                return status;
            }

            @Override
            public DealTransition handle(DealContext dealContext) {
                return proposed;
            }
        };
    }

    private DealTrancheHandler trancheHandler(DealTranche.Status status, TrancheTransition proposed) {
        return new DealTrancheHandler() {

            @Override
            public DealTranche.Status handledStatus() {
                return status;
            }

            @Override
            public TrancheTransition handle(DealContext dealContext, DealTranche tranche) {
                return proposed;
            }
        };
    }

    private DealContext context() {
        return contextBuilder(deal(Deal.Status.ACTIVE,
                tranche(TRANCHE_ID, DealTranche.Status.MANAGING))).build();
    }
}
