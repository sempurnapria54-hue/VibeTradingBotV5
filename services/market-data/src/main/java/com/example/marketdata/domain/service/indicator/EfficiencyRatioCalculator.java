package com.example.marketdata.domain.service.indicator;

import static java.util.stream.Collectors.toList;

import com.example.tradingbot.domain.util.DomainMath;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.EfficiencyRatioParams;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.IndicatorParams;
import com.example.tradingbot.domain.model.trade.candle.Candle;
import com.example.tradingbot.domain.model.trade.indicator.EfficiencyRatioValue;
import com.example.tradingbot.domain.model.trade.indicator.IndicatorValue;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Вычислитель Kaufman efficiency ratio: ER = |close[i] − close[i−period]|
 * / Σ|побарных изменений| по окну period. ER→1 — тренд, ER→0 — шум/боковик
 * (нормирован по определению). warmup ≈ period. См.
 * docs/decisions/efficiency-ratio-as-catalog-indicator.md.
 */
@Component
public class EfficiencyRatioCalculator implements IndicatorCalculator {

    @Override
    public IndicatorValue.Type getType() {
        return IndicatorValue.Type.EFFICIENCY_RATIO;
    }

    @Override
    public List<IndicatorValue> calculate(Long instrumentId, Long indicatorConfigId, List<Candle> closedCandles,
                                          IndicatorParams params) {
        EfficiencyRatioParams erParams = (EfficiencyRatioParams) params;
        int period = erParams.getPeriod();
        int warmup = effectiveWarmup(erParams.getWarmup(), period);
        List<IndicatorValue> result = new ArrayList<>();
        if (closedCandles.size() <= period) {
            return result;
        }
        List<BigDecimal> closes = closedCandles.stream().map(Candle::getClose).collect(toList());

        for (int index = Math.max(period, warmup); index < closedCandles.size(); index++) {
            BigDecimal netMove = closes.get(index).subtract(closes.get(index - period)).abs();
            BigDecimal totalMove = BigDecimal.ZERO;
            for (int offset = index - period + 1; offset <= index; offset++) {
                totalMove = totalMove.add(closes.get(offset).subtract(closes.get(offset - 1)).abs());
            }
            BigDecimal efficiencyRatio = totalMove.signum() == 0 ? BigDecimal.ZERO
                    : netMove.divide(totalMove, DomainMath.CONTEXT);
            EfficiencyRatioValue value = new EfficiencyRatioValue();
            value.setInstrumentId(instrumentId);
            value.setIndicatorConfigId(indicatorConfigId);
            value.setCandleTimestamp(candleTimestamp(closedCandles.get(index)));
            value.setEfficiencyRatio(efficiencyRatio);
            result.add(value);
        }
        return result;
    }
}
