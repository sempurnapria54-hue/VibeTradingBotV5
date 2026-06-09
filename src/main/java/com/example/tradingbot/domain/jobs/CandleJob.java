package com.example.tradingbot.domain.jobs;

import static org.apache.commons.lang3.BooleanUtils.isFalse;

import com.example.tradingbot.config.CandleLoadingProperties;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.trade.candle.CandleGroup;
import com.example.tradingbot.domain.service.core.InstrumentService;
import com.example.tradingbot.domain.service.market.CandleLoader;
import com.example.tradingbot.persistence.service.CandleGroupDataService;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Производитель базовых свечных данных (docs/components/CandleJob.md):
 * CRON-тик ведёт каждую {@link CandleGroup} по её циклу через
 * {@link CandleLoader}. Триггерит докачку хвоста для ACTIVE-групп при
 * новом закрытом баре. Стратегических сигналов/сделок не считает. Вне
 * расписания запускается асинхронно через {@link CandleJobFacade}.
 *
 * <p>ORCH-Q1 (провизорный seam): координацию готовности инструмента
 * (Instrument.Status по готовности групп) делает {@link #refreshInstrumentReadiness()}.
 * Канонический владелец оркестрации Instrument.Status — открытый
 * вопрос; здесь минимальная координация, чтобы шаг работал end-to-end.
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
    private final InstrumentService instrumentService;
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
        refreshInstrumentReadiness();
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
        long now = System.currentTimeMillis();
        for (CandleGroup group : activeGroups) {
            if (group.hasNewClosedBar(now)) {
                group.setStatus(CandleGroup.Status.SYNC);
                candleGroupDataService.save(group);
            }
        }
    }

    private void refreshInstrumentReadiness() {
        List<Instrument> loading = instrumentService.findLoadingInstruments();
        for (Instrument instrument : loading) {
            try {
                instrumentService.evaluateReadiness(instrument.getId());
            } catch (RuntimeException e) {
                log.error("Instrument readiness evaluation failed for {}", instrument.getId(), e);
            }
        }
    }
}
