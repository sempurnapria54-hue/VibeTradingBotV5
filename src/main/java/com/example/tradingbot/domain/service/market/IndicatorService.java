package com.example.tradingbot.domain.service.market;

import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.model.aggregate.strategy.setting.StrategyIndicatorSetting;
import com.example.tradingbot.domain.model.trade.indicator.IndicatorValue;
import com.example.tradingbot.persistence.service.IndicatorConfigDataService;
import com.example.tradingbot.persistence.service.IndicatorDataService;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Раздаёт готовые значения индикаторов потребителям (evaluator,
 * калькуляторы, MarketPhaseJob). Сам индикаторы не считает — их заранее
 * считает IndicatorJob. Значение резолвится по идентичности конфигурации
 * запрашивающей настройки (config_id) и отдаётся, только если свежо по
 * её expirationDuration (referencePoint = candleTimestamp). См.
 * docs/components/IndicatorService.md, docs/rules/market-data-freshness.md.
 */
@Service
@RequiredArgsConstructor
public class IndicatorService {

    private final IndicatorConfigDataService configDataService;
    private final IndicatorDataService dataService;
    private final MarketDataExpirationChecker expirationChecker;

    /** Последнее свежее значение по запрашивающей настройке (пусто — нет или устарело). */
    public Optional<IndicatorValue> getLatestValue(Long instrumentId, StrategyIndicatorSetting setting) {
        Long configId = configDataService.resolveConfigId(setting.getIndicatorType(), setting.getParams());
        return dataService.findLatest(instrumentId, configId)
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
        Long configId = configDataService.resolveConfigId(setting.getIndicatorType(), setting.getParams());
        List<IndicatorValue> recent = dataService.findLatestTwo(instrumentId, configId);
        return recent.size() < 2 ? Optional.empty() : Optional.of(recent.get(1));
    }
}
