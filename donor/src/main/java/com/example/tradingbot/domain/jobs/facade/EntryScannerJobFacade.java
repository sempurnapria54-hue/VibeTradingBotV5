package com.example.tradingbot.domain.jobs.facade;

import com.example.tradingbot.domain.jobs.EntryScannerJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Async-фасад ручного триггера {@link EntryScannerJob} вне расписания: не
 * блокирует HTTP-ответ. Делегирует в {@code tick()} (enabled-гейт и
 * in-memory guard применяются и при ручном заходе). Отвечает только за
 * запуск (docs/rules/error-handling-policy.md).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EntryScannerJobFacade {

    private final EntryScannerJob entryScannerJob;

    @Async
    public void trigger() {
        log.info("Manual entry scanner trigger started");
        entryScannerJob.tick();
        log.info("Manual entry scanner trigger finished");
    }
}
