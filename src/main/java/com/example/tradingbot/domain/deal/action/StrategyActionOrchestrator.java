package com.example.tradingbot.domain.deal.action;

import com.example.tradingbot.domain.command.DealActionState;
import com.example.tradingbot.domain.command.DealActionStateStatus;
import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.command.RuntimeTarget;
import com.example.tradingbot.domain.deal.ActionPlan;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStep;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAction;
import com.example.tradingbot.persistence.service.DealActionStateDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static java.util.Objects.isNull;
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
 * docs/decisions/fsm-execution-layering.md.
 */
@Component
@RequiredArgsConstructor
public class StrategyActionOrchestrator {

    private final List<StrategyActionExecutor> executors;
    private final DealActionStateDataService dealActionStateDataService;

    public ActionPlan plan(StrategyStep step, StrategyAction action, DealActionState state, DealContext dealContext) {
        if (isRetryPending(state)) {
            if (isFalse(retryDue(state))) {
                return ActionPlan.empty();
            }
            rearmForRetry(state);
        }
        return executorFor(action)
                .map(executor -> executor.next(step, action, state, dealContext))
                .orElseGet(ActionPlan::empty);
    }

    private Optional<StrategyActionExecutor> executorFor(StrategyAction action) {
        return executors.stream()
                        .filter(executor -> isTrue(executor.supports(action)))
                        .findFirst();
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
