package com.example.marketdata.domain.jobs;

import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.marketdata.config.CandleLoadingProperties;
import com.example.marketdata.domain.service.CandleLoader;
import com.example.marketdata.domain.service.InstrumentCatalogService;
import com.example.marketdata.persistence.service.CandleGroupDataService;
import com.example.tradingbot.domain.model.trade.candle.CandleGroup;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Производитель свечных данных (docs/components/CandleJob.md): тик ведёт
 * каждую {@link CandleGroup} по её циклу через {@link CandleLoader} и
 * триггерит докачку хвоста готовым группам, у которых подошёл новый
 * закрытый бар. Стратегических решений не принимает.
 *
 * <p>Готовность инструмента пересчитывается по его группам — тем же
 * тиком, потому что именно он группы и двигает
 * (docs/lifecycles/Instrument.md).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CandleJob {

    private static final Set<CandleGroup.Status> WORKING_STATUSES = Set.of(
            CandleGroup.Status.CREATED,
            CandleGroup.Status.BACKFILL,
            CandleGroup.Status.SYNC,
            CandleGroup.Status.CHECK,
            CandleGroup.Status.REPAIR);

    private static final String JOB_NAME = "candleJob";

    private final CandleGroupDataService candleGroupDataService;
    private final CandleLoader candleLoader;
    private final InstrumentCatalogService instrumentCatalogService;
    private final CandleLoadingProperties properties;
    private final JobExecutionGuard executionGuard;

    @Scheduled(cron = "${candle-loading.cron}")
    public void tick() {
        if (isFalse(properties.getEnabled())) {
            return;
        }
        executionGuard.runExclusively(JOB_NAME, this::run);
    }

    private void run() {
        triggerTailSync();
        advanceWorkingGroups();
        instrumentCatalogService.refreshReadiness();
    }

    private void advanceWorkingGroups() {
        List<CandleGroup> groups = candleGroupDataService.findByStatusIn(WORKING_STATUSES);
        for (CandleGroup group : groups) {
            try {
                candleLoader.advance(group);
            } catch (RuntimeException e) {
                log.error("Candle loading failed for group {}", group.getId(), e);
            }
        }
    }

    private void triggerTailSync() {
        List<CandleGroup> activeGroups = candleGroupDataService.findByStatusIn(Set.of(CandleGroup.Status.ACTIVE));
        long now = Instant.now().toEpochMilli();
        for (CandleGroup group : activeGroups) {
            if (isTrue(group.hasNewClosedBar(now))) {
                group.setStatus(CandleGroup.Status.SYNC);
                candleGroupDataService.save(group);
            }
        }
    }
}
