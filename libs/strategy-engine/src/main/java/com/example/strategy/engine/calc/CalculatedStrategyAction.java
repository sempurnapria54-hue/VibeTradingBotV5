package com.example.strategy.engine.calc;

import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAction;
import lombok.Builder;
import lombok.Value;

/**
 * Результат УСПЕШНОГО расчёта параметров действия стратегии
 * (docs/components/models/CalculatedStrategyAction.md): объявление
 * действия вместе с посчитанными по нему ценой и размером.
 *
 * <p><b>Результата риск-проверки здесь нет.</b> Расчёт отвечает «сколько и
 * почём», преконтроль — «разрешено ли»; метрики решения преконтроль
 * считает сам, и своего носителя в этом значении они не получают
 * (docs/components/RiskValidator.md).
 *
 * <p>Живёт только в памяти прохода — читателя за сериализацией у значения
 * нет, поэтому неизменяемая форма здесь дефолт
 * (.claude/rules/codestyle.md §«Неизменяемое значение, пересекающее
 * сериализацию»).
 */
@Value
@Builder
public class CalculatedStrategyAction {

    /** Исходное действие стратегии, по которому шёл расчёт. */
    StrategyAction sourceAction;

    /** Рассчитанная цена либо набор цен действия. */
    CalculatedPrice calculatedPrice;

    /** Рассчитанный размер действия. */
    CalculatedSize calculatedSize;

    /** Пояснение расчёта для логов и разбора. */
    String description;
}
