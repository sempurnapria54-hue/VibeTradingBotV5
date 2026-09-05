package com.example.marketdata.domain.jobs;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.BooleanUtils.isFalse;

import com.example.marketdata.config.MarketDataJobsProperties;
import com.example.marketdata.domain.model.MarketStructureConfig;
import com.example.marketdata.domain.service.structure.MarketStructureResolver;
import com.example.marketdata.persistence.service.CandleDataService;
import com.example.marketdata.persistence.service.CandleGroupDataService;
import com.example.marketdata.persistence.service.ComputationConfigDataService;
import com.example.marketdata.persistence.service.IndicatorDataService;
import com.example.marketdata.persistence.service.MarketStructureDataService;
import com.example.tradingbot.domain.model.trade.candle.Candle;
import com.example.tradingbot.domain.model.trade.candle.CandleGroup;
import com.example.tradingbot.domain.model.trade.indicator.AtrValue;
import com.example.tradingbot.domain.model.trade.indicator.EfficiencyRatioValue;
import com.example.tradingbot.domain.model.trade.indicator.IndicatorValue;
import com.example.tradingbot.domain.model.trade.market_structure.MarketStructure;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Производитель структуры рынка
 * (docs/components/MarketStructureJob.md): готовит {@link MarketStructure}
 * по закрытым свечам окна и сохраняет под идентичностью вычисления.
 * Тонкий — классификацию держит {@link MarketStructureResolver},
 * идемпотентность — {@link MarketStructureDataService}.
 *
 * <p><b>Обходятся заказанные идентичности, а инструменты приносит
 * сбор</b> — тот же довод, что у джобы индикаторов
 * ({@link IndicatorJob}).
 *
 * <p><b>Объявленный, но не готовый вход даёт {@code UNKNOWN}, а не
 * пропуск и не приближение.</b> Потребитель, увидев {@code UNKNOWN}, не
 * торгует; пропущенная строка была бы неотличима от «ещё не считали», а
 * подставленное значение выглядело бы наблюдением, не будучи им.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketStructureJob {

    private static final String JOB_NAME = "marketStructureJob";

    /** Статусы группы, при которых её история годится в расчёт. */
    private static final Set<CandleGroup.Status> COMPUTABLE_STATUSES = Set.of(
            CandleGroup.Status.SYNC,
            CandleGroup.Status.CHECK,
            CandleGroup.Status.REPAIR,
            CandleGroup.Status.ACTIVE);

    private final ComputationConfigDataService configDataService;
    private final CandleGroupDataService candleGroupDataService;
    private final CandleDataService candleDataService;
    private final MarketStructureDataService structureDataService;
    private final IndicatorDataService indicatorDataService;
    private final MarketStructureResolver resolver;
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
        for (MarketStructureConfig config : configDataService.findAllMarketStructureConfigs()) {
            computeForAllInstruments(config);
        }
    }

    private void computeForAllInstruments(MarketStructureConfig config) {
        if (isNull(config.getParams()) || isNull(config.getParams().getLookbackBars())) {
            log.error("Market structure config {} has no lookback window", config.getInternalId());
            return;
        }
        List<CandleGroup> groups = candleGroupDataService
                .findByTimeframeAndStatusIn(config.getTimeframe(), COMPUTABLE_STATUSES);
        for (CandleGroup group : groups) {
            compute(config, group);
        }
    }

    private void compute(MarketStructureConfig config, CandleGroup group) {
        try {
            List<Candle> window = candleDataService.findRecentByGroup(
                    group.getId(), config.getParams().getLookbackBars());
            if (isEmpty(window)) {
                return;
            }
            saveResult(config, group.getInstrumentId(), window);
        } catch (RuntimeException e) {
            log.error("Market structure calculation failed for instrument {}", group.getInstrumentId(), e);
        }
    }

    private void saveResult(MarketStructureConfig config, Long instrumentId, List<Candle> window) {
        BigDecimal efficiencyRatio = null;
        if (nonNull(config.getEfficiencyRatioConfigId())) {
            efficiencyRatio = inputScalar(instrumentId, config.getEfficiencyRatioConfigId());
            if (isNull(efficiencyRatio)) {
                structureDataService.saveIfNew(unknownStructure(instrumentId, config.getId(), window));
                return;
            }
        }
        BigDecimal atr = null;
        if (nonNull(config.getAtrConfigId())) {
            atr = inputScalar(instrumentId, config.getAtrConfigId());
            if (isNull(atr)) {
                structureDataService.saveIfNew(unknownStructure(instrumentId, config.getId(), window));
                return;
            }
        }
        MarketStructure structure = resolver.resolve(window, efficiencyRatio, atr, config.getParams());
        structure.setInstrumentId(instrumentId);
        structure.setMarketStructureConfigId(config.getId());
        structureDataService.saveIfNew(structure);
    }

    /**
     * Скаляр готового входа по его идентичности; пусто — вход ещё не
     * посчитан.
     *
     * <p><b>Свежесть входа здесь не гейтится намеренно:</b> толерантность
     * принадлежит читателю результата, а не производителю
     * (docs/rules/market-data-freshness.md). Производитель считает на том,
     * что есть, а годность посчитанного оценивает тот, кто его прочтёт, —
     * по СВОЕМУ сроку и по {@code windowEndAt} результата.
     */
    private BigDecimal inputScalar(Long instrumentId, Long indicatorConfigId) {
        return indicatorDataService.findLatest(instrumentId, indicatorConfigId)
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

    /** Консервативный UNKNOWN на окне: объявленный вход не готов, торговать нельзя. */
    private MarketStructure unknownStructure(Long instrumentId, Long configId, List<Candle> window) {
        MarketStructure structure = new MarketStructure();
        structure.setInstrumentId(instrumentId);
        structure.setMarketStructureConfigId(configId);
        structure.setType(MarketStructure.Type.UNKNOWN);
        structure.setLevels(new ArrayList<>());
        OffsetDateTime windowEndAt = timestampOf(window.get(window.size() - 1));
        structure.setWindowStartAt(timestampOf(window.get(0)));
        structure.setWindowEndAt(windowEndAt);
        structure.setConfirmedAt(windowEndAt);
        return structure;
    }

    private OffsetDateTime timestampOf(Candle candle) {
        return OffsetDateTime.ofInstant(Instant.ofEpochMilli(candle.getOpenTimestamp()), ZoneOffset.UTC);
    }
}
