package com.example.tradingbot.domain.jobs.facade;

import com.example.tradingbot.domain.jobs.CandleJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Фасад асинхронного запуска {@link CandleJob} вне расписания. Запуск
 * через контроллер не блокирует HTTP-ответ: {@link #trigger()} помечен
 * {@code @Async} и выполняется в отдельном потоке (codestyle: джобы вне
 * расписания триггерятся асинхронно через фасад). Фасады джоб живут в
 * пакете domain.jobs.facade (конвенция проекта).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CandleJobFacade {

    private final CandleJob candleJob;

    /** Асинхронно запускает тик загрузки свечей (вне CRON-расписания). */
    @Async
    public void trigger() {
        log.info("Manual candle job trigger started");
        candleJob.tick();
        log.info("Manual candle job trigger finished");
    }
}
