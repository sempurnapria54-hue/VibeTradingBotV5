package com.example.tradingbot.domain.command.calc;

import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Оркестратор расчёта параметров действия стратегии
 * (docs/components/StrategyActionCalculator.md): собирает свежий
 * CalculationContext и объединяет PriceCalculator + SizeCalculator в
 * готовые параметры команды. RiskValidator напрямую не зовёт, команд не
 * создаёт. Контролируемые ошибки расчёта возвращает как ERROR-результат;
 * unexpected exceptions не оборачивает — они ловятся на границе FSM
 * (RuntimeErrorCode).
 */
@Component
@RequiredArgsConstructor
public class StrategyActionCalculator {

    private final CalculationContextFactory contextFactory;
    private final PriceCalculator priceCalculator;
    private final SizeCalculator sizeCalculator;

    public StrategyActionCalculationResult calculate(StrategyAction action, DealContext dealContext) {
        try {
            CalculationContext context = contextFactory.build(action, dealContext);
            CalculatedPrice price = priceCalculator.calculate(context);
            CalculatedSize size = sizeCalculator.calculate(context, price);
            CalculatedStrategyAction calculated = CalculatedStrategyAction.builder()
                    .sourceAction(action)
                    .calculatedPrice(price)
                    .calculatedSize(size)
                    .description("calculated " + action.getActionType() + " for action " + action.getKey())
                    .build();
            return StrategyActionCalculationResult.success(calculated);
        } catch (CalculationException e) {
            return StrategyActionCalculationResult.error(e.getError());
        }
    }
}
