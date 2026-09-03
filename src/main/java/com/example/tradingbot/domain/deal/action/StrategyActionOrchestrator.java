package com.example.tradingbot.domain.deal.action;

import com.example.tradingbot.domain.command.DealActionState;
import com.example.tradingbot.domain.command.DealActionStateStatus;
import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.command.RuntimeTarget;
import com.example.tradingbot.domain.deal.ActionPlan;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStep;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyActionType;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAlgoOrderAction;
import com.example.tradingbot.persistence.service.DealActionStateDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.Comparator.comparingInt;
import static java.util.Objects.isNull;
import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

/**
 * Оркестратор действия: для выбранного handler'ом StrategyAction гейтит
 * повтор (RETRY_PENDING → ждём backoff или пере-эмитим со стадии повтора) и
 * делегирует прогресс подходящему {@link StrategyActionExecutor} (по типу
 * действия). Возвращает {@link ActionPlan} (команда / risk-block / ошибка
 * расчёта / пусто). Сам команды не исполняет и статус сделки не двигает —
 * это делает handler/оркестратор петли. Обобщает прежний
 * {@code DealActionPlanner} (стадии/повтор) поверх per-type executor'ов
 * (обобщение {@code ServiceCommandFactory}). См.
 * docs/processes/fsm-execution-layering.md.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StrategyActionOrchestrator {

    private final List<StrategyActionExecutor> executors;
    private final DealActionStateDataService dealActionStateDataService;

    /**
     * Следующее действие пакета шага, которое можно начать этим проходом:
     * первое ещё не начатое в порядке риск-класса. Пусто — начинать
     * нечего (пакет исчерпан либо очередное действие ждёт предусловия).
     *
     * <p><b>Ключ порядка — риск-класс:</b> устанавливающие защиту раньше
     * снимающих (docs/rules/live-risk-protection.md). Порядок объявления
     * ключом не служит и внутри одного класса сохраняется — сортировка
     * устойчива.
     *
     * <p><b>Гейт готовности стои́т ДО строки исполнения:</b> отложенное
     * действие пакет останавливает (обгонять его значило бы менять
     * объявленный порядок), неактуальное — пропускается, и пакет берёт
     * следующее (см. {@link ActionReadiness}).
     */
    public Optional<StrategyAction> nextAction(StrategyStep step, DealContext dealContext, DealTranche tranche) {
        for (StrategyAction action : byRiskClass(step)) {
            if (dealContext.actionState(action.getId(), tranche).isPresent()) {
                continue;
            }
            ActionReadiness readiness = readiness(action, dealContext, tranche);
            if (ActionReadiness.DEFERRED.equals(readiness)) {
                return Optional.empty();
            }
            if (ActionReadiness.READY.equals(readiness)) {
                return Optional.of(action);
            }
        }
        return Optional.empty();
    }

    public ActionPlan plan(StrategyStep step, StrategyAction action, DealActionState state, DealContext dealContext,
                           DealTranche tranche) {
        if (isRetryPending(state)) {
            if (isFalse(retryDue(state))) {
                return ActionPlan.empty();
            }
            rearmForRetry(state);
        }
        return executorFor(action)
                .map(executor -> executor.next(step, action, state, dealContext, tranche))
                .orElseGet(ActionPlan::empty);
    }

    private Optional<StrategyActionExecutor> executorFor(StrategyAction action) {
        return executors.stream()
                        .filter(executor -> isTrue(executor.supports(action)))
                        .findFirst();
    }

    /** Действия пакета в порядке риск-класса: снимающие защиту — последними. */
    private List<StrategyAction> byRiskClass(StrategyStep step) {
        return emptyIfNull(step.getActions()).stream()
                .sorted(comparingInt(action -> isProtectionRemoving(action) ? 1 : 0))
                .collect(Collectors.toList());
    }

    /** Действие снимает защиту: CANCEL над отдельной условной заявкой. */
    private boolean isProtectionRemoving(StrategyAction action) {
        return action instanceof StrategyAlgoOrderAction
                && StrategyActionType.CANCEL_ACTION.equals(action.getActionType());
    }

    /**
     * Готовность действия по его исполнителю. Исполнителя нет —
     * действие неисполнимо: пакет идёт дальше, а не встаёт на нём
     * навсегда, и пропуск объявляется в лог. Тихо пропускать нельзя:
     * это дефект раскладки исполнителей, а не состояние сделки.
     */
    private ActionReadiness readiness(StrategyAction action, DealContext dealContext, DealTranche tranche) {
        Optional<StrategyActionExecutor> executor = executorFor(action);
        if (executor.isEmpty()) {
            log.warn("No StrategyActionExecutor supports action id={} type={} — action skipped",
                    action.getId(), action.getActionType());
            return ActionReadiness.IRRELEVANT;
        }
        return executor.get().readiness(action, dealContext, tranche);
    }

    private boolean isRetryPending(DealActionState state) {
        return DealActionStateStatus.RETRY_PENDING.equals(state.getStatus());
    }

    /**
     * Повтор разрешён, если nextRetryAt не задан или уже наступил (иначе ждём backoff).
     */
    private Boolean retryDue(DealActionState state) {
        return isNull(state.getNextRetryAt())
                || isFalse(OffsetDateTime.now(ZoneOffset.UTC)
                                         .isBefore(state.getNextRetryAt()));
    }

    /**
     * Перевод RETRY_PENDING обратно на стадию, с которой команда
     * пере-эмитится: target отсутствует → PLANNED (повтор CREATE); target
     * создан → CREATED (повтор SUBMIT; D-B3 recovery-by-clientId делает
     * повторный submit идемпотентным). REFRESH-провал поднимается через
     * CREATED → SUBMIT (recovery находит сущность) → SUBMITTED → REFRESH.
     */
    private void rearmForRetry(DealActionState state) {
        RuntimeTarget target = state.getTarget();
        state.setStatus(isNull(target)
                                ? DealActionStateStatus.PLANNED
                                : DealActionStateStatus.CREATED);
        dealActionStateDataService.save(state);
    }
}
