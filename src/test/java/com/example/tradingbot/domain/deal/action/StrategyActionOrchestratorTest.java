package com.example.tradingbot.domain.deal.action;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.tradingbot.domain.command.DealActionState;
import com.example.tradingbot.domain.command.DealActionStateStatus;
import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.deal.ActionPlan;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStep;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyActionType;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAlgoOrderAction;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Связывает выбор очередного действия пакета с его домом
 * (docs/components/StrategyActionOrchestrator.md §«Порядок выбора
 * действия»).
 *
 * <p>Несущее: пакет шага исполняется ЦЕЛИКОМ, за проход — одно действие.
 * Прежняя редакция брала первое действие пакета каждым проходом, поэтому
 * двухдейственная ступень (снять защиту плюс поставить новую) не
 * доигрывалась: строка первого действия становилась терминальной, а второе
 * не запускалось ни разу.
 */
class StrategyActionOrchestratorTest {

    private final StrategyActionOrchestrator orchestrator =
            new StrategyActionOrchestrator(List.of(new ReadyExecutor(), new DeferredExecutor()), null);

    @Test
    @DisplayName("Пакет продвигается: действие с готовой строкой пропускается, берётся следующее")
    void packageAdvancesPastStartedAction() {
        StrategyAction first = algoAction(1L, StrategyActionType.CREATE_ACTION);
        StrategyAction second = algoAction(2L, StrategyActionType.CREATE_ACTION);
        DealTranche tranche = tranche();

        Optional<StrategyAction> next = orchestrator.nextAction(step(first, second),
                context(tranche, completedRow(first, tranche)), tranche);

        assertTrue(next.isPresent());
        assertEquals(2L, next.get().getId());
    }

    @Test
    @DisplayName("Ключ порядка — риск-класс: снимающее защиту идёт после устанавливающего")
    void protectionRemovingGoesLast() {
        StrategyAction cancel = algoAction(1L, StrategyActionType.CANCEL_ACTION);
        StrategyAction create = algoAction(2L, StrategyActionType.CREATE_ACTION);
        DealTranche tranche = tranche();

        // Порядок объявления — снятие первым; ключом порядка он не служит.
        Optional<StrategyAction> next = orchestrator.nextAction(step(cancel, create), context(tranche), tranche);

        assertTrue(next.isPresent());
        assertEquals(2L, next.get().getId());
    }

    @Test
    @DisplayName("Отложенное действие останавливает пакет: строка не заводится, следующее не обгоняет")
    void deferredActionStopsPackage() {
        StrategyAction deferred = algoAction(1L, StrategyActionType.CANCEL_ACTION);
        StrategyAction create = algoAction(2L, StrategyActionType.CREATE_ACTION);
        DealTranche tranche = tranche();

        // Устанавливающее уже исполнено — очередь дошла до снятия, а оно ждёт покрытия.
        Optional<StrategyAction> next = orchestrator.nextAction(step(deferred, create),
                context(tranche, completedRow(create, tranche)), tranche);

        assertTrue(next.isEmpty());
    }

    @Test
    @DisplayName("Пакет исчерпан — начинать нечего")
    void exhaustedPackageYieldsNothing() {
        StrategyAction only = algoAction(1L, StrategyActionType.CREATE_ACTION);
        DealTranche tranche = tranche();

        Optional<StrategyAction> next = orchestrator.nextAction(step(only),
                context(tranche, completedRow(only, tranche)), tranche);

        assertTrue(next.isEmpty());
    }

    private StrategyStep step(StrategyAction... actions) {
        StrategyStep step = new StrategyStep();
        step.setActions(List.of(actions));
        return step;
    }

    private StrategyAlgoOrderAction algoAction(Long id, StrategyActionType type) {
        StrategyAlgoOrderAction action = new StrategyAlgoOrderAction();
        action.setId(id);
        action.setActionType(type);
        return action;
    }

    private DealTranche tranche() {
        DealTranche tranche = new DealTranche();
        tranche.setId(7L);
        tranche.setEpisodeSeq(1);
        tranche.setStatus(DealTranche.Status.MANAGING);
        return tranche;
    }

    private DealActionState completedRow(StrategyAction action, DealTranche tranche) {
        DealActionState state = new DealActionState();
        state.setStrategyActionId(action.getId());
        state.setDealTrancheId(tranche.getId());
        state.setTrancheEpisodeSeq(tranche.getEpisodeSeq());
        state.setStatus(DealActionStateStatus.COMPLETED);
        return state;
    }

    private DealContext context(DealTranche tranche, DealActionState... states) {
        Deal deal = new Deal();
        deal.setId(1L);
        deal.setTranches(List.of(tranche));
        return DealContext.builder().deal(deal).actionStates(List.of(states)).build();
    }

    /** Исполнитель создающих действий: предусловий сверх условия шага у них нет. */
    private static class ReadyExecutor implements StrategyActionExecutor {

        @Override
        public Boolean supports(StrategyAction action) {
            return StrategyActionType.CREATE_ACTION.equals(action.getActionType());
        }

        @Override
        public ActionPlan next(StrategyStep step, StrategyAction action, DealActionState state,
                               DealContext dealContext, DealTranche tranche) {
            return ActionPlan.empty();
        }
    }

    /** Исполнитель снятия: предусловие не выполнено — действие ждёт. */
    private static class DeferredExecutor implements StrategyActionExecutor {

        @Override
        public Boolean supports(StrategyAction action) {
            return StrategyActionType.CANCEL_ACTION.equals(action.getActionType());
        }

        @Override
        public ActionReadiness readiness(StrategyAction action, DealContext dealContext, DealTranche tranche) {
            return ActionReadiness.DEFERRED;
        }

        @Override
        public ActionPlan next(StrategyStep step, StrategyAction action, DealActionState state,
                               DealContext dealContext, DealTranche tranche) {
            return ActionPlan.empty();
        }
    }
}
