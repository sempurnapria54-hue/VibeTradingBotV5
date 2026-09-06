package com.example.strategies.domain.jobs.facade;

import com.example.strategies.domain.jobs.OutboxRelayJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Фасад асинхронного запуска {@link OutboxRelayJob} вне расписания:
 * ручной триггер реле не блокирует HTTP-ответ
 * (.claude/rules/codestyle.md §Джобы).
 *
 * <p><b>Зачем ручной триггер именно у реле.</b> Отказ брокера
 * прекращает проход, и строки остаются неопубликованными до следующего
 * тика; после восстановления брокера держатель разгребает накопленное
 * ходом, а не ожиданием.
 *
 * <p>Фасад отвечает только за ЗАПУСК; исход самой работы наружу не
 * транслируется и уходит во внутреннюю градацию
 * (docs/rules/error-handling-policy.md). Перекрывающий запуск гасит
 * защита от конкурентного выполнения — молча, потому что пропуск
 * перекрытия и есть штатное поведение.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelayJobFacade {

    private final OutboxRelayJob outboxRelayJob;

    /** Асинхронно запускает тик вне расписания. */
    @Async
    public void trigger() {
        log.info("Manual OutboxRelayJob trigger started");
        outboxRelayJob.tick();
        log.info("Manual OutboxRelayJob trigger finished");
    }
}
