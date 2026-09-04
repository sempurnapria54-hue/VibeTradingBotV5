package com.example.tradingbot.domain.deal;

import com.example.tradingbot.domain.command.ServiceCommand;
import com.example.tradingbot.domain.command.calc.CalculationError;
import com.example.tradingbot.domain.command.risk.RiskBlockAction;
import lombok.Value;

/**
 * Исход планирования одного StrategyAction за проход: ровно одна из веток —
 * команда к исполнению, risk-block (реакция определяется resolver'ом),
 * либо ошибка расчёта. Все поля {@code null} = нечего делать этим проходом.
 * RVO, потребляется FSM handler'ом.
 */
@Value
public class ActionPlan {

    /** Команда к исполнению этим проходом (null, если действие заблокировано / ошибка / нечего делать). */
    ServiceCommand command;

    /** Реакция risk-layer при блокировке (null, если не блокировано). */
    RiskBlockAction blockAction;

    /** Ошибка расчёта (null, если расчёт успешен). */
    CalculationError calcError;

    public static ActionPlan command(ServiceCommand command) {
        return new ActionPlan(command, null, null);
    }

    public static ActionPlan blocked(RiskBlockAction blockAction) {
        return new ActionPlan(null, blockAction, null);
    }

    public static ActionPlan calcError(CalculationError calcError) {
        return new ActionPlan(null, null, calcError);
    }

    public static ActionPlan empty() {
        return new ActionPlan(null, null, null);
    }
}
