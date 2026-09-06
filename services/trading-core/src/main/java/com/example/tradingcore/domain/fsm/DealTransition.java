package com.example.tradingcore.domain.fsm;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingcore.domain.command.ServiceCommand;
import com.example.tradingcore.domain.safety.HoldSignal;
import java.util.ArrayList;
import java.util.List;
import lombok.Value;

/**
 * Исход одного прохода FSM сделки: команды и, если он разрешён, новый
 * статус (docs/components/DealStateMachine.md).
 *
 * <p><b>Статусные рёбра, являющиеся исходом системного действия, здесь не
 * едут.</b> Терминалы и подтверждение входа пишет ЗВЕНО в транзакции
 * своего завершения, а обработчик гейтит эмиссию: переход несёт тогда
 * команду, а не статус (docs/processes/fsm-execution-layering.md).
 *
 * <p><b>Причины едут вместе со своим ребром.</b> Писатель обоих полей —
 * обработчик, и пишет он их той же транзакцией, которой гейтит ребро:
 * причина выхода из штатного ведения на ребре в координированный выход,
 * причина закрытия — на нём же и на прямом терминале
 * (docs/lifecycles/Deal.md).
 *
 * <p>Живёт только в памяти прохода — читателя за сериализацией нет.
 */
@Value
public class DealTransition {

    /** Команды прохода в порядке диспетчеризации; пусто — команд нет. */
    List<ServiceCommand> commands;

    /** Целевой статус сделки; пусто — сделка остаётся в своём. */
    Deal.Status nextStatus;

    /** Причина выхода из штатного ведения; едет с ребром в координированный выход. */
    Deal.ShutdownReason shutdownReason;

    /** Итоговая бизнес-причина закрытия; едет со своим ребром. */
    Deal.CloseReason closeReason;

    /** Затребованная ступень радиуса; пусто — ступени проход не просит. */
    HoldSignal holdSignal;

    /**
     * Одобренные рёбра траншей, ждущие применения тем же ходом, что и
     * ребро сделки: каскад их не пишет, потому что применение идёт ПОСЛЕ
     * диспетчеризации команд (docs/components/DealOrchestratorJob.md
     * §«Цикл прохода»).
     */
    List<TrancheEdge> trancheEdges;

    private DealTransition(List<ServiceCommand> commands, Deal.Status nextStatus,
                           Deal.ShutdownReason shutdownReason, Deal.CloseReason closeReason,
                           HoldSignal holdSignal, List<TrancheEdge> trancheEdges) {
        this.commands = List.copyOf(emptyIfNull(commands));
        this.nextStatus = nextStatus;
        this.shutdownReason = shutdownReason;
        this.closeReason = closeReason;
        this.holdSignal = holdSignal;
        this.trancheEdges = List.copyOf(emptyIfNull(trancheEdges));
    }

    /** Сделка остаётся в своём статусе. */
    public static DealTransition stay() {
        return new DealTransition(List.of(), null, null, null, null, List.of());
    }

    /** Команды прохода без смены статуса. */
    public static DealTransition commands(List<ServiceCommand> commands) {
        return new DealTransition(commands, null, null, null, null, List.of());
    }

    /** Переход в названный статус без причин. */
    public static DealTransition moveTo(Deal.Status status) {
        return new DealTransition(List.of(), status, null, null, null, List.of());
    }

    /**
     * Ребро в координированный выход: обе причины пишет обработчик той же
     * транзакцией, которой гейтит ребро.
     */
    public static DealTransition collapse(Deal.ShutdownReason shutdownReason, Deal.CloseReason closeReason) {
        return new DealTransition(List.of(), Deal.Status.EXIT_PENDING, shutdownReason, closeReason, null,
                List.of());
    }

    /** Затребованная ступень; статуса проход не двигает. */
    public static DealTransition requestRung(HoldSignal signal) {
        return new DealTransition(List.of(), null, null, null, signal, List.of());
    }

    /** Тот же исход плюс команда прохода. */
    public DealTransition withCommand(ServiceCommand command) {
        if (isNull(command)) {
            return this;
        }
        List<ServiceCommand> extended = new ArrayList<>(commands);
        extended.add(command);
        return new DealTransition(extended, nextStatus, shutdownReason, closeReason, holdSignal,
                trancheEdges);
    }

    /** Тот же исход с затребованной ступенью. */
    public DealTransition withRung(HoldSignal signal) {
        return new DealTransition(commands, nextStatus, shutdownReason, closeReason, signal, trancheEdges);
    }

    /** Тот же исход без статусного ребра — им пользуется гейт матрицы. */
    public DealTransition withoutStatus() {
        return new DealTransition(commands, null, null, null, holdSignal, trancheEdges);
    }

    /** Тот же исход с рёбрами траншей, одобренными каскадом. */
    public DealTransition withTrancheEdges(List<TrancheEdge> edges) {
        return new DealTransition(commands, nextStatus, shutdownReason, closeReason, holdSignal, edges);
    }

    /** Переход несёт команды к диспетчеризации. */
    public Boolean hasCommands() {
        return isNotEmpty(commands);
    }

    /** Переход двигает статус сделки. */
    public Boolean movesStatus() {
        return nonNull(nextStatus);
    }
}
