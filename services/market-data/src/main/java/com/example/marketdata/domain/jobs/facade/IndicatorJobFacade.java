package com.example.marketdata.domain.jobs.facade;

import com.example.marketdata.domain.jobs.IndicatorJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Фасад асинхронного запуска {@link IndicatorJob} вне расписания: ручной
 * триггер не блокирует HTTP-ответ (.claude/rules/codestyle.md §Джобы).
 *
 * <p>Фасад отвечает только за ЗАПУСК; исход самой работы наружу не
 * транслируется и уходит во внутреннюю градацию
 * (docs/rules/error-handling-policy.md).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IndicatorJobFacade {

    private final IndicatorJob indicatorJob;

    /** Асинхронно запускает тик вне расписания. */
    @Async
    public void trigger() {
        log.info("Manual indicator job trigger started");
        indicatorJob.tick();
        log.info("Manual indicator job trigger finished");
    }
}
