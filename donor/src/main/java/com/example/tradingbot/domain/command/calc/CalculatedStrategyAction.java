package com.example.tradingbot.domain.command.calc;

import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAction;
import lombok.Builder;
import lombok.Value;

/**
 * Результат успешного расчёта параметров действия стратегии — вход
 * StrategyActionExecutor'а (эмиссия CREATE-команды). Risk-policy в него не
 * входит. Производит StrategyActionCalculator (шаг 5); здесь — контракт,
 * потребляемый per-type executor'ом действия. RVO. См.
 * docs/components/models/CalculatedStrategyAction.md.
 */
@Value
@Builder
public class CalculatedStrategyAction {

    /** Исходное действие стратегии. */
    StrategyAction sourceAction;

    /** Рассчитанная цена / набор цен. */
    CalculatedPrice calculatedPrice;

    /** Рассчитанный размер. */
    CalculatedSize calculatedSize;

    /** Комментарий расчёта для логов/аудита. */
    String description;
}
