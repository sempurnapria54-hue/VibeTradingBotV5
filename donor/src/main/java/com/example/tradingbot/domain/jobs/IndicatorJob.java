package com.example.tradingbot.domain.jobs;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.BooleanUtils.isFalse;

import com.example.tradingbot.config.MarketDataJobsProperties;
import com.example.tradingbot.domain.model.aggregate.strategy.Strategy;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.StrategyIndicatorSetting;
import com.example.tradingbot.domain.model.trade.candle.Candle;
import com.example.tradingbot.domain.model.trade.candle.CandleGroup;
import com.example.tradingbot.domain.model.trade.indicator.IndicatorValue;
import com.example.tradingbot.domain.service.market.indicator.IndicatorCalculator;
import com.example.tradingbot.persistence.service.CandleDataService;
import com.example.tradingbot.persistence.service.CandleGroupDataService;
import com.example.tradingbot.persistence.service.IndicatorDataService;
import com.example.tradingbot.persistence.service.StrategyDataService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Производитель технических индикаторов (docs/components/IndicatorJob.md):
 * для стратегий всех статусов кроме DELETED заранее считает объявленные
 * IndicatorValue по закрытым свечам и сохраняет их под настройкой-владельцем
 * (ключевание идентичностью вычисления, трек D — реестр конфигураций и дедуп убраны). Тонкий
 * оркестратор — математику держат IndicatorCalculator'ы, идемпотентность/
 * checkpoint — IndicatorDataService (UNIQUE(instrument,
 * strategy_indicator_setting_id, candle_timestamp)). Вне расписания —
 * асинхронно через IndicatorJobFacade.
 */
@Slf4j
@Component
public class IndicatorJob {

    private static final String JOB_NAME = "indicatorJob";

    private final StrategyDataService strategyDataService;
    private final CandleGroupDataService candleGroupDataService;
    private final CandleDataService candleDataService;
    private final IndicatorDataService indicatorDataService;
    private final MarketDataJobsProperties properties;
    private final JobExecutionGuard executionGuard;
    private final Map<IndicatorValue.Type, IndicatorCalculator> calculators;

    public IndicatorJob(StrategyDataService strategyDataService,
                        CandleGroupDataService candleGroupDataService,
                        CandleDataService candleDataService,
                        IndicatorDataService indicatorDataService,
                        MarketDataJobsProperties properties,
                        JobExecutionGuard executionGuard,
                        List<IndicatorCalculator> calculators) {
        this.strategyDataService = strategyDataService;
        this.candleGroupDataService = candleGroupDataService;
        this.candleDataService = candleDataService;
        this.indicatorDataService = indicatorDataService;
        this.properties = properties;
        this.executionGuard = executionGuard;
        this.calculators = calculators.stream().collect(toMap(IndicatorCalculator::getType, identity()));
    }

    @Scheduled(cron = "${market-data.indicator.cron}")
    public void tick() {
        if (isFalse(properties.getIndicator().getEnabled())) {
            return;
        }
        executionGuard.runExclusively(JOB_NAME, this::run);
    }

    private void run() {
        for (Strategy strategy : strategyDataService.findForMarketData()) {
            if (isEmpty(strategy.getIndicatorSettings())) {
                continue;
            }
            for (StrategyIndicatorSetting setting : strategy.getIndicatorSettings()) {
                computeIndicator(strategy.getInstrumentId(), setting);
            }
        }
    }

    private void computeIndicator(Long instrumentId, StrategyIndicatorSetting setting) {
        try {
            IndicatorCalculator calculator = calculators.get(setting.getIndicatorType());
            if (isNull(calculator)) {
                return;
            }
            Optional<CandleGroup> group = candleGroupDataService.findByInstrumentIdAndTimeframe(
                    instrumentId, setting.getParams().getTimeframe());
            if (group.isEmpty()) {
                return;
            }
            List<Candle> candles = candleDataService.findRecentByGroup(
                    group.get().getId(), properties.getCandleWindowBars());
            if (isEmpty(candles)) {
                return;
            }
            List<IndicatorValue> values = calculator.calculate(
                    instrumentId, setting.getId(), candles, setting.getParams());
            indicatorDataService.saveValues(instrumentId, setting.getId(), values);
        } catch (RuntimeException e) {
            log.error("Indicator calculation failed for instrument {} indicator {}",
                    instrumentId, nonNull(setting) ? setting.getIndicatorType() : null, e);
        }
    }
}
