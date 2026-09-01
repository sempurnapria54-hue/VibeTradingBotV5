package com.example.tradingbot.domain.deal.action;

import com.example.tradingbot.domain.command.DealActionState;
import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.deal.ActionPlan;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStep;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAction;

/**
 * Per-pass исполнитель одного типа действия стратегии (CREATE ordinary /
 * CREATE algo / …). За проход смотрит стадию {@link DealActionState} и выдаёт
 * <b>следующую</b> команду действия ({@code place → refresh-подтверждение по
 * фактам → следующая}) либо пустой {@link ActionPlan} («готово / нечего
 * делать»). Секвенс ведёт петля по подтверждённым фактам, не по ACK
 * (CMD-Q6). Диспетчер {@link StrategyActionOrchestrator} маршрутизирует по
 * {@link #supports(StrategyAction)}. Обобщает прежние {@code DealActionPlanner}
 * + {@code ServiceCommandFactory}, разложенные по типам действий. См.
 * docs/processes/fsm-execution-layering.md.
 */
public interface StrategyActionExecutor {

    /** Исполняет ли этот executor данное действие (по подтипу + типу действия). */
    Boolean supports(StrategyAction action);

    /**
     * Готово ли действие начать исполнение — вопрос задаётся ДО того, как
     * заведена строка исполнения. Умолчание {@code READY}: у действия,
     * которое ничего не снимает и ничьего покрытия не забирает,
     * предусловия сверх условия шага нет, а условие шага проверил
     * обработчик.
     */
    default ActionReadiness readiness(StrategyAction action, DealContext dealContext, DealTranche tranche) {
        return ActionReadiness.READY;
    }

    /**
     * Следующая команда действия за этот проход по стадии
     * {@link DealActionState}, либо пустой {@link ActionPlan}, если действие
     * ещё не готово продвигаться / завершено.
     */
    ActionPlan next(StrategyStep step, StrategyAction action, DealActionState state, DealContext dealContext,
                    DealTranche tranche);
}
