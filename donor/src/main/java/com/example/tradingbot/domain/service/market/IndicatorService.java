package com.example.tradingbot.domain.service.market;

import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.model.aggregate.strategy.setting.StrategyIndicatorSetting;
import com.example.tradingbot.domain.model.trade.indicator.IndicatorValue;
import com.example.tradingbot.persistence.service.IndicatorDataService;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Раздаёт готовые значения индикаторов потребителям (evaluator,
 * калькуляторы, MarketPhaseResolver). Сам индикаторы не считает — их
 * заранее считает IndicatorJob. Значение резолвится по
 * настройке-владельцу (её id — owner-ключевание, трек D) и отдаётся,
 * только если свежо по её expirationDuration (referencePoint =
 * candleTimestamp). См. docs/components/IndicatorService.md,
 * docs/rules/market-data-freshness.md.
 */
@Service
@RequiredArgsConstructor
public class IndicatorService {

    private final IndicatorDataService dataService;
    private final MarketDataExpirationChecker expirationChecker;

    /** Последнее свежее значение по настройке-владельцу (пусто — нет или устарело). */
    public Optional<IndicatorValue> getLatestValue(Long instrumentId, StrategyIndicatorSetting setting) {
        return dataService.findLatest(instrumentId, setting.getId())
                .filter(value -> isTrue(expirationChecker.isFresh(
                        value.getCandleTimestamp(), setting.getExpirationDuration())));
    }

    /** Свежие значения по набору настроек (устаревшие/отсутствующие отбрасываются). */
    public List<IndicatorValue> getLatestValues(Long instrumentId,
                                                Collection<StrategyIndicatorSetting> settings) {
        return settings.stream()
                .map(setting -> getLatestValue(instrumentId, setting))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(toList());
    }

    /**
     * Предыдущее (второе по свежести) значение настройки — для slope/
     * crossover; свежесть не гейтит (направление, не точка решения).
     */
    public Optional<IndicatorValue> getPreviousValue(Long instrumentId, StrategyIndicatorSetting setting) {
        List<IndicatorValue> recent = dataService.findLatestTwo(instrumentId, setting.getId());
        return recent.size() < 2 ? Optional.empty() : Optional.of(recent.get(1));
    }
}
