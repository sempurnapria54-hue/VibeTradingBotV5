package com.example.tradingcore.domain.command.strategy;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import com.example.strategy.engine.calc.CalculationError;
import com.example.tradingcore.domain.command.ServiceCommand;
import com.example.tradingcore.domain.command.risk.RiskBlockAction;
import lombok.Value;

/**
 * Исход прохода по одному действию стратегии: следующая команда, реакция
 * преконтроля либо контролируемая ошибка расчёта
 * (docs/components/StrategyActionExecutor.md).
 *
 * <p><b>Три исхода взаимоисключающи, и пустой — четвёртый.</b> Пустой план
 * означает «готово или нечего делать этим проходом»: стадия действия
 * ждёт подтверждённого факта, которого ещё нет. Он не ошибка и не
 * бездействие — секвенс ведёт петля по фактам, а не по счётчику проходов.
 *
 * <p><b>Реакцию исполняет обработчик, а не исполнитель типа.</b> Здесь
 * она только доносится: род реакции решает карта резолвера, а что с ней
 * делать — машина состояний
 * (docs/components/RiskBlockResolver.md).
 *
 * <p>Живёт только в памяти прохода — читателя за сериализацией у значения
 * нет (.claude/rules/codestyle.md §«Неизменяемое значение, пересекающее
 * сериализацию»).
 */
@Value
public class ActionPlan {

    /** Следующая команда действия; пусто — команды этим проходом нет. */
    ServiceCommand command;

    /** Блокирующая реакция преконтроля; пусто — преконтроль не блокировал. */
    RiskBlockAction blocked;

    /** Контролируемая ошибка расчёта; пусто — расчёт не отказывал. */
    CalculationError calculationError;

    private ActionPlan(ServiceCommand command, RiskBlockAction blocked, CalculationError calculationError) {
        this.command = command;
        this.blocked = blocked;
        this.calculationError = calculationError;
    }

    /** План с командой: действие продвигается. */
    public static ActionPlan of(ServiceCommand command) {
        return new ActionPlan(command, null, null);
    }

    /** План с реакцией преконтроля: действие не исполняется, реакцию ведёт обработчик. */
    public static ActionPlan blocked(RiskBlockAction blocked) {
        return new ActionPlan(null, blocked, null);
    }

    /** План с ошибкой расчёта: параметров действия не существует. */
    public static ActionPlan calculationFailed(CalculationError error) {
        return new ActionPlan(null, null, error);
    }

    /** Пустой план: готово либо нечего делать этим проходом. */
    public static ActionPlan nothing() {
        return new ActionPlan(null, null, null);
    }

    /** План несёт команду к диспетчеризации. */
    public Boolean hasCommand() {
        return nonNull(command);
    }

    /** План несёт блокирующую реакцию преконтроля. */
    public Boolean isBlocked() {
        return nonNull(blocked);
    }

    /** План несёт контролируемую ошибку расчёта. */
    public Boolean hasCalculationError() {
        return nonNull(calculationError);
    }

    /** Плану нечего сказать: ни команды, ни реакции, ни ошибки. */
    public Boolean isEmpty() {
        return isNull(command) && isNull(blocked) && isNull(calculationError);
    }
}
