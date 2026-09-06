package com.example.tradingcore.domain.jobs.facade;

import com.example.tradingcore.domain.jobs.AnomalyJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Фасад асинхронного запуска {@link AnomalyJob} вне расписания: ручной
 * триггер прохода проактивной детекции не блокирует HTTP-ответ
 * (.claude/rules/codestyle.md §Джобы).
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
public class AnomalyJobFacade {

    private final AnomalyJob anomalyJob;

    /** Асинхронно запускает тик вне расписания. */
    @Async
    public void trigger() {
        log.info("Manual AnomalyJob trigger started");
        anomalyJob.tick();
        log.info("Manual AnomalyJob trigger finished");
    }
}
