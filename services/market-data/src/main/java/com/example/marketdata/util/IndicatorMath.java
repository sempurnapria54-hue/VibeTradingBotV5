package com.example.marketdata.util;

import java.math.BigDecimal;
import java.util.List;
import lombok.experimental.UtilityClass;
import com.example.tradingbot.domain.util.DomainMath;

/**
 * Числовые помощники расчёта индикаторов, общие для вычислителей: серия
 * EMA, простое среднее окна, популяционное отклонение. Формы —
 * docs/spec/indicator-calculation.json (`indicatorWindowAverage`, `emaNext`,
 * `bollingerVariance`); точность промежуточного деления —
 * docs/rules/decimal-arithmetic.md.
 */
@UtilityClass
public class IndicatorMath {

    private static final BigDecimal TWO = BigDecimal.valueOf(2L);

    /**
     * Серия EMA, выровненная по входу: члены до затравки пусты, затравка
     * стоит на члене {@code period - 1}, дальше — шаг `emaNext`.
     */
    public static BigDecimal[] emaSeries(List<BigDecimal> values, int period) {
        BigDecimal[] result = new BigDecimal[values.size()];
        if (values.size() < period) {
            return result;
        }
        BigDecimal multiplier = TWO.divide(BigDecimal.valueOf(period + 1L), DomainMath.CONTEXT);
        BigDecimal sum = BigDecimal.ZERO;
        for (int index = 0; index < period; index++) {
            sum = sum.add(values.get(index));
        }
        BigDecimal ema = sum.divide(BigDecimal.valueOf(period), DomainMath.CONTEXT);
        result[period - 1] = ema;
        for (int index = period; index < values.size(); index++) {
            ema = ema.add(values.get(index).subtract(ema).multiply(multiplier));
            result[index] = ema;
        }
        return result;
    }

    /** Простое среднее окна {@code [from, from + period)} (`indicatorWindowAverage`). */
    public static BigDecimal sma(List<BigDecimal> values, int from, int period) {
        BigDecimal sum = BigDecimal.ZERO;
        for (int index = from; index < from + period; index++) {
            sum = sum.add(values.get(index));
        }
        return sum.divide(BigDecimal.valueOf(period), DomainMath.CONTEXT);
    }

    /** Корень популяционной дисперсии окна {@code [from, from + period)} (`bollingerVariance`). */
    public static BigDecimal populationStdDev(List<BigDecimal> values, int from, int period, BigDecimal mean) {
        BigDecimal sumSquares = BigDecimal.ZERO;
        for (int index = from; index < from + period; index++) {
            BigDecimal diff = values.get(index).subtract(mean);
            sumSquares = sumSquares.add(diff.multiply(diff));
        }
        BigDecimal variance = sumSquares.divide(BigDecimal.valueOf(period), DomainMath.CONTEXT);
        return variance.sqrt(DomainMath.CONTEXT);
    }
}
