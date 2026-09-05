package com.example.marketdata.domain.jobs.facade;

import com.example.marketdata.domain.jobs.CandleJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Фасад асинхронного запуска {@link CandleJob} вне расписания: ручной
 * триггер не блокирует HTTP-ответ (.claude/rules/codestyle.md §Джобы).
 *
 * <p>Фасад отвечает только за ЗАПУСК; исход самой работы наружу не
 * транслируется и уходит во внутреннюю градацию
 * (docs/rules/error-handling-policy.md).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CandleJobFacade {

    private final CandleJob candleJob;

    /** Асинхронно запускает тик вне расписания. */
    @Async
    public void trigger() {
        log.info("Manual candle job trigger started");
        candleJob.tick();
        log.info("Manual candle job trigger finished");
    }
}
