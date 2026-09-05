package com.example.tradingbot.domain.service.market.indicator;

import static java.util.stream.Collectors.toList;

import com.example.tradingbot.domain.util.DomainMath;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.BollingerBandsParams;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.IndicatorParams;
import com.example.tradingbot.domain.model.trade.candle.Candle;
import com.example.tradingbot.domain.model.trade.indicator.BollingerBandsValue;
import com.example.tradingbot.domain.model.trade.indicator.IndicatorValue;
import com.example.tradingbot.util.Constants;
import com.example.tradingbot.util.IndicatorMath;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Вычислитель полос Боллинджера: средняя = SMA(period) по close, границы
 * = средняя ± multiplier·σ (популяционное СКО окна); bandwidth =
 * (upper − lower)/middle; %B = (close − lower)/(upper − lower).
 * warmup ≈ period.
 */
@Component
public class BollingerBandsCalculator implements IndicatorCalculator {

    private static final BigDecimal HALF = BigDecimal.valueOf(5L, 1);

    @Override
    public IndicatorValue.Type getType() {
        return IndicatorValue.Type.BOLLINGER_BANDS;
    }

    @Override
    public List<IndicatorValue> calculate(Long instrumentId, Long indicatorConfigId, List<Candle> closedCandles,
                                          IndicatorParams params) {
        BollingerBandsParams bbParams = (BollingerBandsParams) params;
        int period = bbParams.getPeriod();
        BigDecimal multiplier = bbParams.getDeviationMultiplier();
        int warmup = effectiveWarmup(bbParams.getWarmup(), period);
        List<IndicatorValue> result = new ArrayList<>();
        if (closedCandles.size() < period) {
            return result;
        }
        List<BigDecimal> closes = closedCandles.stream().map(Candle::getClose).collect(toList());

        for (int index = Math.max(period - 1, warmup); index < closedCandles.size(); index++) {
            int from = index - period + 1;
            BigDecimal middle = IndicatorMath.sma(closes, from, period);
            BigDecimal deviation = IndicatorMath.populationStdDev(closes, from, period, middle).multiply(multiplier);
            BigDecimal upper = middle.add(deviation);
            BigDecimal lower = middle.subtract(deviation);
            BollingerBandsValue value = new BollingerBandsValue();
            value.setInstrumentId(instrumentId);
            value.setIndicatorConfigId(indicatorConfigId);
            value.setCandleTimestamp(candleTimestamp(closedCandles.get(index)));
            value.setUpperBand(upper);
            value.setMiddleBand(middle);
            value.setLowerBand(lower);
            value.setBandwidth(bandwidth(upper, lower, middle));
            value.setPercentB(percentB(closes.get(index), upper, lower));
            result.add(value);
        }
        return result;
    }

    private BigDecimal bandwidth(BigDecimal upper, BigDecimal lower, BigDecimal middle) {
        if (middle.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return upper.subtract(lower).divide(middle, DomainMath.CONTEXT);
    }

    private BigDecimal percentB(BigDecimal close, BigDecimal upper, BigDecimal lower) {
        BigDecimal range = upper.subtract(lower);
        if (range.signum() == 0) {
            return HALF;
        }
        return close.subtract(lower).divide(range, DomainMath.CONTEXT);
    }
}
