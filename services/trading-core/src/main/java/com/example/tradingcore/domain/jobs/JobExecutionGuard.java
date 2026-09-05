package com.example.tradingcore.domain.jobs;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Защита джобы от конкурентного выполнения: пока один запуск активен,
 * перекрывающий пропускается (.claude/rules/codestyle.md §Джобы).
 *
 * <p>Перекрыть друг друга могут двое: затянувшийся предыдущий тик и
 * ручной триггер, пришедший параллельно расписанию.
 *
 * <p><b>Замок in-memory, на инстанс, и это названное ограничение.</b> На
 * нескольких репликах два инстанса возьмут два разных замка и пройдут
 * одновременно. Распределённый замок — предмет масштабирования ядра
 * после прод-рубежа (.claude/rules/tech-radar.md, запись
 * «Raw-JDBC ради advisory-замка»); до него ядро живёт одним инстансом.
 */
@Slf4j
@Component
public class JobExecutionGuard {

    private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    /**
     * Исполняет тик, если этой джобы сейчас никто не исполняет.
     *
     * @param jobName имя джобы — ключ замка
     * @param tick    само тело тика
     */
    public void runExclusively(String jobName, Runnable tick) {
        ReentrantLock lock = locks.computeIfAbsent(jobName, name -> new ReentrantLock());
        if (lock.tryLock()) {
            try {
                tick.run();
            } finally {
                lock.unlock();
            }
            return;
        }
        log.warn("Job {} is already running: overlapping tick skipped", jobName);
    }
}
