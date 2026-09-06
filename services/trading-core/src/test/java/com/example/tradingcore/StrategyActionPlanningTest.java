package com.example.tradingcore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStep;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyActionType;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAlgoOrderAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyOrderAction;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingcore.domain.command.ActionKind;
import com.example.tradingcore.domain.command.DealActionState;
import com.example.tradingcore.domain.command.DealActionStateStatus;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.ServiceCommand;
import com.example.tradingcore.domain.command.ServiceCommandType;
import com.example.tradingcore.domain.command.strategy.ActionPlan;
import com.example.tradingcore.domain.command.strategy.ActionReadiness;
import com.example.tradingcore.domain.command.strategy.StrategyActionExecutor;
import com.example.tradingcore.domain.command.strategy.StrategyActionOrchestrator;
import com.example.tradingcore.persistence.service.DealActionStateDataService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Выбор действия пакета шага и гейт повтора.
 *
 * <p><b>Предмет — направление ошибки, а не наличие метода.</b> Пакет,
 * берущий каждым проходом первое действие, стоя́л бы на нём вечно: ступень
 * из двух действий (снять защиту, поставить новую) не доигрывалась бы
 * никогда. Пакет, обгоняющий отложенное действие, менял бы объявленный
 * порядок — то есть ставил бы защиту после того, как прежняя снята.
 */
class StrategyActionPlanningTest {

    private static final Long DEAL_ID = 7L;
    private static final Long TRANCHE_ID = 21L;

    private final DealActionStateDataService dataService = mock(DealActionStateDataService.class);
    private final RecordingExecutor executor = new RecordingExecutor();

    private final StrategyActionOrchestrator orchestrator =
            new StrategyActionOrchestrator(List.of(executor), dataService);

    /**
     * Снимающее защиту действие исполняется ПОСЛЕ устанавливающего, как бы
     * они ни были объявлены.
     *
     * <p>Обратный порядок оголяет позицию: прежняя защита снята, новая ещё
     * не стои́т (docs/rules/live-risk-protection.md).
     */
    @Test
    void protectionRemovalGoesAfterEstablishment() {
        StrategyStep step = step(cancelProtection(102L), createProtection(101L));

        Optional<StrategyAction> first = orchestrator.nextAction(step, dealContext(), tranche());

        assertThat(first).isPresent();
        assertThat(first.get().getId()).isEqualTo(101L);
    }

    /**
     * Действие с уже заведённой строкой заново не начинается.
     *
     * <p>Строка — носитель признака «шаг применён на этом эпизоде»; взяв
     * то же действие повторно, пакет никогда не дошёл бы до второго.
     */
    @Test
    void actionWithARowIsNotStartedAgain() {
        StrategyStep step = step(createProtection(101L), createEntry(103L));
        DealContext context = dealContext(planned(101L));

        Optional<StrategyAction> next = orchestrator.nextAction(step, context, tranche());

        assertThat(next).isPresent();
        assertThat(next.get().getId()).isEqualTo(103L);
    }

    /** Отложенное действие ОСТАНАВЛИВАЕТ пакет: обгонять его нельзя. */
    @Test
    void deferredActionHaltsThePackage() {
        executor.readiness = ActionReadiness.DEFERRED;
        StrategyStep step = step(createProtection(101L), createEntry(103L));

        assertThat(orchestrator.nextAction(step, dealContext(), tranche())).isEmpty();
    }

    /** Неактуальное — пропускается: предмета у него больше нет, ждать нечего. */
    @Test
    void irrelevantActionIsSkipped() {
        executor.readinessByAction.put(101L, ActionReadiness.IRRELEVANT);
        StrategyStep step = step(createProtection(101L), createEntry(103L));

        Optional<StrategyAction> next = orchestrator.nextAction(step, dealContext(), tranche());

        assertThat(next).isPresent();
        assertThat(next.get().getId()).isEqualTo(103L);
    }

    /**
     * Первый ход заводит строку исполнения и регистрирует её в контексте
     * прохода.
     *
     * <p>Без регистрации анкер команды не резолвится: контекст собран до
     * строки, и второй запрос того же действия за проход завёл бы вторую.
     */
    @Test
    void firstMoveCreatesAndRegistersTheAnchor() {
        StrategyAction action = createEntry(103L);
        DealContext context = dealContext();
        when(dataService.save(any())).thenAnswer(invocation -> {
            DealActionState saved = invocation.getArgument(0);
            saved.setId(555L);
            return saved;
        });

        ActionPlan plan = orchestrator.plan(step(action), action, null, context, tranche());

        assertThat(plan.hasCommand()).isTrue();
        assertThat(context.actionState(103L, tranche())).isPresent();
        assertThat(executor.seenState.getStatus()).isEqualTo(DealActionStateStatus.PLANNED);
        assertThat(executor.seenState.getTrancheEpisodeSeq()).isEqualTo(1);
    }

    /**
     * Строка, ждущая отката, команды не отдаёт; по наступлении срока
     * перевзводится.
     *
     * <p>Отдать команду раньше срока значило бы тратить бюджет попыток на
     * то же обстоятельство, которое только что отказало.
     */
    @Test
    void retryPendingRowWaitsForItsTurn() {
        StrategyAction action = createEntry(103L);
        DealActionState waiting = planned(103L);
        waiting.setStatus(DealActionStateStatus.RETRY_PENDING);
        waiting.setNextRetryAt(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(5));

        assertThat(orchestrator.plan(step(action), action, waiting, dealContext(), tranche()).isEmpty()).isTrue();
        verify(dataService, never()).save(any());

        waiting.setNextRetryAt(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        when(dataService.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(orchestrator.plan(step(action), action, waiting, dealContext(), tranche()).hasCommand()).isTrue();
        assertThat(waiting.getStatus()).isEqualTo(DealActionStateStatus.PLANNED);
    }

    /** Действия, которое никто не исполняет, пакет не начинает и не планирует. */
    @Test
    void unsupportedActionYieldsNothing() {
        executor.supported = false;
        StrategyAction action = createEntry(103L);

        assertThat(orchestrator.nextAction(step(action), dealContext(), tranche())).isEmpty();
        assertThat(orchestrator.plan(step(action), action, planned(103L), dealContext(), tranche()).isEmpty())
                .isTrue();
    }

    private StrategyStep step(StrategyAction... actions) {
        StrategyStep step = new StrategyStep();
        step.setActions(List.of(actions));
        return step;
    }

    private DealContext dealContext(DealActionState... states) {
        Deal deal = new Deal();
        deal.setId(DEAL_ID);
        return DealContext.builder()
                .deal(deal)
                .actionStates(new ArrayList<>(List.of(states)))
                .build();
    }

    private DealTranche tranche() {
        DealTranche tranche = new DealTranche();
        tranche.setId(TRANCHE_ID);
        tranche.setEpisodeSeq(1);
        return tranche;
    }

    private DealActionState planned(Long strategyActionId) {
        DealActionState state = new DealActionState();
        state.setDealId(DEAL_ID);
        state.setActionKind(ActionKind.STRATEGY);
        state.setStrategyActionId(strategyActionId);
        state.setDealTrancheId(TRANCHE_ID);
        state.setTrancheEpisodeSeq(1);
        state.setStatus(DealActionStateStatus.PLANNED);
        return state;
    }

    private StrategyAlgoOrderAction createProtection(Long id) {
        StrategyAlgoOrderAction action = new StrategyAlgoOrderAction();
        action.setId(id);
        action.setKey("protection-" + id);
        action.setActionType(StrategyActionType.CREATE_ACTION);
        action.setConditionType(AlgoOrder.ConditionType.STOP_LOSS);
        return action;
    }

    private StrategyAlgoOrderAction cancelProtection(Long id) {
        StrategyAlgoOrderAction action = createProtection(id);
        action.setActionType(StrategyActionType.CANCEL_ACTION);
        action.setTargetActionKey("protection-101");
        return action;
    }

    private StrategyOrderAction createEntry(Long id) {
        StrategyOrderAction action = new StrategyOrderAction();
        action.setId(id);
        action.setKey("entry-" + id);
        action.setActionType(StrategyActionType.CREATE_ACTION);
        return action;
    }

    /** Исполнитель-заглушка: отвечает заданной готовностью и запоминает строку. */
    private static class RecordingExecutor implements StrategyActionExecutor {

        private boolean supported = true;
        private ActionReadiness readiness = ActionReadiness.READY;
        private final Map<Long, ActionReadiness> readinessByAction = new HashMap<>();
        private DealActionState seenState;

        @Override
        public Boolean supports(StrategyAction action) {
            return supported;
        }

        @Override
        public ActionReadiness readiness(StrategyAction action, DealContext dealContext, DealTranche tranche) {
            return readinessByAction.getOrDefault(action.getId(), readiness);
        }

        @Override
        public ActionPlan next(StrategyStep step, StrategyAction action, DealActionState state,
                               DealContext dealContext, DealTranche tranche) {
            seenState = state;
            return ActionPlan.of(ServiceCommand.builder()
                    .type(ServiceCommandType.SUBMIT_ORDER_COMMAND)
                    .dealId(dealContext.getDeal().getId())
                    .dealActionStateId(state.getId())
                    .build());
        }
    }
}
