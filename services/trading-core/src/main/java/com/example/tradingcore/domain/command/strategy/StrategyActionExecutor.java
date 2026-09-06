package com.example.tradingcore.domain.command.strategy;

import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStep;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAction;
import com.example.tradingcore.domain.command.DealActionState;
import com.example.tradingcore.domain.command.DealContext;

/**
 * Исполнитель одного типа действия стратегии: за проход смотрит стадию
 * исполнения и выдаёт <b>следующую</b> команду либо пустой план
 * (docs/components/StrategyActionExecutor.md).
 *
 * <p><b>Стадия выводится из подтверждённых фактов</b>, а не из счётчика
 * проходов: создать, отправить, дождаться факта, идти дальше
 * (docs/processes/fsm-execution-layering.md).
 *
 * <p><b>Границы.</b> Статус исполнения сам не пишет — его двигают
 * исполнители команд и учёт повторов; условие шага не проверяет — это
 * сделал обработчик; более одной команды за проход не выдаёт.
 */
public interface StrategyActionExecutor {

    /** Исполняет ли этот исполнитель данное действие — по подтипу и типу действия. */
    Boolean supports(StrategyAction action);

    /**
     * Гейт предусловия действия — <b>до</b> строки исполнения.
     *
     * <p>Умолчание {@code READY} переопределяет тот исполнитель, у чьего
     * действия предусловие есть.
     */
    default ActionReadiness readiness(StrategyAction action, DealContext dealContext, DealTranche tranche) {
        return ActionReadiness.READY;
    }

    /** Следующая команда действия за проход либо пустой план. */
    ActionPlan next(StrategyStep step, StrategyAction action, DealActionState state,
                    DealContext dealContext, DealTranche tranche);
}
