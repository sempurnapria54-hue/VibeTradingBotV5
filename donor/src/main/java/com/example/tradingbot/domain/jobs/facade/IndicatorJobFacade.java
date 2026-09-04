package com.example.tradingbot.domain.jobs.facade;

import com.example.tradingbot.domain.jobs.IndicatorJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Фасад асинхронного запуска {@link IndicatorJob} вне расписания: запуск
 * через контроллер не блокирует HTTP-ответ ({@code @Async}). Фасады джоб
 * живут в пакете domain.jobs.facade (конвенция проекта).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IndicatorJobFacade {

    private final IndicatorJob indicatorJob;

    /** Асинхронно запускает тик расчёта индикаторов (вне CRON-расписания). */
    @Async
    public void trigger() {
        log.info("Manual indicator job trigger started");
        indicatorJob.tick();
        log.info("Manual indicator job trigger finished");
    }
}
