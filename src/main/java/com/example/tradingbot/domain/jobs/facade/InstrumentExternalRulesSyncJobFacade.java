package com.example.tradingbot.domain.jobs.facade;

import com.example.tradingbot.domain.jobs.InstrumentExternalRulesSyncJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Фасад асинхронного запуска {@link InstrumentExternalRulesSyncJob} вне
 * расписания: {@link #trigger()} помечен {@code @Async}, чтобы запуск
 * через контроллер не блокировал HTTP-ответ (codestyle: джобы вне
 * расписания триггерятся асинхронно через фасад).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InstrumentExternalRulesSyncJobFacade {

    private final InstrumentExternalRulesSyncJob job;

    /** Асинхронно запускает тик синхронизации внешних правил (вне CRON-расписания). */
    @Async
    public void trigger() {
        log.info("Manual instrument external rules sync trigger started");
        job.tick();
        log.info("Manual instrument external rules sync trigger finished");
    }
}
