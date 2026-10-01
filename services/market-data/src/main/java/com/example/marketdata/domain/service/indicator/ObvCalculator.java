package com.example.marketdata.domain.service.indicator;

import static java.util.Objects.isNull;

import com.example.tradingbot.domain.model.aggregate.strategy.setting.IndicatorParams;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.ObvParams;
import com.example.tradingbot.domain.model.trade.candle.Candle;
import com.example.tradingbot.domain.model.trade.indicator.IndicatorValue;
import com.example.tradingbot.domain.model.trade.indicator.ObvValue;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Вычислитель OBV. Затравка, шаг кумулятивной суммы и выведенный прогрев —
 * docs/spec/indicator-calculation.json (`obvSeed`, `obvNext`,
 * `derivedWarmup`); продолжение записанного ряда —
 * docs/components/IndicatorJob.md §«Кумулятивный тип продолжает записанный
 * ряд»; смысл ряда и ограничение операнда — docs/models/domain/other/IndicatorValue.md
 * §«Енум `Type`».
 */
@Component
public class ObvCalculator implements IndicatorCalculator {

    /** Номер бара затравки, когда затравки в окне нет. */
    private static final int NO_SEED = -1;

    @Override
    public IndicatorValue.Type getType() {
        return IndicatorValue.Type.OBV;
    }

    @Override
    public Boolean isCumulative() {
        return true;
    }

    @Override
    public List<IndicatorValue> calculate(Long instrumentId, Long indicatorConfigId, List<Candle> closedCandles,
                                          IndicatorParams params) {
        return calculate(instrumentId, indicatorConfigId, closedCandles, params, null);
    }

    /**
     * Затравка есть — сумма продолжается от неё с бара после её бара, и
     * прогрев эти бары не отсекает: ряд уже разогнан. Затравки нет — сумма
     * начинается нулём на первом баре окна, и значения идут с прогрева.
     */
    @Override
    public List<IndicatorValue> calculate(Long instrumentId, Long indicatorConfigId, List<Candle> closedCandles,
                                          IndicatorParams params, IndicatorValue lastStored) {
        ObvParams obvParams = (ObvParams) params;
        int warmup = effectiveWarmup(obvParams.getWarmup(), 1);
        int seedIndex = seedIndexOf(closedCandles, lastStored);
        boolean seeded = seedIndex != NO_SEED;
        BigDecimal obv = seeded ? ((ObvValue) lastStored).getObv() : BigDecimal.ZERO;
        List<IndicatorValue> result = new ArrayList<>();
        for (int index = Math.max(seedIndex + 1, 1); index < closedCandles.size(); index++) {
            Candle candle = closedCandles.get(index);
            int direction = candle.getClose().compareTo(closedCandles.get(index - 1).getClose());
            if (direction != 0 && isNull(candle.getVolume())) {
                continue;
            }
            if (direction > 0) {
                obv = obv.add(candle.getVolume());
            } else if (direction < 0) {
                obv = obv.subtract(candle.getVolume());
            }
            if (seeded || index >= warmup) {
                result.add(valueOf(instrumentId, indicatorConfigId, candle, obv));
            }
        }
        return result;
    }

    /** Номер бара затравки в окне; затравки нет — значение не подано, пусто либо его бара в окне нет. */
    private int seedIndexOf(List<Candle> candles, IndicatorValue lastStored) {
        if (isNull(lastStored) || isNull(lastStored.getCandleTimestamp())
                || isNull(((ObvValue) lastStored).getObv())) {
            return NO_SEED;
        }
        OffsetDateTime seedAt = lastStored.getCandleTimestamp();
        for (int index = 0; index < candles.size(); index++) {
            if (candleTimestamp(candles.get(index)).isEqual(seedAt)) {
                return index;
            }
        }
        return NO_SEED;
    }

    private ObvValue valueOf(Long instrumentId, Long indicatorConfigId, Candle candle, BigDecimal obv) {
        ObvValue value = new ObvValue();
        value.setInstrumentId(instrumentId);
        value.setIndicatorConfigId(indicatorConfigId);
        value.setCandleTimestamp(candleTimestamp(candle));
        value.setObv(obv);
        return value;
    }
}
