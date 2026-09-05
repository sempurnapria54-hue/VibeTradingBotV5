package com.example.marketdata.domain.jobs;

import static org.apache.commons.lang3.BooleanUtils.isFalse;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Защита джоб от конкурентного выполнения: одно имя джобы — не более
 * одного активного запуска одновременно (перекрытие запланированного
 * тика с предыдущим затянувшимся или с ручным триггером через фасад).
 * In-memory (на инстанс) — достаточно до распила на микросервисы; при
 * мультиинстансе понадобится распределённый лок (ShedLock и т. п.).
 */
@Slf4j
@Component
public class JobExecutionGuard {

    private final Set<String> running = ConcurrentHashMap.newKeySet();

    /**
     * Выполняет задачу эксклюзивно по имени джобы: если такой запуск уже
     * идёт — пропускает (лог) и не запускает второй.
     */
    public void runExclusively(String jobName, Runnable task) {
        if (isFalse(running.add(jobName))) {
            log.debug("Job {} is already running, skipping overlapping execution", jobName);
            return;
        }
        try {
            task.run();
        } finally {
            running.remove(jobName);
        }
    }
}
