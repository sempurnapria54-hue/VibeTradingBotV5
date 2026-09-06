package com.example.tradingcore.domain.jobs.facade;

import com.example.tradingcore.domain.jobs.DealOrchestratorJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Фасад асинхронного запуска {@link DealOrchestratorJob} вне расписания: ручной
 * триггер прохода сопровождения сделок не блокирует HTTP-ответ
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
public class DealOrchestratorJobFacade {

    private final DealOrchestratorJob dealOrchestratorJob;

    /** Асинхронно запускает тик вне расписания. */
    @Async
    public void trigger() {
        log.info("Manual DealOrchestratorJob trigger started");
        dealOrchestratorJob.tick();
        log.info("Manual DealOrchestratorJob trigger finished");
    }
}
