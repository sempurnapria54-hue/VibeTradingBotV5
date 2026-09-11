package com.example.auditstatistics.domain.jobs;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Защита джобы от конкурентного выполнения: пока один запуск активен,
 * перекрывающий пропускается (.claude/rules/codestyle.md §Джобы).
 *
 * <p><b>Перекрывающий здесь ровно один — затянувшийся предыдущий тик.</b>
 * Второго запускающего у джоб этого сервиса нет: ручных фасадов у них не
 * бывает, поверхность объявлена только читающей
 * (docs/architecture/services.md). Поэтому охрана нужна той джобе, чей
 * такт — CRON: он бьёт независимо от того, кончился ли предыдущий проход,
 * а проход чистки длителен по построению — удаление идёт порциями, пока
 * старее глубины ничего не остаётся.
 *
 * <p><b>Соседний тик состояния приёма охраны не имеет, и это не
 * непоследовательность:</b> у него такт {@code fixedDelay}, который
 * следующий такт до конца текущего не запускает, и второго запускающего
 * нет — пара сходится целиком (docs/components/ReceptionStateJob.md).
 * Признак механический, и наследовать экземпцию по соседству нельзя.
 *
 * <p><b>Замок in-memory, на инстанс, и это названное ограничение.</b> На
 * нескольких репликах два инстанса возьмут два разных замка и пройдут
 * одновременно. Для чистки это не опасно и без замка: удаление
 * идемпотентно по исходу, а снятие момента разрыва — тем более; замок
 * экономит работу, а не держит инвариант.
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
