package com.example.strategy.engine.calc.util;

import lombok.experimental.UtilityClass;

/**
 * Общий дом констант кодов контролируемых ошибок расчёта. Перечень кодов
 * и признак, по которому константа кода лежит здесь, а не у своего
 * калькулятора, — docs/components/models/CalculationError.md.
 */
@UtilityClass
public class CalculationErrorCodes {

    /**
     * Уровень остановки не на убыточной стороне якоря: сайзить по нему
     * нечего. Исход — неисполнение шага, а не авария прохода: живой риск
     * при этом под контролем.
     */
    public static final String STOP_LEVEL_NOT_ON_LOSS_SIDE_FOR_SIZING =
            "STOP_LEVEL_NOT_ON_LOSS_SIDE_FOR_SIZING";

    /**
     * Ступень защитной лестницы меньше минимального торгового размера:
     * лестница из объявленного числа ступеней на этой экспозиции
     * невыразима. Исход — <b>отказ шага</b>, а не авария сделки: прежняя
     * защита при этом жива, и увод сделки в аварийный контур закрыл бы по
     * рынку позицию, риск которой под контролем
     * (docs/processes/risk-evaluation.md §«Карв-аут исчерпанного бюджета
     * сделки»). Отказ при этом ВИДИМЫЙ: строка исполнения уходит в
     * {@code FAILED} (docs/components/SizeCalculator.md).
     */
    public static final String PROTECTION_LADDER_STEP_BELOW_MIN_SIZE =
            "PROTECTION_LADDER_STEP_BELOW_MIN_SIZE";

    /**
     * Ставка комиссии счёта не наблюдена: считать издержку нечем. Читают
     * код обе половины расчёта — цена и размер.
     */
    public static final String FEE_RATE_UNAVAILABLE = "FEE_RATE_UNAVAILABLE";
}
