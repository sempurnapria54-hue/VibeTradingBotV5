package com.example.marketdata;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.marketdata.mapping.ComputationParamsJsonConverter;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.AtrParams;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.IndicatorParams;
import com.example.tradingbot.domain.model.trade.candle.TimeFrame;
import com.example.tradingbot.domain.model.trade.indicator.IndicatorValue;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Каноническая форма параметров — ключ идентичности вычисления.
 *
 * <p>Если она зависит от порядка полей во входе, одна и та же
 * «ATR(14) на 1H», пришедшая двумя порядками, заведёт в реестре ДВЕ
 * строки, и значение посчитается дважды — ровно то, ради чего реестр и
 * существует (docs/models/domain/other/IndicatorValue.md §«Ключевание —
 * идентичностью вычисления»). Поэтому свойство проверяется, а не
 * подразумевается.
 */
class ComputationIdentityTest {

    private final ComputationParamsJsonConverter converter =
            new ComputationParamsJsonConverter(new ObjectMapper());

    /** Порядок полей во входе идентичность не меняет. */
    @Test
    void canonicalFormIsIndependentOfInputOrder() {
        Map<String, Object> straight = new LinkedHashMap<>();
        straight.put("period", 14);
        straight.put("timeframe", TimeFrame.ONE_HOUR.name());
        straight.put("warmup", 28);

        Map<String, Object> shuffled = new LinkedHashMap<>();
        shuffled.put("warmup", 28);
        shuffled.put("timeframe", TimeFrame.ONE_HOUR.name());
        shuffled.put("period", 14);

        String first = converter.paramsToCanonical(
                converter.toIndicatorParams(straight, IndicatorValue.Type.ATR));
        String second = converter.paramsToCanonical(
                converter.toIndicatorParams(shuffled, IndicatorValue.Type.ATR));

        assertThat(first).isEqualTo(second);
    }

    /** Разные параметры — разная идентичность: иначе два вычисления делили бы одну строку. */
    @Test
    void differentParamsGiveDifferentIdentity() {
        IndicatorParams shortWindow = converter.toIndicatorParams(
                Map.of("period", 14, "timeframe", TimeFrame.ONE_HOUR.name()), IndicatorValue.Type.ATR);
        IndicatorParams longWindow = converter.toIndicatorParams(
                Map.of("period", 21, "timeframe", TimeFrame.ONE_HOUR.name()), IndicatorValue.Type.ATR);

        assertThat(converter.paramsToCanonical(shortWindow))
                .isNotEqualTo(converter.paramsToCanonical(longWindow));
    }

    /** Тот же расчёт на другом таймфрейме — другая идентичность. */
    @Test
    void timeframeIsPartOfIdentity() {
        IndicatorParams hourly = converter.toIndicatorParams(
                Map.of("period", 14, "timeframe", TimeFrame.ONE_HOUR.name()), IndicatorValue.Type.ATR);
        IndicatorParams daily = converter.toIndicatorParams(
                Map.of("period", 14, "timeframe", TimeFrame.ONE_DAY.name()), IndicatorValue.Type.ATR);

        assertThat(converter.paramsToCanonical(hourly))
                .isNotEqualTo(converter.paramsToCanonical(daily));
    }

    /** Подтип параметров резолвится по типу индикатора, а не по тегу в теле. */
    @Test
    void paramsSubtypeComesFromDeclaredType() {
        IndicatorParams params = converter.toIndicatorParams(
                Map.of("period", 14, "timeframe", TimeFrame.ONE_HOUR.name()), IndicatorValue.Type.ATR);

        assertThat(params).isInstanceOf(AtrParams.class);
        assertThat(((AtrParams) params).getPeriod()).isEqualTo(14);
        assertThat(params.getTimeframe()).isEqualTo(TimeFrame.ONE_HOUR);
    }
}
