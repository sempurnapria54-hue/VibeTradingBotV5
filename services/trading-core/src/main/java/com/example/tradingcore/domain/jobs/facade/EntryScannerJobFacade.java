package com.example.tradingcore.domain.jobs.facade;

import com.example.tradingcore.domain.jobs.EntryScannerJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Фасад асинхронного запуска {@link EntryScannerJob} вне расписания: ручной
 * триггер отбора входа не блокирует HTTP-ответ
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
public class EntryScannerJobFacade {

    private final EntryScannerJob entryScannerJob;

    /** Асинхронно запускает тик вне расписания. */
    @Async
    public void trigger() {
        log.info("Manual EntryScannerJob trigger started");
        entryScannerJob.tick();
        log.info("Manual EntryScannerJob trigger finished");
    }
}
