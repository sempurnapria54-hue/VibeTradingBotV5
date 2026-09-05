package com.example.marketdata.domain.jobs.facade;

import com.example.marketdata.domain.jobs.MarketStructureJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Фасад асинхронного запуска {@link MarketStructureJob} вне расписания: ручной
 * триггер не блокирует HTTP-ответ (.claude/rules/codestyle.md §Джобы).
 *
 * <p>Фасад отвечает только за ЗАПУСК; исход самой работы наружу не
 * транслируется и уходит во внутреннюю градацию
 * (docs/rules/error-handling-policy.md).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketStructureJobFacade {

    private final MarketStructureJob marketStructureJob;

    /** Асинхронно запускает тик вне расписания. */
    @Async
    public void trigger() {
        log.info("Manual market structure job trigger started");
        marketStructureJob.tick();
        log.info("Manual market structure job trigger finished");
    }
}
