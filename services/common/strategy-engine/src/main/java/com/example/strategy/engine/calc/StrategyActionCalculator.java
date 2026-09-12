package com.example.strategy.engine.calc;

import static java.util.Objects.isNull;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Оркеструет расчёт параметров одного действия стратегии: цена, затем
 * размер (docs/components/StrategyActionCalculator.md,
 * docs/processes/strategy-action-calculation.md).
 *
 * <p><b>Порядок несущий:</b> размер по риску считается от расстояния до
 * уровня остановки убытка, то есть от уже посчитанной цены. Обратный
 * порядок потребовал бы считать цену дважды.
 *
 * <p><b>Контекст приезжает готовым, а не собирается здесь.</b> Все его
 * операнды — живое состояние счёта, граф сделки и свежие рыночные
 * данные — резолвит тот, кто держит персистентность; библиотека к базе не
 * ходит, и ровно на этом стои́т её пригодность для бэктеста
 * (docs/architecture/services.md §«Что в библиотеку НЕ уезжает»).
 * Собирающий обязан отдавать <b>свежий</b> контекст на КАЖДОЕ действие:
 * после каждого исполненного действия меняются и заявки, и позиция, и
 * цены (docs/components/models/CalculationContext.md §«Scope сборки»).
 *
 * <p><b>Контракт возвратный, а не бросковый.</b> Субкалькуляторы бросают
 * {@link CalculationException}; здесь оно перехватывается и становится
 * результатом-ошибкой. Неожиданные исключения так не оборачиваются — их
 * ловит граница исполнения прохода
 * (docs/rules/runtime-error-classification.md).
 *
 * <p><b>Решений о применимости действия не принимает</b> — их принимает
 * обработчик; команд не эмитит — их строит исполнитель типа действия.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StrategyActionCalculator {

    private static final String MISSING_CALCULATION_CONTEXT = "MISSING_CALCULATION_CONTEXT";

    private final PriceCalculator priceCalculator;
    private final SizeCalculator sizeCalculator;

    /** Параметры действия: успех с рассчитанным действием либо контролируемая ошибка. */
    public StrategyActionCalculationResult calculate(CalculationContext context) {
        if (isNull(context) || isNull(context.getAction())) {
            return StrategyActionCalculationResult.error(CalculationError.permanent(
                    MISSING_CALCULATION_CONTEXT, "Calculation context carries no action"));
        }
        try {
            CalculatedPrice price = priceCalculator.calculate(context);
            CalculatedSize size = sizeCalculator.calculate(context, price);
            return StrategyActionCalculationResult.success(CalculatedStrategyAction.builder()
                    .sourceAction(context.getAction())
                    .calculatedPrice(price)
                    .calculatedSize(size)
                    .description(describe(price, size))
                    .build());
        } catch (CalculationException e) {
            log.warn("Action calculation refused actionKey={} code={} type={}",
                    context.getAction().getKey(), e.getError().getCode(), e.getError().getType());
            return StrategyActionCalculationResult.error(e.getError());
        }
    }

    /**
     * Пояснение расчёта — склейка пояснений обеих половин.
     *
     * <p>Своего текста оркестратор не сочиняет: числа считали они, и
     * пересказ развёлся бы с ними при первой правке формулы.
     */
    private String describe(CalculatedPrice price, CalculatedSize size) {
        return String.join("; ", nullSafe(price.getDescription()), nullSafe(size.getDescription()));
    }

    private String nullSafe(String description) {
        return isNull(description) ? "" : description;
    }
}
