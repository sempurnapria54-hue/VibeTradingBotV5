package com.example.tradingbot.domain.jobs.facade;

import com.example.tradingbot.domain.jobs.MarketPhaseJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Фасад асинхронного запуска {@link MarketPhaseJob} вне расписания:
 * запуск через контроллер не блокирует HTTP-ответ ({@code @Async}).
 * Фасады джоб живут в пакете domain.jobs.facade (конвенция проекта).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketPhaseJobFacade {

    private final MarketPhaseJob marketPhaseJob;

    /** Асинхронно запускает тик расчёта фазы рынка (вне CRON-расписания). */
    @Async
    public void trigger() {
        log.info("Manual market phase job trigger started");
        marketPhaseJob.tick();
        log.info("Manual market phase job trigger finished");
    }
}
