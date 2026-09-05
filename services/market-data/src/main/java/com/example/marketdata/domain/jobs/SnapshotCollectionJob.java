package com.example.marketdata.domain.jobs;

import static org.apache.commons.lang3.BooleanUtils.isFalse;

import com.example.marketdata.config.SnapshotCollectionProperties;
import com.example.marketdata.domain.service.SnapshotCollector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Тик сбора невосполнимых срезов (docs/processes/snapshot-collection.md).
 *
 * <p><b>Перекрытие проходов запрещено.</b> Затянувшийся проход не
 * догоняется вторым: два конкурирующих прохода удвоили бы расход лимитов
 * ровно тогда, когда лимитов и так не хватило. Механизм — тот же
 * {@link JobExecutionGuard}, что у остальных джоб.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SnapshotCollectionJob {

    private static final String JOB_NAME = "snapshotCollectionJob";

    private final SnapshotCollector collector;
    private final SnapshotCollectionProperties properties;
    private final JobExecutionGuard executionGuard;

    @Scheduled(cron = "${snapshot-collection.cron}")
    public void tick() {
        if (isFalse(properties.getEnabled())) {
            return;
        }
        executionGuard.runExclusively(JOB_NAME, this::run);
    }

    private void run() {
        try {
            collector.collectPass();
        } catch (RuntimeException e) {
            log.error("Snapshot collection pass failed", e);
        }
    }
}
