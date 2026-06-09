package com.example.tradingbot.domain.service.market.indicator;

import com.example.tradingbot.domain.model.aggregate.strategy.setting.AtrParams;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.IndicatorParams;
import com.example.tradingbot.domain.model.trade.candle.Candle;
import com.example.tradingbot.domain.model.trade.indicator.AtrValue;
import com.example.tradingbot.domain.model.trade.indicator.IndicatorValue;
import com.example.tradingbot.util.Constants;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Вычислитель ATR (true range, сглаживание Wilder). warmup ≈ 2·period. */
@Component
public class AtrCalculator implements IndicatorCalculator {

    @Override
    public IndicatorValue.Type getType() {
        return IndicatorValue.Type.ATR;
    }

    @Override
    public List<IndicatorValue> calculate(Long instrumentId, Long configId, List<Candle> closedCandles,
                                          IndicatorParams params) {
        AtrParams atrParams = (AtrParams) params;
        int period = atrParams.getPeriod();
        int warmup = effectiveWarmup(atrParams.getWarmup(), 2 * period);
        List<IndicatorValue> result = new ArrayList<>();
        if (closedCandles.size() < period) {
            return result;
        }
        BigDecimal[] trueRange = trueRanges(closedCandles);
        BigDecimal periodValue = BigDecimal.valueOf(period);

        BigDecimal seedSum = BigDecimal.ZERO;
        for (int index = 0; index < period; index++) {
            seedSum = seedSum.add(trueRange[index]);
        }
        BigDecimal atr = seedSum.divide(periodValue, Constants.Calc.MATH_CONTEXT);
        emit(result, instrumentId, configId, closedCandles, period - 1, warmup, atr);

        for (int index = period; index < closedCandles.size(); index++) {
            atr = atr.multiply(periodValue.subtract(BigDecimal.ONE)).add(trueRange[index])
                    .divide(periodValue, Constants.Calc.MATH_CONTEXT);
            emit(result, instrumentId, configId, closedCandles, index, warmup, atr);
        }
        return result;
    }

    private BigDecimal[] trueRanges(List<Candle> candles) {
        BigDecimal[] trueRange = new BigDecimal[candles.size()];
        trueRange[0] = candles.get(0).getHigh().subtract(candles.get(0).getLow());
        for (int index = 1; index < candles.size(); index++) {
            Candle candle = candles.get(index);
            BigDecimal previousClose = candles.get(index - 1).getClose();
            BigDecimal highLow = candle.getHigh().subtract(candle.getLow());
            BigDecimal highClose = candle.getHigh().subtract(previousClose).abs();
            BigDecimal lowClose = candle.getLow().subtract(previousClose).abs();
            trueRange[index] = highLow.max(highClose).max(lowClose);
        }
        return trueRange;
    }

    private void emit(List<IndicatorValue> result, Long instrumentId, Long configId, List<Candle> candles,
                      int index, int warmup, BigDecimal atr) {
        if (index < warmup) {
            return;
        }
        AtrValue value = new AtrValue();
        value.setInstrumentId(instrumentId);
        value.setConfigId(configId);
        value.setCandleTimestamp(candleTimestamp(candles.get(index)));
        value.setAtr(atr);
        result.add(value);
    }
}
