package com.example.tradingbot.domain.jobs;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.BooleanUtils.isFalse;

import com.example.tradingbot.config.MarketDataJobsProperties;
import com.example.tradingbot.domain.model.aggregate.strategy.Strategy;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.StrategyIndicatorSetting;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.StrategyMarketPhaseSetting;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.StrategyMarketStructureSetting;
import com.example.tradingbot.domain.model.trade.candle.CandleGroup;
import com.example.tradingbot.domain.model.trade.indicator.IndicatorValue;
import com.example.tradingbot.domain.model.trade.market_phase.MarketPhase;
import com.example.tradingbot.domain.model.trade.market_structure.MarketStructure;
import com.example.tradingbot.domain.service.market.IndicatorService;
import com.example.tradingbot.domain.service.market.MarketStructureService;
import com.example.tradingbot.domain.service.market.condition.ConditionEvaluationContext;
import com.example.tradingbot.domain.service.market.phase.MarketPhaseClassification;
import com.example.tradingbot.domain.service.market.phase.MarketPhaseClassifier;
import com.example.tradingbot.persistence.service.CandleGroupDataService;
import com.example.tradingbot.persistence.service.MarketPhaseDataService;
import com.example.tradingbot.persistence.service.StrategyDataService;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Производитель фазы рынка (docs/components/MarketPhaseJob.md): для
 * стратегий всех статусов кроме DELETED собирает контекст оценки из
 * готовых IndicatorValue / MarketStructure и классифицирует фазу через
 * MarketPhaseClassifier (stateless first-match по phaseRules), сохраняя
 * актуальный MarketPhase. Тонкий — классификацию держит классификатор,
 * истинность условий — StrategyConditionEvaluator. Идемпотентность по
 * candle_timestamp последней закрытой свечи фаза-таймфрейма. Вне
 * расписания — через фасад.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketPhaseJob {

    private static final String JOB_NAME = "marketPhaseJob";

    private final StrategyDataService strategyDataService;
    private final CandleGroupDataService candleGroupDataService;
    private final IndicatorService indicatorService;
    private final MarketStructureService marketStructureService;
    private final MarketPhaseClassifier classifier;
    private final MarketPhaseDataService phaseDataService;
    private final MarketDataJobsProperties properties;
    private final JobExecutionGuard executionGuard;

    @Scheduled(cron = "${market-data.phase.cron}")
    public void tick() {
        if (isFalse(properties.getPhase().getEnabled())) {
            return;
        }
        executionGuard.runExclusively(JOB_NAME, this::run);
    }

    private void run() {
        for (Strategy strategy : strategyDataService.findForMarketData()) {
            computePhase(strategy);
        }
    }

    private void computePhase(Strategy strategy) {
        StrategyMarketPhaseSetting setting = strategy.getMarketPhaseSetting();
        if (isNull(setting) || isEmpty(setting.getPhaseRules())) {
            return;
        }
        Long instrumentId = strategy.getInstrumentId();
        try {
            Optional<CandleGroup> group = candleGroupDataService.findByInstrumentIdAndTimeframe(
                    instrumentId, setting.getTimeframe());
            if (group.isEmpty() || isNull(group.get().getActualLastUtcMillis())) {
                return;
            }
            OffsetDateTime candleTimestamp = OffsetDateTime.ofInstant(
                    Instant.ofEpochMilli(group.get().getActualLastUtcMillis()), ZoneOffset.UTC);
            if (alreadyComputed(instrumentId, setting.getId(), candleTimestamp)) {
                return;
            }
            ConditionEvaluationContext context = buildContext(instrumentId, setting, candleTimestamp);
            MarketPhaseClassification classification = classifier.classify(setting.getPhaseRules(), context);
            phaseDataService.saveIfNew(phase(instrumentId, setting, classification, candleTimestamp));
        } catch (RuntimeException e) {
            log.error("Market phase calculation failed for instrument {}", instrumentId, e);
        }
    }

    private boolean alreadyComputed(Long instrumentId, Long settingId, OffsetDateTime candleTimestamp) {
        OffsetDateTime checkpoint = phaseDataService.findCheckpoint(instrumentId, settingId);
        return nonNull(checkpoint)
                && (checkpoint.isAfter(candleTimestamp) || checkpoint.isEqual(candleTimestamp));
    }

    private ConditionEvaluationContext buildContext(Long instrumentId, StrategyMarketPhaseSetting setting,
                                                    OffsetDateTime candleTimestamp) {
        Map<String, IndicatorValue> latestIndicators = new HashMap<>();
        Map<String, IndicatorValue> previousIndicators = new HashMap<>();
        for (StrategyIndicatorSetting indicatorSetting : nullSafe(setting.getIndicatorSettings())) {
            indicatorService.getLatestValue(instrumentId, indicatorSetting)
                    .ifPresent(value -> latestIndicators.put(indicatorSetting.getKey(), value));
            indicatorService.getPreviousValue(instrumentId, indicatorSetting)
                    .ifPresent(value -> previousIndicators.put(indicatorSetting.getKey(), value));
        }
        Map<String, MarketStructure> structures = new HashMap<>();
        for (StrategyMarketStructureSetting structureSetting : nullSafe(setting.getMarketStructureSettings())) {
            marketStructureService.getLatestStructure(instrumentId, structureSetting)
                    .ifPresent(structure -> structures.put(structureSetting.getKey(), structure));
        }
        return ConditionEvaluationContext.builder()
                .latestIndicators(latestIndicators)
                .previousIndicators(previousIndicators)
                .structures(structures)
                .evaluationTime(candleTimestamp)
                .build();
    }

    private MarketPhase phase(Long instrumentId, StrategyMarketPhaseSetting setting,
                              MarketPhaseClassification classification, OffsetDateTime candleTimestamp) {
        MarketPhase phase = new MarketPhase();
        phase.setInstrumentId(instrumentId);
        phase.setSetting(setting);
        phase.setType(classification.getType());
        phase.setCandleTimestamp(candleTimestamp);
        phase.setConfirmedAt(classification.getConfirmedAt());
        return phase;
    }

    private <T> List<T> nullSafe(List<T> source) {
        return isNull(source) ? List.of() : source;
    }
}
