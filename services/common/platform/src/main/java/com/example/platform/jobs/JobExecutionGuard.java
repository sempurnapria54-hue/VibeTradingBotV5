package com.example.platform.jobs;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Защита джобы от конкурентного выполнения: пока один запуск активен,
 * перекрывающий пропускается (.claude/rules/codestyle.md §Джобы).
 *
 * <p><b>Перекрывающих бывает два рода, и охрана нужна не всякой джобе.</b>
 * Затянувшийся предыдущий тик перекрывает следующий только при такте
 * {@code cron} — {@code fixedDelay} следующий запуск до конца текущего не
 * пускает сам. Второй род — ручной триггер через асинхронный фасад — есть
 * второй источник запуска, и с ним перекрытие возможно при любой форме
 * такта. Отсюда механический признак: охраны нет только у джобы, у которой
 * <b>и</b> такт {@code fixedDelay}, <b>и</b> фасада нет.
 *
 * <p><b>Замок in-memory, на инстанс, и это названное ограничение.</b> На
 * нескольких репликах два инстанса возьмут два разных замка и пройдут
 * одновременно; распределённый лок (ShedLock и т. п.) — после
 * архитектурного рубежа. Там, где проход идемпотентен по исходу, замок
 * экономит работу, а не держит инвариант: у реле публикация
 * повторяется идемпотентно, а дубль закрыт отметкой обработанного у
 * потребителя, форму которой задаёт его следствие; у чистки удаление
 * идемпотентно само по себе.
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
