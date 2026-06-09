package com.example.tradingbot.domain.jobs.facade;

import com.example.tradingbot.domain.jobs.MarketStructureJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Фасад асинхронного запуска {@link MarketStructureJob} вне расписания:
 * запуск через контроллер не блокирует HTTP-ответ ({@code @Async}).
 * Фасады джоб живут в пакете domain.jobs.facade (конвенция проекта).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketStructureJobFacade {

    private final MarketStructureJob marketStructureJob;

    /** Асинхронно запускает тик расчёта структуры рынка (вне CRON-расписания). */
    @Async
    public void trigger() {
        log.info("Manual market structure job trigger started");
        marketStructureJob.tick();
        log.info("Manual market structure job trigger finished");
    }
}
