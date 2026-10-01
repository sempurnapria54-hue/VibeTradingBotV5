package com.example.marketdata.domain.service.indicator;

import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.toList;

import com.example.tradingbot.domain.model.aggregate.strategy.setting.EmaParams;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.IndicatorParams;
import com.example.tradingbot.domain.model.trade.candle.Candle;
import com.example.tradingbot.domain.model.trade.indicator.EmaValue;
import com.example.tradingbot.domain.model.trade.indicator.IndicatorValue;
import com.example.marketdata.util.IndicatorMath;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Вычислитель EMA. Затравка, шаг сглаживания и выведенный прогрев —
 * docs/spec/indicator-calculation.json (`indicatorWindowAverage`,
 * `emaNext`, `derivedWarmup`).
 */
@Component
public class EmaCalculator implements IndicatorCalculator {

    /** Кратность периода в выведенном прогреве EMA (`derivedWarmup`). */
    private static final int EMA_WARMUP_PERIODS = 2;

    @Override
    public IndicatorValue.Type getType() {
        return IndicatorValue.Type.EMA;
    }

    @Override
    public List<IndicatorValue> calculate(Long instrumentId, Long indicatorConfigId, List<Candle> closedCandles,
                                          IndicatorParams params) {
        EmaParams emaParams = (EmaParams) params;
        int period = emaParams.getPeriod();
        int warmup = effectiveWarmup(emaParams.getWarmup(), EMA_WARMUP_PERIODS * period);
        List<BigDecimal> closes = closedCandles.stream().map(Candle::getClose).collect(toList());
        BigDecimal[] ema = IndicatorMath.emaSeries(closes, period);
        List<IndicatorValue> result = new ArrayList<>();
        for (int index = warmup; index < closedCandles.size(); index++) {
            if (nonNull(ema[index])) {
                EmaValue value = new EmaValue();
                value.setInstrumentId(instrumentId);
                value.setIndicatorConfigId(indicatorConfigId);
                value.setCandleTimestamp(candleTimestamp(closedCandles.get(index)));
                value.setEma(ema[index]);
                result.add(value);
            }
        }
        return result;
    }
}
