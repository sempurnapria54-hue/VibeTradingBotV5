package com.example.tradingbot.domain.service.market.indicator;

import static java.util.Objects.isNull;

import com.example.tradingbot.domain.model.aggregate.strategy.setting.IndicatorParams;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.ObvParams;
import com.example.tradingbot.domain.model.trade.candle.Candle;
import com.example.tradingbot.domain.model.trade.indicator.IndicatorValue;
import com.example.tradingbot.domain.model.trade.indicator.ObvValue;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Вычислитель OBV (On-Balance Volume): кумулятивная знаковая сумма
 * объёма — +volume при росте close, −volume при падении, 0 при
 * равенстве. Кумулятивен от старта истории; warmup = 1 (нужна
 * предыдущая свеча). Абсолютный уровень нестабилен — операнд ограничен
 * относительными формами (docs/decisions/volume-condition-semantics.md).
 */
@Component
public class ObvCalculator implements IndicatorCalculator {

    @Override
    public IndicatorValue.Type getType() {
        return IndicatorValue.Type.OBV;
    }

    @Override
    public List<IndicatorValue> calculate(Long instrumentId, Long strategyIndicatorSettingId, List<Candle> closedCandles,
                                          IndicatorParams params) {
        ObvParams obvParams = (ObvParams) params;
        int warmup = effectiveWarmup(obvParams.getWarmup(), 1);
        List<IndicatorValue> result = new ArrayList<>();
        if (closedCandles.size() < 2) {
            return result;
        }
        BigDecimal obv = BigDecimal.ZERO;
        for (int index = 1; index < closedCandles.size(); index++) {
            BigDecimal close = closedCandles.get(index).getClose();
            BigDecimal previousClose = closedCandles.get(index - 1).getClose();
            BigDecimal volume = volumeOf(closedCandles.get(index));
            int direction = close.compareTo(previousClose);
            if (direction > 0) {
                obv = obv.add(volume);
            } else if (direction < 0) {
                obv = obv.subtract(volume);
            }
            if (index >= warmup) {
                ObvValue value = new ObvValue();
                value.setInstrumentId(instrumentId);
                value.setStrategyIndicatorSettingId(strategyIndicatorSettingId);
                value.setCandleTimestamp(candleTimestamp(closedCandles.get(index)));
                value.setObv(obv);
                result.add(value);
            }
        }
        return result;
    }

    private BigDecimal volumeOf(Candle candle) {
        return isNull(candle.getVolume()) ? BigDecimal.ZERO : candle.getVolume();
    }
}
