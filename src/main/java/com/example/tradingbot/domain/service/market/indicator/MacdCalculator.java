package com.example.tradingbot.domain.service.market.indicator;

import static java.util.Objects.isNull;
import static java.util.stream.Collectors.toList;

import com.example.tradingbot.domain.model.aggregate.strategy.setting.IndicatorParams;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.MacdParams;
import com.example.tradingbot.domain.model.trade.candle.Candle;
import com.example.tradingbot.domain.model.trade.indicator.IndicatorValue;
import com.example.tradingbot.domain.model.trade.indicator.MacdValue;
import com.example.tradingbot.util.IndicatorMath;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Вычислитель MACD: линия = EMA(fast) − EMA(slow) по close, сигнальная =
 * EMA(signal) линии, гистограмма = линия − сигнальная. warmup ≈ slow +
 * signal (сигнальная требует прогрева поверх медленной EMA).
 */
@Component
public class MacdCalculator implements IndicatorCalculator {

    @Override
    public IndicatorValue.Type getType() {
        return IndicatorValue.Type.MACD;
    }

    @Override
    public List<IndicatorValue> calculate(Long instrumentId, Long strategyIndicatorSettingId, List<Candle> closedCandles,
                                          IndicatorParams params) {
        MacdParams macdParams = (MacdParams) params;
        int fast = macdParams.getFastPeriod();
        int slow = macdParams.getSlowPeriod();
        int signal = macdParams.getSignalPeriod();
        int warmup = effectiveWarmup(macdParams.getWarmup(), slow + signal);
        List<IndicatorValue> result = new ArrayList<>();
        if (closedCandles.size() < slow) {
            return result;
        }
        List<BigDecimal> closes = closedCandles.stream().map(Candle::getClose).collect(toList());
        BigDecimal[] emaFast = IndicatorMath.emaSeries(closes, fast);
        BigDecimal[] emaSlow = IndicatorMath.emaSeries(closes, slow);

        int macdStart = slow - 1;
        List<BigDecimal> macdLine = new ArrayList<>();
        for (int index = macdStart; index < closedCandles.size(); index++) {
            macdLine.add(emaFast[index].subtract(emaSlow[index]));
        }
        BigDecimal[] signalCompact = IndicatorMath.emaSeries(macdLine, signal);

        for (int index = macdStart; index < closedCandles.size(); index++) {
            int compact = index - macdStart;
            BigDecimal signalLine = signalCompact[compact];
            if (index < warmup || isNull(signalLine)) {
                continue;
            }
            BigDecimal macd = macdLine.get(compact);
            MacdValue value = new MacdValue();
            value.setInstrumentId(instrumentId);
            value.setStrategyIndicatorSettingId(strategyIndicatorSettingId);
            value.setCandleTimestamp(candleTimestamp(closedCandles.get(index)));
            value.setMacdLine(macd);
            value.setSignalLine(signalLine);
            value.setHistogram(macd.subtract(signalLine));
            result.add(value);
        }
        return result;
    }
}
