package com.example.marketdata.domain.jobs;

import static java.util.Objects.isNull;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.BooleanUtils.isFalse;

import com.example.marketdata.config.MarketDataJobsProperties;
import com.example.marketdata.domain.model.IndicatorConfig;
import com.example.marketdata.domain.service.indicator.IndicatorCalculator;
import com.example.marketdata.persistence.service.CandleDataService;
import com.example.marketdata.persistence.service.CandleGroupDataService;
import com.example.marketdata.persistence.service.ComputationConfigDataService;
import com.example.marketdata.persistence.service.IndicatorDataService;
import com.example.tradingbot.domain.model.trade.candle.Candle;
import com.example.tradingbot.domain.model.trade.candle.CandleGroup;
import com.example.tradingbot.domain.model.trade.indicator.IndicatorValue;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Производитель технических индикаторов
 * (docs/components/IndicatorJob.md): считает объявленные значения по
 * закрытым свечам и сохраняет их под идентичностью вычисления. Тонкий
 * оркестратор — математику держат {@link IndicatorCalculator}, а
 * идемпотентность — {@link IndicatorDataService}.
 *
 * <p><b>Обходятся ЗАКАЗАННЫЕ ИДЕНТИЧНОСТИ, а не настройки стратегий.</b>
 * В монолите джоба шла по настройкам стратегий, потому что и результат
 * ключевался настройкой; настройки живут в базе другого сервиса, и
 * ходить в неё market-data не может и не должен
 * (docs/models/domain/other/IndicatorValue.md §«Ключевание —
 * идентичностью вычисления»).
 *
 * <p><b>Инструменты приносит СБОР, а не заказ.</b> Идентичность
 * называет, ЧТО считать; по каким инструментам — отвечают уже собранные
 * группы её таймфрейма. Так одна заказанная «ATR(14) на 1H»
 * автоматически покрывает весь листинг, у которого этот таймфрейм
 * собирается, — то, чего требуют детекторы советника
 * (docs/architecture/market-data-collection.md §«Пригодность для
 * детекторов»); требовать отдельного заказа на каждый инструмент значило
 * бы завести N заказов ради одного вопроса к рынку.
 */
@Slf4j
@Component
public class IndicatorJob {

    private static final String JOB_NAME = "indicatorJob";

    /** Статусы группы, при которых её история годится в расчёт. */
    private static final Set<CandleGroup.Status> COMPUTABLE_STATUSES = Set.of(
            CandleGroup.Status.SYNC,
            CandleGroup.Status.CHECK,
            CandleGroup.Status.REPAIR,
            CandleGroup.Status.ACTIVE);

    private final ComputationConfigDataService configDataService;
    private final CandleGroupDataService candleGroupDataService;
    private final CandleDataService candleDataService;
    private final IndicatorDataService indicatorDataService;
    private final MarketDataJobsProperties properties;
    private final JobExecutionGuard executionGuard;
    private final Map<IndicatorValue.Type, IndicatorCalculator> calculators;

    public IndicatorJob(ComputationConfigDataService configDataService,
                        CandleGroupDataService candleGroupDataService,
                        CandleDataService candleDataService,
                        IndicatorDataService indicatorDataService,
                        MarketDataJobsProperties properties,
                        JobExecutionGuard executionGuard,
                        List<IndicatorCalculator> calculators) {
        this.configDataService = configDataService;
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
        for (IndicatorConfig config : configDataService.findAllIndicatorConfigs()) {
            computeForAllInstruments(config);
        }
    }

    private void computeForAllInstruments(IndicatorConfig config) {
        IndicatorCalculator calculator = calculators.get(config.getIndicatorType());
        if (isNull(calculator)) {
            log.error("No calculator for indicator type {}", config.getIndicatorType());
            return;
        }
        List<CandleGroup> groups = candleGroupDataService
                .findByTimeframeAndStatusIn(config.getTimeframe(), COMPUTABLE_STATUSES);
        for (CandleGroup group : groups) {
            compute(config, calculator, group);
        }
    }

    private void compute(IndicatorConfig config, IndicatorCalculator calculator, CandleGroup group) {
        try {
            List<Candle> candles = candleDataService.findRecentByGroup(
                    group.getId(), properties.getCandleWindowBars());
            if (isEmpty(candles)) {
                return;
            }
            List<IndicatorValue> values = calculator.calculate(
                    group.getInstrumentId(), config.getId(), candles, config.getParams());
            indicatorDataService.saveValues(group.getInstrumentId(), config.getId(), values);
        } catch (RuntimeException e) {
            log.error("Indicator calculation failed for instrument {} indicator {}",
                    group.getInstrumentId(), config.getIndicatorType(), e);
        }
    }
}
