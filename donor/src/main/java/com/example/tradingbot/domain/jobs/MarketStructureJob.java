package com.example.tradingbot.domain.jobs;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.BooleanUtils.isFalse;

import com.example.tradingbot.config.MarketDataJobsProperties;
import com.example.tradingbot.domain.model.aggregate.strategy.Strategy;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.StrategyIndicatorSetting;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.StrategyMarketStructureSetting;
import com.example.tradingbot.domain.model.trade.candle.Candle;
import com.example.tradingbot.domain.model.trade.candle.CandleGroup;
import com.example.tradingbot.domain.model.trade.indicator.AtrValue;
import com.example.tradingbot.domain.model.trade.indicator.EfficiencyRatioValue;
import com.example.tradingbot.domain.model.trade.indicator.IndicatorValue;
import com.example.tradingbot.domain.model.trade.market_structure.MarketStructure;
import com.example.tradingbot.domain.service.market.IndicatorService;
import com.example.tradingbot.domain.service.market.structure.MarketStructureResolver;
import com.example.tradingbot.persistence.service.CandleDataService;
import com.example.tradingbot.persistence.service.CandleGroupDataService;
import com.example.tradingbot.persistence.service.MarketStructureDataService;
import com.example.tradingbot.persistence.service.StrategyDataService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Производитель структуры рынка (docs/components/MarketStructureJob.md):
 * для стратегий всех статусов кроме DELETED заранее готовит MarketStructure
 * по закрытым свечам окна и сохраняет под настройкой-владельцем
 * (ключевание идентичностью вычисления, трек D — реестр конфигураций и дедуп убраны). Тонкий —
 * классификацию держит MarketStructureResolver; идемпотентность —
 * MarketStructureDataService (UNIQUE(instrument,
 * strategy_market_structure_setting_id, window_end_at)). Готовые каталожные
 * ER/ATR стратегии (fork-A / D3) потребляются как входы резолвера; при
 * объявленном, но не готовом входе — консервативный UNKNOWN (не proxy).
 * Вне расписания — через фасад.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketStructureJob {

    private static final String JOB_NAME = "marketStructureJob";

    private final StrategyDataService strategyDataService;
    private final CandleGroupDataService candleGroupDataService;
    private final CandleDataService candleDataService;
    private final MarketStructureDataService structureDataService;
    private final MarketStructureResolver resolver;
    private final IndicatorService indicatorService;
    private final MarketDataJobsProperties properties;
    private final JobExecutionGuard executionGuard;

    @Scheduled(cron = "${market-data.structure.cron}")
    public void tick() {
        if (isFalse(properties.getStructure().getEnabled())) {
            return;
        }
        executionGuard.runExclusively(JOB_NAME, this::run);
    }

    private void run() {
        for (Strategy strategy : strategyDataService.findForMarketData()) {
            if (isEmpty(strategy.getMarketStructureSettings())) {
                continue;
            }
            Map<String, StrategyIndicatorSetting> indicatorsByKey = indicatorsByKey(strategy.getIndicatorSettings());
            for (StrategyMarketStructureSetting setting : strategy.getMarketStructureSettings()) {
                computeStructure(strategy.getInstrumentId(), setting, indicatorsByKey);
            }
        }
    }

    private void computeStructure(Long instrumentId, StrategyMarketStructureSetting setting,
                                  Map<String, StrategyIndicatorSetting> indicatorsByKey) {
        try {
            if (isNull(setting.getParams()) || isNull(setting.getParams().getLookbackBars())) {
                return;
            }
            Optional<CandleGroup> group = candleGroupDataService.findByInstrumentIdAndTimeframe(
                    instrumentId, setting.getTimeframe());
            if (group.isEmpty()) {
                return;
            }
            List<Candle> window = candleDataService.findRecentByGroup(
                    group.get().getId(), setting.getParams().getLookbackBars());
            if (isEmpty(window)) {
                return;
            }
            saveResult(instrumentId, setting, indicatorsByKey, window);
        } catch (RuntimeException e) {
            log.error("Market structure calculation failed for instrument {}", instrumentId, e);
        }
    }

    private void saveResult(Long instrumentId, StrategyMarketStructureSetting setting,
                            Map<String, StrategyIndicatorSetting> indicatorsByKey, List<Candle> window) {
        Long settingId = setting.getId();
        BigDecimal efficiencyRatio = null;
        if (nonNull(setting.getEfficiencyRatioKey())) {
            efficiencyRatio = catalogScalar(instrumentId, setting.getEfficiencyRatioKey(), indicatorsByKey);
            if (isNull(efficiencyRatio)) {
                structureDataService.saveIfNew(unknownStructure(instrumentId, settingId, window));
                return;
            }
        }
        BigDecimal atr = null;
        if (nonNull(setting.getAtrKey())) {
            atr = catalogScalar(instrumentId, setting.getAtrKey(), indicatorsByKey);
            if (isNull(atr)) {
                structureDataService.saveIfNew(unknownStructure(instrumentId, settingId, window));
                return;
            }
        }
        MarketStructure structure = resolver.resolve(window, efficiencyRatio, atr, setting.getParams());
        structure.setInstrumentId(instrumentId);
        structure.setMarketStructureConfigId(settingId);
        structureDataService.saveIfNew(structure);
    }

    /** Скаляр готового каталожного ER/ATR по ключу настройки стратегии; null — настройки нет или вход не готов/устарел. */
    private BigDecimal catalogScalar(Long instrumentId, String key,
                                     Map<String, StrategyIndicatorSetting> indicatorsByKey) {
        StrategyIndicatorSetting indicatorSetting = indicatorsByKey.get(key);
        if (isNull(indicatorSetting)) {
            return null;
        }
        return indicatorService.getLatestValue(instrumentId, indicatorSetting)
                .map(this::scalarOf)
                .orElse(null);
    }

    private BigDecimal scalarOf(IndicatorValue value) {
        if (value instanceof EfficiencyRatioValue efficiencyRatioValue) {
            return efficiencyRatioValue.getEfficiencyRatio();
        }
        if (value instanceof AtrValue atrValue) {
            return atrValue.getAtr();
        }
        return null;
    }

    /** Консервативный UNKNOWN-результат на окне (объявленный вход не готов) — чтобы потребитель не торговал. */
    private MarketStructure unknownStructure(Long instrumentId, Long settingId, List<Candle> window) {
        MarketStructure structure = new MarketStructure();
        structure.setInstrumentId(instrumentId);
        structure.setMarketStructureConfigId(settingId);
        structure.setType(MarketStructure.Type.UNKNOWN);
        structure.setLevels(new ArrayList<>());
        OffsetDateTime windowEndAt = timestampOf(window.get(window.size() - 1));
        structure.setWindowStartAt(timestampOf(window.get(0)));
        structure.setWindowEndAt(windowEndAt);
        structure.setConfirmedAt(windowEndAt);
        return structure;
    }

    private Map<String, StrategyIndicatorSetting> indicatorsByKey(List<StrategyIndicatorSetting> indicators) {
        Map<String, StrategyIndicatorSetting> result = new HashMap<>();
        if (nonNull(indicators)) {
            indicators.forEach(setting -> {
                if (nonNull(setting.getKey())) {
                    result.put(setting.getKey(), setting);
                }
            });
        }
        return result;
    }

    private OffsetDateTime timestampOf(Candle candle) {
        return OffsetDateTime.ofInstant(Instant.ofEpochMilli(candle.getOpenTimestamp()), ZoneOffset.UTC);
    }
}
