package com.example.tradingbot.domain.deal;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import com.example.tradingbot.domain.model.aggregate.strategy.Strategy;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.StrategyIndicatorSetting;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.StrategyMarketStructureSetting;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.trade.indicator.IndicatorValue;
import com.example.tradingbot.domain.model.trade.market_price.MarketPriceData;
import com.example.tradingbot.domain.model.trade.market_structure.MarketStructure;
import com.example.tradingbot.domain.service.market.IndicatorService;
import com.example.tradingbot.domain.service.market.MarketPriceDataService;
import com.example.tradingbot.domain.service.market.MarketStructureService;
import com.example.tradingbot.domain.service.market.condition.ConditionEvaluationContext;
import com.example.tradingbot.persistence.service.StrategyDataService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Собирает {@link ConditionEvaluationContext} для оценки StrategyCondition
 * (entry-скан и FSM handler'ы) из готовых рыночных данных: свежие/предыдущие
 * значения индикаторов по ключу настройки, структуры по ключу, текущая цена
 * (last) по тикеру. Сами данные не считает — читает через сервисы (свежесть
 * индикаторов гейтит IndicatorService, структуры — MarketStructureService).
 * Ключи карт = {@code setting.getKey()} (совпадают с indicatorKey/
 * structureKey операндов условия). Параллель сборки расчётных данных —
 * docs/components/CalculationContextFactory.md.
 */
@Component
@RequiredArgsConstructor
public class MarketConditionContextFactory {

    private final StrategyDataService strategyDataService;
    private final IndicatorService indicatorService;
    private final MarketStructureService marketStructureService;
    private final MarketPriceDataService marketPriceDataService;

    public ConditionEvaluationContext build(Instrument instrument) {
        Long instrumentId = instrument.getId();
        Strategy strategy = strategyDataService.findActiveByInstrumentIdWithSettings(instrumentId).orElse(null);
        List<StrategyIndicatorSetting> indicatorSettings = isNull(strategy)
                ? List.of() : nullSafe(strategy.getIndicatorSettings());
        List<StrategyMarketStructureSetting> structureSettings = isNull(strategy)
                ? List.of() : nullSafe(strategy.getMarketStructureSettings());
        MarketPriceData marketPriceData = marketPriceDataService.getMarketPriceData(
                instrumentId, instrument.getExternalId());
        return ConditionEvaluationContext.builder()
                .latestIndicators(latestIndicators(instrumentId, indicatorSettings))
                .previousIndicators(previousIndicators(instrumentId, indicatorSettings))
                .structures(structures(instrumentId, structureSettings))
                .price(isNull(marketPriceData) ? null : marketPriceData.getExternalLastPrice())
                .evaluationTime(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
    }

    private Map<String, IndicatorValue> latestIndicators(Long instrumentId,
                                                         List<StrategyIndicatorSetting> settings) {
        Map<String, IndicatorValue> map = new HashMap<>();
        for (StrategyIndicatorSetting setting : settings) {
            indicatorService.getLatestValue(instrumentId, setting)
                    .ifPresent(value -> map.put(setting.getKey(), value));
        }
        return map;
    }

    private Map<String, IndicatorValue> previousIndicators(Long instrumentId,
                                                           List<StrategyIndicatorSetting> settings) {
        Map<String, IndicatorValue> map = new HashMap<>();
        for (StrategyIndicatorSetting setting : settings) {
            indicatorService.getPreviousValue(instrumentId, setting)
                    .ifPresent(value -> map.put(setting.getKey(), value));
        }
        return map;
    }

    private Map<String, MarketStructure> structures(Long instrumentId,
                                                    List<StrategyMarketStructureSetting> settings) {
        Map<String, MarketStructure> map = new HashMap<>();
        for (StrategyMarketStructureSetting setting : settings) {
            marketStructureService.getLatestStructure(instrumentId, setting)
                    .ifPresent(value -> map.put(setting.getKey(), value));
        }
        return map;
    }

    private <T> List<T> nullSafe(List<T> list) {
        return nonNull(list) ? list : List.of();
    }
}
