package com.example.tradingbot.domain.service.market.indicator;

import static java.util.Objects.nonNull;

import com.example.tradingbot.domain.model.aggregate.strategy.setting.IndicatorParams;
import com.example.tradingbot.domain.model.trade.candle.Candle;
import com.example.tradingbot.domain.model.trade.indicator.IndicatorValue;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Вычислитель одного типа технического индикатора по закрытым свечам.
 * Реализация считает значения по всей переданной истории (рекурсивные/
 * кумулятивные индикаторы требуют полного ряда для корректного хвоста) и
 * отдаёт только значения после warmup-зоны (без look-ahead). Эффективный
 * warmup = override настройки ?? выведенный из типа и периода.
 * Идемпотентность (дедуп по candle_timestamp) и checkpoint держит
 * IndicatorDataService/job. См. docs/components/IndicatorJob.md (§Warmup),
 * docs/models/domain/other/IndicatorValue.md.
 */
public interface IndicatorCalculator {

    /** Тип индикатора, который умеет считать этот вычислитель. */
    IndicatorValue.Type getType();

    /**
     * Считает значения индикатора по закрытым свечам (по возрастанию
     * открытия) для конфигурации strategyIndicatorSettingId; возвращает только значения
     * после warmup-зоны. instrumentId/strategyIndicatorSettingId/candleTimestamp на
     * результатах проставлены.
     */
    List<IndicatorValue> calculate(Long instrumentId, Long strategyIndicatorSettingId, List<Candle> closedCandles,
                                   IndicatorParams params);

    /** Эффективный warmup: явный override настройки имеет приоритет над выведенным. */
    default Integer effectiveWarmup(Integer override, Integer derived) {
        return nonNull(override) ? override : derived;
    }

    /** Время свечи как точка отсчёта результата (UTC, по открытию бара). */
    default OffsetDateTime candleTimestamp(Candle candle) {
        return OffsetDateTime.ofInstant(Instant.ofEpochMilli(candle.getOpenTimestamp()), ZoneOffset.UTC);
    }
}
