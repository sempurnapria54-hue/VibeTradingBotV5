package com.example.marketdata.util;

import java.math.BigDecimal;
import java.util.List;
import lombok.experimental.UtilityClass;
import com.example.tradingbot.domain.util.DomainMath;

/**
 * Числовые помощники расчёта индикаторов (переиспользуемые между
 * вычислителями): экспоненциальная скользящая, простая скользящая,
 * популяционное СКО. Точная арифметика — деталь реализации (CODE).
 * Утилитный класс — живёт в пакете util (конвенция проекта).
 */
@UtilityClass
public class IndicatorMath {

    private static final BigDecimal TWO = BigDecimal.valueOf(2L);

    /**
     * Серия EMA по значениям: индексы до (period-1) — null (разгон),
     * seed на (period-1) = SMA первых period значений, далее рекурсивно
     * EMA[i] = EMA[i-1] + k·(value[i] − EMA[i-1]), k = 2/(period+1).
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

    /** SMA окна [from, from+period) по значениям. */
    public static BigDecimal sma(List<BigDecimal> values, int from, int period) {
        BigDecimal sum = BigDecimal.ZERO;
        for (int index = from; index < from + period; index++) {
            sum = sum.add(values.get(index));
        }
        return sum.divide(BigDecimal.valueOf(period), DomainMath.CONTEXT);
    }

    /** Популяционное СКО окна [from, from+period) с заранее посчитанным средним. */
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
