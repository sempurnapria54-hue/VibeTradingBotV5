package com.example.marketdata.domain.jobs.facade;

import com.example.marketdata.domain.jobs.SnapshotCollectionJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Фасад асинхронного запуска {@link SnapshotCollectionJob} вне расписания: ручной
 * триггер не блокирует HTTP-ответ (.claude/rules/codestyle.md §Джобы).
 *
 * <p>Фасад отвечает только за ЗАПУСК; исход самой работы наружу не
 * транслируется и уходит во внутреннюю градацию
 * (docs/rules/error-handling-policy.md).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SnapshotCollectionJobFacade {

    private final SnapshotCollectionJob snapshotCollectionJob;

    /** Асинхронно запускает тик вне расписания. */
    @Async
    public void trigger() {
        log.info("Manual snapshot collection job trigger started");
        snapshotCollectionJob.tick();
        log.info("Manual snapshot collection job trigger finished");
    }
}
