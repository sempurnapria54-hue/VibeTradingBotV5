package com.example.marketdata.domain.service;

import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.marketdata.persistence.service.IndicatorDataService;
import com.example.tradingbot.domain.model.trade.indicator.IndicatorValue;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Раздаёт готовые значения индикаторов потребителям. Сам индикаторы не
 * считает — их заранее считает {@code IndicatorJob}. Значение резолвится
 * по <b>идентичности вычисления</b> и отдаётся, только если свежо под
 * <b>толерантность запрашивающего</b> (referencePoint =
 * {@code candleTimestamp}). См. docs/components/IndicatorService.md,
 * docs/rules/market-data-freshness.md.
 *
 * <p><b>Срок приезжает операндом, а не читается со строки.</b> В монолите
 * значение принадлежало настройке стратегии и брало срок у неё; в
 * сервисной конструкции настройка живёт в чужой базе, а одно и то же
 * значение шарится между заказчиками с разной толерантностью
 * (docs/models/domain/other/IndicatorValue.md).
 */
@Service
@RequiredArgsConstructor
public class IndicatorService {

    private final IndicatorDataService dataService;
    private final MarketDataExpirationChecker expirationChecker;

    /** Последнее значение идентичности, свежее под толерантность читателя (пусто — нет или устарело). */
    public Optional<IndicatorValue> getLatestValue(Long instrumentId, Long indicatorConfigId, Duration tolerance) {
        return dataService.findLatest(instrumentId, indicatorConfigId)
                .filter(value -> isTrue(expirationChecker.isFresh(value.getCandleTimestamp(), tolerance)));
    }

    /**
     * Предыдущее (второе по свежести) значение идентичности — для slope/
     * crossover; свежесть не гейтит (направление, не точка решения).
     */
    public Optional<IndicatorValue> getPreviousValue(Long instrumentId, Long indicatorConfigId) {
        List<IndicatorValue> recent = dataService.findLatestTwo(instrumentId, indicatorConfigId);
        return recent.size() < 2 ? Optional.empty() : Optional.of(recent.get(1));
    }
}
