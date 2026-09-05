package com.example.tradingbot.domain.service.market.indicator;

import static java.util.stream.Collectors.toList;

import com.example.tradingbot.domain.util.DomainMath;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.IndicatorParams;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.RsiParams;
import com.example.tradingbot.domain.model.trade.candle.Candle;
import com.example.tradingbot.domain.model.trade.indicator.IndicatorValue;
import com.example.tradingbot.domain.model.trade.indicator.RsiValue;
import com.example.tradingbot.util.Constants;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Вычислитель RSI (Wilder smoothing) по close. warmup ≈ 2·period. */
@Component
public class RsiCalculator implements IndicatorCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100L);

    @Override
    public IndicatorValue.Type getType() {
        return IndicatorValue.Type.RSI;
    }

    @Override
    public List<IndicatorValue> calculate(Long instrumentId, Long indicatorConfigId, List<Candle> closedCandles,
                                          IndicatorParams params) {
        RsiParams rsiParams = (RsiParams) params;
        int period = rsiParams.getPeriod();
        int warmup = effectiveWarmup(rsiParams.getWarmup(), 2 * period);
        List<IndicatorValue> result = new ArrayList<>();
        if (closedCandles.size() <= period) {
            return result;
        }
        List<BigDecimal> closes = closedCandles.stream().map(Candle::getClose).collect(toList());
        BigDecimal periodValue = BigDecimal.valueOf(period);

        BigDecimal gainSum = BigDecimal.ZERO;
        BigDecimal lossSum = BigDecimal.ZERO;
        for (int index = 1; index <= period; index++) {
            BigDecimal change = closes.get(index).subtract(closes.get(index - 1));
            gainSum = gainSum.add(change.max(BigDecimal.ZERO));
            lossSum = lossSum.add(change.negate().max(BigDecimal.ZERO));
        }
        BigDecimal avgGain = gainSum.divide(periodValue, DomainMath.CONTEXT);
        BigDecimal avgLoss = lossSum.divide(periodValue, DomainMath.CONTEXT);
        emit(result, instrumentId, indicatorConfigId, closedCandles, period, warmup, rsi(avgGain, avgLoss));

        for (int index = period + 1; index < closedCandles.size(); index++) {
            BigDecimal change = closes.get(index).subtract(closes.get(index - 1));
            BigDecimal gain = change.max(BigDecimal.ZERO);
            BigDecimal loss = change.negate().max(BigDecimal.ZERO);
            avgGain = avgGain.multiply(periodValue.subtract(BigDecimal.ONE)).add(gain)
                    .divide(periodValue, DomainMath.CONTEXT);
            avgLoss = avgLoss.multiply(periodValue.subtract(BigDecimal.ONE)).add(loss)
                    .divide(periodValue, DomainMath.CONTEXT);
            emit(result, instrumentId, indicatorConfigId, closedCandles, index, warmup, rsi(avgGain, avgLoss));
        }
        return result;
    }

    private BigDecimal rsi(BigDecimal avgGain, BigDecimal avgLoss) {
        if (avgLoss.signum() == 0) {
            return HUNDRED;
        }
        BigDecimal rs = avgGain.divide(avgLoss, DomainMath.CONTEXT);
        return HUNDRED.subtract(HUNDRED.divide(BigDecimal.ONE.add(rs), DomainMath.CONTEXT));
    }

    private void emit(List<IndicatorValue> result, Long instrumentId, Long indicatorConfigId, List<Candle> candles,
                      int index, int warmup, BigDecimal rsi) {
        if (index < warmup) {
            return;
        }
        RsiValue value = new RsiValue();
        value.setInstrumentId(instrumentId);
        value.setIndicatorConfigId(indicatorConfigId);
        value.setCandleTimestamp(candleTimestamp(candles.get(index)));
        value.setRsi(rsi);
        result.add(value);
    }
}
