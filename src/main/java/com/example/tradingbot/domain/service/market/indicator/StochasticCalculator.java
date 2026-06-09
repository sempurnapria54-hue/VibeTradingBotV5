package com.example.tradingbot.domain.service.market.indicator;

import static java.util.Objects.nonNull;

import com.example.tradingbot.domain.model.aggregate.strategy.setting.IndicatorParams;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.StochasticParams;
import com.example.tradingbot.domain.model.trade.candle.Candle;
import com.example.tradingbot.domain.model.trade.indicator.IndicatorValue;
import com.example.tradingbot.domain.model.trade.indicator.StochasticValue;
import com.example.tradingbot.util.Constants;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Вычислитель стохастического осциллятора: rawK = 100·(close − llv) /
 * (hhv − llv) по окну kPeriod; %K = SMA(smoothPeriod) от rawK; %D =
 * SMA(dPeriod) от %K. warmup ≈ kPeriod + smoothPeriod + dPeriod.
 */
@Component
public class StochasticCalculator implements IndicatorCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100L);
    private static final BigDecimal FLAT_WINDOW_VALUE = BigDecimal.valueOf(50L);

    @Override
    public IndicatorValue.Type getType() {
        return IndicatorValue.Type.STOCHASTIC;
    }

    @Override
    public List<IndicatorValue> calculate(Long instrumentId, Long configId, List<Candle> closedCandles,
                                          IndicatorParams params) {
        StochasticParams stochasticParams = (StochasticParams) params;
        int kPeriod = stochasticParams.getkPeriod();
        int dPeriod = stochasticParams.getdPeriod();
        int smoothPeriod = stochasticParams.getSmoothPeriod();
        int warmup = effectiveWarmup(stochasticParams.getWarmup(), kPeriod + smoothPeriod + dPeriod);
        List<IndicatorValue> result = new ArrayList<>();
        if (closedCandles.size() < kPeriod) {
            return result;
        }
        BigDecimal[] rawK = rawStochastic(closedCandles, kPeriod);
        BigDecimal[] kLine = smoothedSeries(rawK, smoothPeriod);
        BigDecimal[] dLine = smoothedSeries(kLine, dPeriod);

        for (int index = warmup; index < closedCandles.size(); index++) {
            if (nonNull(kLine[index]) && nonNull(dLine[index])) {
                StochasticValue value = new StochasticValue();
                value.setInstrumentId(instrumentId);
                value.setConfigId(configId);
                value.setCandleTimestamp(candleTimestamp(closedCandles.get(index)));
                value.setK(kLine[index]);
                value.setD(dLine[index]);
                result.add(value);
            }
        }
        return result;
    }

    private BigDecimal[] rawStochastic(List<Candle> candles, int kPeriod) {
        BigDecimal[] rawK = new BigDecimal[candles.size()];
        for (int index = kPeriod - 1; index < candles.size(); index++) {
            BigDecimal highest = candles.get(index).getHigh();
            BigDecimal lowest = candles.get(index).getLow();
            for (int offset = index - kPeriod + 1; offset <= index; offset++) {
                highest = highest.max(candles.get(offset).getHigh());
                lowest = lowest.min(candles.get(offset).getLow());
            }
            BigDecimal range = highest.subtract(lowest);
            rawK[index] = range.signum() == 0 ? FLAT_WINDOW_VALUE
                    : candles.get(index).getClose().subtract(lowest)
                    .multiply(HUNDRED).divide(range, Constants.Calc.MATH_CONTEXT);
        }
        return rawK;
    }

    /** SMA окна period по серии, индексы которой могут быть null до своего разгона. */
    private BigDecimal[] smoothedSeries(BigDecimal[] source, int period) {
        BigDecimal[] result = new BigDecimal[source.length];
        for (int index = period - 1; index < source.length; index++) {
            BigDecimal sum = BigDecimal.ZERO;
            boolean complete = true;
            for (int offset = index - period + 1; offset <= index; offset++) {
                if (nonNull(source[offset])) {
                    sum = sum.add(source[offset]);
                } else {
                    complete = false;
                    break;
                }
            }
            if (complete) {
                result[index] = sum.divide(BigDecimal.valueOf(period), Constants.Calc.MATH_CONTEXT);
            }
        }
        return result;
    }
}
