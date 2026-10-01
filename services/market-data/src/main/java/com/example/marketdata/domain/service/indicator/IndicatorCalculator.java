package com.example.marketdata.domain.service.indicator;

import static java.util.Objects.nonNull;

import com.example.tradingbot.domain.model.aggregate.strategy.setting.IndicatorParams;
import com.example.tradingbot.domain.model.trade.candle.Candle;
import com.example.tradingbot.domain.model.trade.indicator.IndicatorValue;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Вычислитель одного типа технического индикатора по закрытым свечам окна
 * прохода; отдаёт только значения, сохраняемые по прогреву (без
 * заглядывания вперёд). Действующий прогрев и граница сохранения —
 * docs/spec/indicator-calculation.json (`effectiveWarmup`,
 * `indicatorValueStored`); кумулятивный тип продолжает записанный ряд —
 * docs/components/IndicatorJob.md §«Кумулятивный тип продолжает записанный
 * ряд». Идемпотентность (дедуп по candle_timestamp) держит
 * IndicatorDataService. См. docs/components/IndicatorJob.md §Прогрев,
 * docs/models/domain/other/IndicatorValue.md.
 */
public interface IndicatorCalculator {

    /** Тип индикатора, который умеет считать этот вычислитель. */
    IndicatorValue.Type getType();

    /**
     * Считает значения индикатора по закрытым свечам (по возрастанию
     * открытия) для конфигурации indicatorConfigId; возвращает только значения
     * после warmup-зоны. instrumentId/indicatorConfigId/candleTimestamp на
     * результатах проставлены.
     */
    List<IndicatorValue> calculate(Long instrumentId, Long indicatorConfigId, List<Candle> closedCandles,
                                   IndicatorParams params);

    /**
     * Кумулятивный тип: сумму продолжает от последнего записанного значения
     * ряда идентичности, а не начинает заново со своего окна
     * (docs/components/IndicatorJob.md §«Кумулятивный тип продолжает
     * записанный ряд»).
     */
    default Boolean isCumulative() {
        return false;
    }

    /**
     * Считает значения, продолжая записанный ряд идентичности от
     * {@code lastStored} — последнего записанного значения, чей бар лежит в
     * окне; пусто — такого значения нет. Некумулятивному типу записанное
     * значение не нужно, и он считает по окну.
     */
    default List<IndicatorValue> calculate(Long instrumentId, Long indicatorConfigId, List<Candle> closedCandles,
                                           IndicatorParams params, IndicatorValue lastStored) {
        return calculate(instrumentId, indicatorConfigId, closedCandles, params);
    }

    /** Действующий прогрев (`effectiveWarmup`): объявленный override старше выведенного. */
    default Integer effectiveWarmup(Integer override, Integer derived) {
        return nonNull(override) ? override : derived;
    }

    /** Время свечи как точка отсчёта результата (UTC, по открытию бара). */
    default OffsetDateTime candleTimestamp(Candle candle) {
        return OffsetDateTime.ofInstant(Instant.ofEpochMilli(candle.getOpenTimestamp()), ZoneOffset.UTC);
    }
}
